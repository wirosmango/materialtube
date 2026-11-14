package dev.ytclient.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import dev.ytclient.Graph
import dev.ytclient.innertube.HistoryEntry

/** Пока идёт поиск по истории, подгружаем страницы дальше только до этого числа записей, чтобы не выкачать всю историю. */
private const val SearchLoadLimit = 1000

private sealed interface HistoryRow {
    val key: String

    data class Header(val title: String, override val key: String) : HistoryRow

    data class Item(val entry: HistoryEntry) : HistoryRow {
        override val key: String get() = "v-" + entry.key
    }
}

/** Плоский список для LazyColumn: перед первым роликом каждой даты — заголовок. */
private fun historyRows(entries: List<HistoryEntry>): List<HistoryRow> {
    val rows = ArrayList<HistoryRow>(entries.size + 8)
    var current: String? = null
    for (entry in entries) {
        val section = entry.section
        if (section != null && section != current) {
            rows += HistoryRow.Header(section, "h-" + entry.key)
            current = section
        }
        rows += HistoryRow.Item(entry)
    }
    return rows
}

/**
 * Экран «История»: все просмотренные ролики по датам, поиск по уже загруженным записям, удаление записи
 * и обновление «потянуть вниз». Список общий с лентой на вкладке «Профиль» ([HistoryViewModel]).
 * [bottomInset] — место снизу под мини-плеер, чтобы он не закрывал последние записи.
 */
@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    onOpenVideo: (String) -> Unit,
    bottomInset: Dp = 0.dp,
) {
    val history: HistoryViewModel = viewModel()
    val state by history.state.collectAsStateWithLifecycle()
    val auth by Graph.auth.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val snackbar = remember { SnackbarHostState() }

    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }

    fun closeSearch() {
        searching = false
        query = ""
    }

    BackHandler { if (searching) closeSearch() else onBack() }

    val needle = query.trim()
    val visible = remember(state.entries, needle) {
        if (needle.isEmpty()) {
            state.entries
        } else {
            state.entries.filter {
                it.video.title.contains(needle, ignoreCase = true) ||
                    it.video.channel?.contains(needle, ignoreCase = true) == true
            }
        }
    }
    val rows = remember(visible) { historyRows(visible) }

    LaunchedEffect(Unit) {
        history.messages.collect {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(it)
        }
    }
    LaunchedEffect(searching) { if (searching) focus.requestFocus() }

    // Поиск идёт по загруженным записям: если совпадений мало, а продолжение есть, подгружаем дальше.
    LaunchedEffect(needle, visible.size, state.continuation, state.loading, state.loadingMore) {
        if (needle.isNotEmpty() && visible.size < 6 && state.entries.size < SearchLoadLimit &&
            state.continuation != null && !state.loading && !state.loadingMore
        ) {
            history.loadMore()
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { index ->
                if (index != null && index >= listState.layoutInfo.totalItemsCount - 3) history.loadMore()
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (searching) {
                        TextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = { Text("Поиск по истории") },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
                            modifier = Modifier.fillMaxWidth().focusRequester(focus),
                        )
                    } else {
                        Text("История")
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { if (searching) closeSearch() else onBack() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    if (!searching) {
                        IconButton(onClick = { searching = true }) {
                            Icon(Icons.Rounded.Search, contentDescription = "Поиск по истории")
                        }
                    } else if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Очистить")
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.loading && state.entries.isNotEmpty(),
            onRefresh = { history.load(keepItems = true) },
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            when {
                !auth.loggedIn -> CenteredMessage("Войдите в аккаунт, чтобы увидеть историю просмотров.")

                state.loading && state.entries.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    LoadingIndicator()
                }

                state.entries.isEmpty() -> CenteredMessage(
                    text = state.error ?: "История просмотров пуста или ответ не удалось разобрать.",
                    isError = state.error != null,
                )

                rows.isEmpty() && !state.loadingMore -> CenteredMessage("Ничего не найдено среди загруженных записей.")

                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp + bottomInset),
                ) {
                    items(rows, key = { it.key }) { row ->
                        when (row) {
                            is HistoryRow.Header -> Text(
                                text = row.title,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier
                                    .animateItem()
                                    .fillMaxWidth()
                                    .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 6.dp),
                            )

                            is HistoryRow.Item -> HistoryVideoRow(
                                entry = row.entry,
                                onClick = { onOpenVideo(row.entry.video.id) },
                                onRemove = if (row.entry.removeToken != null) {
                                    { history.remove(row.entry) }
                                } else {
                                    null
                                },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                    if (state.loadingMore) {
                        item(key = "history-more") {
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                LoadingIndicator()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CenteredMessage(text: String, isError: Boolean = false) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(24.dp),
        )
    }
}

/** Компактная строка истории: превью слева, справа название, канал и просмотры; в конце — кнопка удаления. */
@Composable
private fun HistoryVideoRow(
    entry: HistoryEntry,
    onClick: () -> Unit,
    onRemove: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val item = entry.video
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .width(150.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLowest),
        ) {
            AsyncImage(
                model = item.thumbnail,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            item.duration?.let { duration ->
                Surface(
                    color = Color.Black.copy(alpha = 0.72f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp),
                ) {
                    Text(
                        text = duration,
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        }

        Column(Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            item.channel?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            item.views?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (onRemove != null) {
            IconButton(onClick = onRemove, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Rounded.Delete, contentDescription = "Удалить из истории")
            }
        }
    }
}
