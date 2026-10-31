package dev.ytclient.ui

import android.view.Gravity
import android.view.TextureView
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import dev.ytclient.player.PlaybackMeta
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private val MiniPlayerMaxWidth = 480.dp
private val MiniPlayerCorner = 28.dp
private const val DismissFlingVelocity = 1500f

/**
 * Сколько места снизу списки оставляют под мини-плеером: высота карточки (16:9, как у неё самой) плюс зазор
 * до панели навигации. Карточка занимает ширину экрана минус поля, но не больше [MiniPlayerMaxWidth].
 */
@Composable
fun miniPlayerClearance(): Dp {
    val screen = LocalConfiguration.current.screenWidthDp.dp
    val width = minOf(screen - 32.dp, MiniPlayerMaxWidth)
    return width * 9f / 16f + 8.dp
}

/**
 * Мини-плеер как в официальном клиенте: парящая карточка 16:9, видео идёт прямо в ней. Поверх видео крупные
 * полупрозрачные кнопки «играть/пауза» (слева) и «закрыть» (справа), снизу — название и тонкая полоса прогресса.
 * Нажатие или свайп вверх разворачивают ролик, свайп вбок закрывает.
 * «Пауза» — скруглённый квадрат, «играть» — круг, как в большом плеере.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MiniPlayer(
    meta: PlaybackMeta,
    error: String?,
    player: Player,
    onExpand: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isPlaying by remember(player) { mutableStateOf(player.isPlaying) }
    var buffering by remember(player) { mutableStateOf(player.playbackState == Player.STATE_BUFFERING) }
    var position by remember(player) { mutableLongStateOf(player.currentPosition) }
    var duration by remember(player) { mutableLongStateOf(0L) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(value: Boolean) { isPlaying = value }
            override fun onPlaybackStateChanged(state: Int) { buffering = state == Player.STATE_BUFFERING }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    LaunchedEffect(player) {
        while (true) {
            position = player.currentPosition
            duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0L
            delay(400)
        }
    }

    // Свайп вбок закрывает: карточка уезжает за край и гаснет; если не дотянули — пружинисто возвращается.
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val dismissDistance = with(density) { 96.dp.toPx() }
    val expandDistance = with(density) { 24.dp.toPx() }
    var dragX by remember { mutableFloatStateOf(0f) }
    var dragY by remember { mutableFloatStateOf(0f) }

    Surface(
        shape = RoundedCornerShape(MiniPlayerCorner),
        color = Color.Black,
        shadowElevation = 8.dp,
        modifier = modifier
            .widthIn(max = MiniPlayerMaxWidth)
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .offset { IntOffset(dragX.roundToInt(), 0) }
            .graphicsLayer { alpha = 1f - (abs(dragX) / (dismissDistance * 4f)).coerceIn(0f, 1f) }
            .draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { dragX += it },
                onDragStopped = { velocity ->
                    scope.launch {
                        val flung = abs(velocity) > DismissFlingVelocity
                        if (abs(dragX) > dismissDistance || flung) {
                            val direction = if (dragX != 0f) dragX else velocity
                            val target = if (direction > 0f) dismissDistance * 4f else -dismissDistance * 4f
                            animate(dragX, target, animationSpec = tween(160)) { v, _ -> dragX = v }
                            onClose()
                            dragX = 0f
                        } else {
                            animate(dragX, 0f, animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f)) { v, _ ->
                                dragX = v
                            }
                        }
                    }
                },
            )
            .draggable(
                orientation = Orientation.Vertical,
                state = rememberDraggableState { dragY += it },
                onDragStopped = { velocity ->
                    if (dragY < -expandDistance || velocity < -800f) onExpand()
                    dragY = 0f
                },
            ),
    ) {
        Box(Modifier.fillMaxSize().clickable(onClick = onExpand)) {
            VideoSurface(player = player, cornerRadius = MiniPlayerCorner, modifier = Modifier.fillMaxSize())

            if (buffering && error == null) {
                LoadingIndicator(Modifier.align(Alignment.Center).size(48.dp))
            }

            MiniPlayPauseButton(
                playing = isPlaying,
                onClick = { if (player.isPlaying) player.pause() else player.play() },
                modifier = Modifier.align(Alignment.TopStart).padding(14.dp),
            )
            OverlayButton(
                onClick = onClose,
                modifier = Modifier.align(Alignment.TopEnd).padding(14.dp),
            ) {
                Icon(Icons.Rounded.Close, contentDescription = "Закрыть", tint = Color.White, modifier = Modifier.size(32.dp))
            }

            // Подпись снизу слева, как плашка «Есть реклама» в официальном клиенте: здесь в ней название ролика.
            // При ошибке воспроизведения вместо названия показывается ошибка.
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (error != null) {
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.92f)
                } else {
                    Color.Black.copy(alpha = 0.55f)
                },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 14.dp, end = 14.dp, bottom = 16.dp),
            ) {
                Text(
                    text = error ?: meta.title ?: "Загрузка…",
                    style = MaterialTheme.typography.titleSmall,
                    color = if (error != null) MaterialTheme.colorScheme.onErrorContainer else Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }

            // Для прямых эфиров длительности нет — полосу не рисуем.
            if (duration > 0) {
                LinearProgressIndicator(
                    progress = { (position.toFloat() / duration).coerceIn(0f, 1f) },
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.25f),
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp),
                )
            }
        }
    }
}

/** Крупная полупрозрачная круглая кнопка поверх видео. */
@Composable
private fun OverlayButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** «Пауза» — скруглённый квадрат, «играть» — круг; при переключении форма плавно перетекает. */
@Composable
private fun MiniPlayPauseButton(playing: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val radius by animateDpAsState(
        targetValue = if (playing) 16.dp else 26.dp,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "miniPlayShape",
    )
    Box(
        modifier = modifier
            .size(52.dp)
            .clip(RoundedCornerShape(radius))
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            contentDescription = if (playing) "Пауза" else "Играть",
            tint = Color.White,
            modifier = Modifier.size(32.dp),
        )
    }
}

