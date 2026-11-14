package dev.ytclient.ui

import android.webkit.CookieManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import dev.ytclient.Graph
import dev.ytclient.innertube.VideoItem

/** Количество последних видео истории, показываемых в горизонтальной ленте на странице профиля (полный список — на экране «История»). */
private const val RecentHistoryCount = 7

/** Вкладка «Профиль»: сверху аккаунт, ниже компактная горизонтальная лента из 7 последних видео и кнопка перехода в полную историю. */
@Composable
fun AccountScreen(
    onLogin: () -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenVideo: (String) -> Unit,
) {
    val auth by Graph.auth.state.collectAsStateWithLifecycle()
    val vm: ProfileViewModel = viewModel()
    val account by vm.account.collectAsStateWithLifecycle()
    val history: HistoryViewModel = viewModel()
    val historyState by history.state.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { AppTopBar(title = "Профиль", onSearch = onSearch, onSettings = onSettings) },
    ) { padding ->
        LazyColumn(
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

            item(key = "history-header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("История", style = MaterialTheme.typography.titleLarge)
                    if (auth.loggedIn && historyState.items.isNotEmpty()) {
                        FilledTonalButton(onClick = onOpenHistory) {
                            Text("Посмотреть историю")
                        }
                    }
                }
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
                else -> item(key = "history-carousel") {
                    val recent = remember(historyState.items) { historyState.items.take(RecentHistoryCount) }
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(recent, key = { "hr-" + it.id }) { item ->
                            CompactHistoryVideoCard(
                                item = item,
                                onClick = { onOpenVideo(item.id) },
                            )
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

/** Компактная карточка ролика для горизонтальной карусели последних 7 просмотренных видео. */
@Composable
private fun CompactHistoryVideoCard(item: VideoItem, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 500f),
        label = "historyCardPress",
    )

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = Modifier
            .width(220.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(24.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        Column {
            Box(
                Modifier
                    .padding(8.dp)
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
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
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
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
            Column(
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    minLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                item.channel?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
