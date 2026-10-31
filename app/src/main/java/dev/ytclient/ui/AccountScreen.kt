package dev.ytclient.ui

import android.webkit.CookieManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.LoadingIndicator
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.ytclient.Graph

/** Вкладка «Профиль»: сверху аккаунт, ниже (позже) история просмотров. В верхней панели поиск и настройки. */
@Composable
fun AccountScreen(onLogin: () -> Unit, onSearch: () -> Unit, onSettings: () -> Unit, onOpenVideo: (String) -> Unit) {
    val auth by Graph.auth.state.collectAsStateWithLifecycle()
    val vm: ProfileViewModel = viewModel()
    val account by vm.account.collectAsStateWithLifecycle()
    val history: FeedViewModel = viewModel(key = "feed-FEhistory") { FeedViewModel("FEhistory", true) }
    val historyState by history.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { index ->
                if (index != null && index >= listState.layoutInfo.totalItemsCount - 3) history.loadMore()
            }
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { AppTopBar(title = "Профиль", onSearch = onSearch, onSettings = onSettings) },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = FloatingBarClearance),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "account") {
                Surface(
                    shape = RoundedCornerShape(32.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (auth.loggedIn) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                ChannelAvatarImage(account?.avatar, 72.dp)
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        account?.name ?: "Вы вошли в аккаунт",
                                        style = MaterialTheme.typography.titleLarge,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    account?.handle?.let {
                                        Text(
                                            it,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                            OutlinedButton(
                                onClick = {
                                    Graph.auth.clear()
                                    CookieManager.getInstance().removeAllCookies(null)
                                },
                            ) { Text("Выйти") }
                        }
                    } else {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Вход не выполнен", style = MaterialTheme.typography.titleLarge)
                            Text(
                                "Войдите, чтобы увидеть персональные рекомендации, подписки и историю.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Button(onClick = onLogin) { Text("Войти через Google") }
                        }
                    }
                }
            }

            item(key = "history") {
                Text("История", style = MaterialTheme.typography.titleLarge)
            }
            when {
                !auth.loggedIn -> item(key = "history-hint") {
                    Text(
                        "Войдите, чтобы увидеть историю просмотров.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                historyState.loading -> item(key = "history-loading") {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        LoadingIndicator()
                    }
                }
                historyState.items.isEmpty() -> item(key = "history-empty") {
                    Text(
                        historyState.error ?: "История пуста или ответ не удалось разобрать. " +
                            "Просмотры из этого приложения пока не записываются.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (historyState.error != null) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                else -> {
                    items(historyState.items, key = { "h-" + it.id }) { item ->
                        VideoCard(item = item, onClick = { onOpenVideo(item.id) })
                    }
                    if (historyState.loadingMore) item(key = "history-more") {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            LoadingIndicator()
                        }
                    }
                }
            }

            item(key = "warning") {
                Text(
                    "Неофициальный клиент: YouTube может ограничить аккаунт, который им пользуется. " +
                        "Для проверки лучше использовать отдельный аккаунт.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
