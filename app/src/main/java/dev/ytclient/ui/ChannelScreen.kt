package dev.ytclient.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/** Страница канала: шапка с аватаркой и подпиской, ниже видео канала с подгрузкой. */
@Composable
fun ChannelScreen(channelId: String, onBack: () -> Unit, onOpenVideo: (String) -> Unit) {
    val vm: ChannelViewModel = viewModel(key = "channel-$channelId") { ChannelViewModel(channelId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val listState = rememberLazyListState()

    BackHandler(onBack = onBack)

    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { index ->
                if (index != null && index >= vm.state.value.items.size - 2) vm.loadMore()
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            val channel = state.channel
            when {
                state.loading -> LoadingIndicator(Modifier.align(Alignment.Center))

                channel == null -> Column(
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        state.error ?: "Не удалось открыть канал",
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                    Button(onClick = vm::load) { Text("Повторить") }
                }

                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    item(key = "header") {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ChannelAvatarImage(channel.avatar, 72.dp)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    channel.name,
                                    style = MaterialTheme.typography.titleLarge,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                channel.subscribers?.let {
                                    Text(
                                        it,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                SubscribeButton(
                                    subscribed = channel.subscribed,
                                    onClick = {
                                        vm.toggleSubscribe { message ->
                                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                )
                            }
                        }
                    }
                    if (state.items.isEmpty()) {
                        item(key = "empty") {
                            Text(
                                "Видео не найдены или ответ не удалось разобрать",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(32.dp),
                            )
                        }
                    }
                    items(state.items, key = { it.id }) { item ->
                        VideoCard(item = item, onClick = { onOpenVideo(item.id) })
                    }
                    if (state.loadingMore) {
                        item(key = "more") {
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