/**
 * Видео на TextureView со скруглёнными углами. PlayerView рисует на SurfaceView, который не обрезается по
 * скруглению, поэтому здесь своя небольшая обёртка: TextureView внутри AspectRatioFrameLayout (режим «заполнить»,
 * лишнее обрезается), всё это в контейнере с обрезкой по контуру.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun VideoSurface(player: Player, cornerRadius: Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val radiusPx = with(LocalDensity.current) { cornerRadius.toPx() }
    val texture = remember(context) { TextureView(context) }

    var ratio by remember(player) { mutableFloatStateOf(videoRatioOf(player.videoSize)) }
    DisposableEffect(player, texture) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) { ratio = videoRatioOf(videoSize) }
        }
        player.addListener(listener)
        player.setVideoTextureView(texture)
        onDispose {
            player.removeListener(listener)
            // Если видео уже перехватил другой экран (развёрнутый плеер), этот вызов ничего не сбросит.
            player.clearVideoTextureView(texture)
        }
    }

    AndroidView(
        factory = { ctx ->
            val frame = AspectRatioFrameLayout(ctx).apply {
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                addView(texture, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            }
            FrameLayout(ctx).apply {
                setBackgroundColor(android.graphics.Color.BLACK)
                clipChildren = true
                outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(view: android.view.View, outline: android.graphics.Outline) {
                        outline.setRoundRect(0, 0, view.width, view.height, radiusPx)
                    }
                }
                clipToOutline = true
                addView(
                    frame,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        Gravity.CENTER,
                    ),
                )
            }
        },
        update = { root ->
            (root.getChildAt(0) as AspectRatioFrameLayout).setAspectRatio(ratio)
        },
        modifier = modifier,
    )
}

private fun videoRatioOf(size: VideoSize): Float =
    if (size.width == 0 || size.height == 0) 0f else size.width * size.pixelWidthHeightRatio / size.height
