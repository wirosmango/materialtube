package dev.ytclient.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.ytclient.Graph

@Composable
fun FeedScreen(
    title: String,
    browseId: String,
    requiresLogin: Boolean,
    loginHint: String,
    onOpenVideo: (String) -> Unit,
    onLogin: () -> Unit,
    onSearch: () -> Unit,
    header: (@Composable () -> Unit)? = null,
) {
    val vm: FeedViewModel = viewModel(key = "feed-$browseId") { FeedViewModel(browseId, requiresLogin) }
    val state by vm.state.collectAsStateWithLifecycle()
    val auth by Graph.auth.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val listState = rememberLazyListState()

    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { index ->
                if (index != null && index >= vm.state.value.items.size - 3) vm.loadMore()
            }
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { AppTopBar(title = title, onSearch = onSearch) },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                state.loading -> LoadingIndicator(Modifier.align(Alignment.Center))

                state.items.isEmpty() -> Column(
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    val message = when {
                        state.error != null -> state.error.orEmpty()
                        !auth.loggedIn -> loginHint
                        else -> "Лента пуста или ответ не удалось разобрать"
                    }
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (state.error != null) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        textAlign = TextAlign.Center,
                    )
                    if (!auth.loggedIn) Button(onClick = onLogin) { Text("Войти через Google") }
                    OutlinedButton(onClick = vm::load) { Text("Обновить") }
                    if (auth.loggedIn && state.error == null) {
                        OutlinedButton(onClick = { copyLastResponse(context) }) {
                            Text("Скопировать ответ (отладка)")
                        }
                    }
                }

                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = FloatingBarClearance),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    header?.let { item(key = "header") { it() } }
                    items(state.items, key = { it.id }) { item ->
                        VideoCard(item = item, onClick = { onOpenVideo(item.id) })
                    }
                    if (state.loadingMore) {
                        item {
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

/** Кладёт сырой ответ ленты в буфер обмена (обрезан: в буфер нельзя класть больше ~1 МБ). */
internal fun copyLastResponse(context: Context) {
    val text = Graph.innerTube.lastFeedJson.orEmpty().take(400_000)
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("youtube-response", text))
    Toast.makeText(context, "Скопировано: ${text.length} символов", Toast.LENGTH_SHORT).show()
}
