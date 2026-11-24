package dev.ytclient.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.C
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import dev.ytclient.Graph
import dev.ytclient.innertube.Channel
import dev.ytclient.innertube.LikeStatus
import dev.ytclient.innertube.StreamInfo

@Composable
fun PlayerScreen(videoId: String, onBack: () -> Unit, onOpenVideo: (String) -> Unit) {
    val vm: PlayerViewModel = viewModel(key = "player-$videoId") { PlayerViewModel(videoId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val related by vm.related.collectAsStateWithLifecycle()
    val failure by vm.failure.collectAsStateWithLifecycle()
    val channel by vm.channel.collectAsStateWithLifecycle()
    val subscribed by vm.subscribed.collectAsStateWithLifecycle()
    val openChannel = LocalOpenChannel.current
    val status by vm.status.collectAsStateWithLifecycle()
    val commentsToken by vm.commentsToken.collectAsStateWithLifecycle()
    var showComments by remember { mutableStateOf(false) }
    var showShare by remember { mutableStateOf(false) }
    var descriptionOpen by remember { mutableStateOf(false) }
    val context = LocalContext.current

    if (showShare) {
        ShareSheet(
            videoId = videoId,
            title = (state as? PlayerUiState.Ready)?.info?.title.orEmpty(),
            onDismiss = { showShare = false },
        )
    }
    if (showComments) CommentsSheet(token = commentsToken, onDismiss = { showComments = false })

    // Плеер живёт в сессии и не зависит от экрана: здесь он только подключается к роликам и показывается.
    LaunchedEffect(vm) { Graph.playback.attach(vm) }

    var fullscreen by remember { mutableStateOf(false) }
    FullscreenEffect(fullscreen)

    val scope = rememberCoroutineScope()
    val dismissDistance = with(LocalDensity.current) { 96.dp.toPx() }
    var dragY by remember { mutableFloatStateOf(0f) }
    val swipeDownModifier = Modifier.draggable(
        orientation = Orientation.Vertical,
        enabled = !fullscreen && !descriptionOpen,
        state = rememberDraggableState { delta ->
            dragY = (dragY + delta).coerceAtLeast(0f)
        },
        onDragStopped = { velocity ->
            scope.launch {
                if (dragY > dismissDistance || velocity > 800f) {
                    val target = dismissDistance * 3.5f
                    animate(dragY, target, animationSpec = tween(150)) { v, _ -> dragY = v }
                    onBack()
                    dragY = 0f
                } else {
                    animate(dragY, 0f, animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f)) { v, _ ->
                        dragY = v
                    }
                }
            }
        },
    )

    BackHandler(onBack = onBack)
    // «Назад» сначала закрывает описание, затем полноэкранный режим.
    BackHandler(enabled = descriptionOpen) { descriptionOpen = false }
    BackHandler(enabled = fullscreen && !descriptionOpen) { fullscreen = false }

    Scaffold(
        modifier = Modifier
            .offset { IntOffset(0, dragY.roundToInt()) }
            .graphicsLayer { alpha = 1f - (dragY / (dismissDistance * 4f)).coerceIn(0f, 0.45f) },
        topBar = {
            if (!fullscreen) TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Свернуть")
                    }
                },
                modifier = swipeDownModifier,
            )
        },
    ) { padding ->
        Column(
            Modifier
                .then(if (fullscreen) Modifier.background(Color.Black) else Modifier.padding(padding))
                .fillMaxSize(),
        ) {
            when (val current = state) {
                PlayerUiState.Loading -> Box(
                    Modifier.fillMaxWidth().aspectRatio(16f / 9f).then(swipeDownModifier),
                    contentAlignment = Alignment.Center,
                ) { LoadingIndicator() }

                is PlayerUiState.Error -> Column(
                    modifier = Modifier.fillMaxWidth().then(swipeDownModifier).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text = current.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                    Button(onClick = vm::load) { Text("Повторить") }
                }

                is PlayerUiState.Ready -> {
                    VideoPlayer(
                        info = current.info,
                        channel = channel,
                        descriptionOpen = descriptionOpen,
                        onOpenDescription = { descriptionOpen = true },
                        onCloseDescription = { descriptionOpen = false },
                        onQuality = vm::setQuality,
                        onHlsQuality = vm::rememberQuality,
                        fullscreen = fullscreen,
                        onToggleFullscreen = { fullscreen = !fullscreen },
                        modifier = if (fullscreen) Modifier.weight(1f) else swipeDownModifier,
                    )
                    if (!fullscreen) Box(Modifier.weight(1f).fillMaxWidth()) {
                        LazyColumn(Modifier.fillMaxSize()) {
                            item(key = "info") {
                                Column(
                                    Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Text(
                                        text = current.info.title,
                                        style = MaterialTheme.typography.titleLarge,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { descriptionOpen = true },
                                    )
                                    val owner = channel
                                    if (owner != null) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clip(RoundedCornerShape(24.dp))
                                                    .clickable { openChannel(owner.id) },
                                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                ChannelAvatarImage(owner.avatar, 40.dp)
                                                Text(
                                                    owner.name,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                            SubscribeButton(
                                                subscribed = subscribed,
                                                onClick = {
                                                    vm.toggleSubscribe { message ->
                                                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                            )
                                        }
                                    } else {
                                        current.info.author?.let {
                                            Text(
                                                it,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                    // Строку «Клиент: …» убрали; остаётся только причина отката, если первые клиенты не сработали.
                                    if (current.info.notes.isNotBlank()) {
                                        Text(
                                            text = "До этого: " + current.info.notes,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    val toast = { message: String ->
                                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        LikeButton(status, onClick = { vm.react(LikeStatus.Like, toast) })
                                        DislikeButton(status, onClick = { vm.react(LikeStatus.Dislike, toast) })
                                        CommentsButton(onClick = { showComments = true })
                                        ShareButton(onClick = { showShare = true })
                                    }
                                }
                            }
                            items(related, key = { it.id }) { item ->
                                VideoCard(item = item, onClick = { onOpenVideo(item.id) })
                            }
                        }
                        // Описание выезжает снизу и занимает всё место под видео, пока его не закроют крестиком.
                        DescriptionPanel(
                            visible = descriptionOpen,
                            fromRight = false,
                            info = current.info,
                            channel = channel,
                            onClose = { descriptionOpen = false },
                            modifier = Modifier.align(Alignment.BottomCenter).fillMaxSize(),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun VideoPlayer(
    info: StreamInfo,
    channel: Channel?,
    descriptionOpen: Boolean,
    onOpenDescription: () -> Unit,
    onCloseDescription: () -> Unit,
    onQuality: (height: Int, positionMs: Long) -> Unit,
    onHlsQuality: (Int) -> Unit,
    fullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Плеер принадлежит сессии (см. PlaybackSession): он продолжает играть, когда экран свёрнут в мини-плеер.
    val player = Graph.playback.player
    val playbackError by Graph.playback.error.collectAsStateWithLifecycle()

    // Для HLS список качеств берём из дорожек, а текущее — из размера кадра. Начальные значения читаем из плеера:
    // при повторном открытии ролика, который уже играет, onTracksChanged второй раз не придёт.
    var hlsHeights by remember(player) { mutableStateOf(videoHeightsOf(player.currentTracks)) }
    var hlsCurrent by remember(player) { mutableStateOf(player.videoSize.height.takeIf { it > 0 }) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) {
                hlsHeights = videoHeightsOf(tracks)
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.height > 0) hlsCurrent = videoSize.height
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    Column(modifier) {
        Box(if (fullscreen) Modifier.fillMaxSize() else Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        this.player = player
                        useController = false
                    }
                },
                update = { it.player = player },
                modifier = Modifier.fillMaxSize(),
            )
            PlayerControls(
                player = player,
                title = if (fullscreen) info.title else "",
                onTitleClick = if (fullscreen) onOpenDescription else null,
                fullscreen = fullscreen,
                onToggleFullscreen = onToggleFullscreen,
                qualityOptions = if (info.isHls) hlsHeights else info.availableHeights,
                qualityCurrent = if (info.isHls) hlsCurrent else info.videoHeight,
                onQuality = { h ->
                    if (info.isHls) {
                        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                            .setMaxVideoSize(Int.MAX_VALUE, h)
                            .build()
                        onHlsQuality(h)
                    } else {
                        onQuality(h, player.currentPosition)
                    }
                },
            )
            // В полноэкранном режиме панель описания выезжает справа поверх видео.
            if (fullscreen) {
                DescriptionPanel(
                    visible = descriptionOpen,
                    fromRight = true,
                    info = info,
                    channel = channel,
                    onClose = onCloseDescription,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .widthIn(max = 640.dp)
                        .fillMaxWidth(0.7f)
                        .padding(12.dp),
                )
            }
        }
        playbackError?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

private fun videoHeightsOf(tracks: Tracks): List<Int> =
    tracks.groups
        .filter { it.type == C.TRACK_TYPE_VIDEO }
        .flatMap { g -> (0 until g.length).map { g.getTrackFormat(it).height } }
        .filter { it > 0 }
        .distinct()
        .sortedDescending()

/**
 * Панель описания в стиле MD3 Expressive: скруглённый лист, сегментированные карточки метаданных и абзацев,
 * закрытие крестиком в правом верхнем углу. В обычном режиме выезжает снизу, в полноэкранном — справа.
 */
@Composable
private fun DescriptionPanel(
    visible: Boolean,
    fromRight: Boolean,
    info: StreamInfo,
    channel: Channel?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val enter = if (fromRight) {
        slideInHorizontally(initialOffsetX = { it }) + fadeIn()
    } else {
        slideInVertically(initialOffsetY = { it }) + fadeIn()
    }
    val exit = if (fromRight) {
        slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
    } else {
        slideOutVertically(targetOffsetY = { it }) + fadeOut()
    }
    val outerShape = if (fromRight) {
        RoundedCornerShape(28.dp)
    } else {
        RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
    }

    val authorName = channel?.name?.takeIf { it.isNotBlank() } ?: info.author?.takeIf { it.isNotBlank() }
    val facts = buildList {
        if (authorName != null) add("Канал" to authorName)
        info.durationSeconds?.takeIf { it > 0 }?.let { add("Длительность" to formatDuration(it)) }
        if (info.isLive) add("Формат" to "Прямой эфир")
        info.videoHeight?.let { add("Качество" to "${it}p") }
    }
    val paragraphs = remember(info.description) {
        info.description
            .trim()
            .split(Regex("\\n\\s*\\n+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .ifEmpty { listOf("У этого видео нет описания.") }
    }

    AnimatedVisibility(
        visible = visible,
        enter = enter,
        exit = exit,
        modifier = modifier,
    ) {
        // Кликабельная поверхность перехватывает тапы, чтобы они не уходили в список или плеер под панелью.
        Surface(
            onClick = {},
            shape = outerShape,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 2.dp,
            shadowElevation = if (fromRight) 12.dp else 6.dp,
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "close") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ) {
                            Text(
                                text = "О видео",
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            )
                        }
                        FilledTonalIconButton(onClick = onClose) {
                            Icon(Icons.Rounded.Close, contentDescription = "Закрыть описание")
                        }
                    }
                }

                // Выразительная карточка с названием видео (чуть ниже крестика).
                item(key = "title") {
                    Surface(
                        shape = RoundedCornerShape(28.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                text = info.title,
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            if (channel != null || authorName != null) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    if (channel != null) {
                                        ChannelAvatarImage(channel.avatar, 36.dp)
                                    }
                                    Text(
                                        text = authorName.orEmpty(),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }

                // Сегментированный список фактов о ролике (MD3 Expressive segmented group).
                if (facts.isNotEmpty()) {
                    item(key = "facts") {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            facts.forEachIndexed { index, (label, value) ->
                                Surface(
                                    shape = segmentShape(index, facts.size),
                                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 20.dp, vertical = 14.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Text(
                                            text = value,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(start = 16.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item(key = "desc-header") {
                    Text(
                        text = "Описание",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp, top = 4.dp),
                    )
                }

                // Сегментированные блоки абзацев описания: внешние углы крупные (24.dp), внутренние — малые (6.dp).
                item(key = "desc-segments") {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        paragraphs.forEachIndexed { index, block ->
                            Surface(
                                shape = segmentShape(index, paragraphs.size),
                                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = block,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Скругление элемента в сегментированной группе MD3 Expressive: внешние края 24.dp, стыки — 6.dp. */
private fun segmentShape(index: Int, total: Int): RoundedCornerShape {
    val outer = 24.dp
    val inner = 6.dp
    return when {
        total <= 1 -> RoundedCornerShape(outer)
        index == 0 -> RoundedCornerShape(topStart = outer, topEnd = outer, bottomStart = inner, bottomEnd = inner)
        index == total - 1 -> RoundedCornerShape(topStart = inner, topEnd = inner, bottomStart = outer, bottomEnd = outer)
        else -> RoundedCornerShape(inner)
    }
}

private fun formatDuration(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** Полноэкранный режим: альбомная ориентация и скрытые системные панели; при выходе всё возвращается. */
@Composable
private fun FullscreenEffect(fullscreen: Boolean) {
    val activity = LocalContext.current.findActivity() ?: return
    DisposableEffect(fullscreen) {
        val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        if (fullscreen) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
