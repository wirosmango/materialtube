package dev.ytclient.ui

import android.widget.Toast
import kotlinx.coroutines.delay
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.material3.Surface
import androidx.compose.material3.Icon
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.size
import androidx.compose.animation.scaleOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import dev.ytclient.Graph
import dev.ytclient.innertube.LikeStatus
import dev.ytclient.player.MediaSources
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Вкладка Shorts: вертикальная лента, один ролик на страницу, листаются свайпом.
 * [active] = false, когда поверх открыт другой экран (плеер, поиск, вход): тогда ролик ставится на паузу.
 */
@Composable
fun ShortsScreen(active: Boolean, vm: ShortsViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Box(Modifier.fillMaxSize()) {
        when {
            state.loading -> LoadingIndicator(Modifier.align(Alignment.Center))

            state.ids.isEmpty() -> Column(
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = state.error ?: "Не удалось получить ленту Shorts",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (state.error != null) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    textAlign = TextAlign.Center,
                )
                Button(onClick = vm::load) { Text("Обновить") }
                OutlinedButton(onClick = { copyLastResponse(context) }) {
                    Text("Скопировать ответ (отладка)")
                }
            }

            else -> ShortsPager(ids = state.ids, active = active, onNearEnd = vm::loadMore)
        }
    }
}

@Composable
private fun ShortsPager(ids: List<String>, active: Boolean, onNearEnd: () -> Unit) {
    val context = LocalContext.current
    val pagerState = rememberPagerState { ids.size }

    // Один плеер на всю ленту: при смене страницы в него загружается следующий ролик.
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
            playWhenReady = true
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }

    val currentActive by rememberUpdatedState(active)
    var resolving by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<LikeStatus?>(null) }
    var commentsToken by remember { mutableStateOf<String?>(null) }
    var showComments by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, player) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) player.pause()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(active) {
        if (active) player.play() else player.pause()
    }

    val currentId = ids.getOrNull(pagerState.settledPage)
    LaunchedEffect(currentId) {
        val id = currentId ?: return@LaunchedEffect
        resolving = true
        error = null
        status = null
        commentsToken = null
        launch {
            try {
                val next = Graph.innerTube.watchNext(id)
                status = next.status
                commentsToken = next.commentsToken
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Оценки и комментарии необязательны.
            }
        }
        player.stop()
        player.clearMediaItems()
        try {
            val info = Graph.innerTube.resolveStream(id, maxHeight = 720)
            player.setMediaSource(MediaSources.create(info, Graph.http))
            player.prepare()
            player.playWhenReady = currentActive
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: "Не удалось загрузить ролик"
        }
        resolving = false
    }

    LaunchedEffect(pagerState.settledPage) {
        if (pagerState.settledPage >= ids.size - 2) onNearEnd()
    }

    val onReact: (LikeStatus) -> Unit = { target ->
        val id = currentId
        if (id != null) {
            if (!Graph.auth.current.loggedIn) {
                Toast.makeText(context, "Войдите в аккаунт на вкладке «Аккаунт»", Toast.LENGTH_SHORT).show()
            } else {
                val previous = status
                val new = if (previous == target) LikeStatus.None else target
                status = new
                scope.launch {
                    try {
                        Graph.innerTube.react(id, new)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // Откатываем, только если пользователь не успел перелистнуть на другой ролик.
                        if (currentId == id) status = previous
                        Toast.makeText(context, e.message ?: "Не удалось поставить оценку", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    if (showComments) CommentsSheet(token = commentsToken, onDismiss = { showComments = false })

    VerticalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
        ShortPage(
            id = ids[page],
            isCurrent = page == pagerState.settledPage,
            player = player,
            resolving = resolving,
            error = error,
            status = status,
            onReact = onReact,
            onComments = { showComments = true },
        )
    }
}

@Composable
private fun ShortPage(
    id: String,
    isCurrent: Boolean,
    player: ExoPlayer,
    resolving: Boolean,
    error: String?,
    status: LikeStatus?,
    onReact: (LikeStatus) -> Unit,
    onComments: () -> Unit,
) {
    // Ролик лежит в скруглённой «карточке» над панелью навигации, как и остальные экраны.
    Box(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = FloatingBarClearance),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(32.dp))
                .background(Color.Black),
        ) {
            if (isCurrent) {
                AndroidView(
                    factory = { ctx -> PlayerView(ctx).apply { useController = false; this.player = player } },
                    update = { it.player = player },
                    modifier = Modifier.fillMaxSize(),
                )
                // Прозрачный слой сверху ловит нажатия (PlayerView без контроллера их не отдаёт).
                var boost by remember { mutableStateOf(false) }
                var heartKey by remember { mutableIntStateOf(0) }
                var showHeart by remember { mutableStateOf(false) }
                LaunchedEffect(heartKey) {
                    if (heartKey > 0) {
                        showHeart = true
                        delay(700)
                        showHeart = false
                    }
                }
                Box(
                    Modifier
                        .fillMaxSize()
                        .playerGestures(
                            player = player,
                            onTap = { if (player.isPlaying) player.pause() else player.play() },
                            // Двойное нажатие ставит лайк (уже стоящий не снимает: для этого есть кнопка).
                            onDoubleTap = { _, _ ->
                                if (status != LikeStatus.Like) onReact(LikeStatus.Like)
                                heartKey++
                            },
                            onBoost = { boost = it },
                        ),
                )
                AnimatedVisibility(
                    visible = showHeart,
                    enter = fadeIn() + scaleIn(initialScale = 0.5f),
                    exit = fadeOut() + scaleOut(targetScale = 1.4f),
                    modifier = Modifier.align(Alignment.Center),
                ) {
                    Icon(
                        Icons.Rounded.Favorite,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(112.dp),
                    )
                }
                if (boost) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp),
                    ) {
                        Text(
                            "${BoostSpeed.toInt()}× ▶▶",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }
                if (resolving) LoadingIndicator(Modifier.align(Alignment.Center))
                Column(
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    LikeButton(status, onClick = { onReact(LikeStatus.Like) })
                    DislikeButton(status, onClick = { onReact(LikeStatus.Dislike) })
                    CommentsButton(onClick = onComments)
                }
                error?.let {
                    Text(
                        text = it,
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    )
                }
            } else {
                AsyncImage(
                    model = "https://i.ytimg.com/vi/$id/mqdefault.jpg",
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
