package dev.ytclient.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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

    BackHandler(onBack = onBack)
    BackHandler(enabled = fullscreen) { fullscreen = false }

    Scaffold(
        topBar = {
            if (!fullscreen) TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Свернуть")
                    }
                },
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
                    Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                    contentAlignment = Alignment.Center,
                ) { LoadingIndicator() }

                is PlayerUiState.Error -> Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
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
                        onQuality = vm::setQuality,
                        onHlsQuality = vm::rememberQuality,
                        fullscreen = fullscreen,
                        onToggleFullscreen = { fullscreen = !fullscreen },
                        modifier = if (fullscreen) Modifier.weight(1f) else Modifier,
                    )
                    if (!fullscreen) LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                        item(key = "info") {
                            Column(
                                Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(current.info.title, style = MaterialTheme.typography.titleLarge)
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
                                Text(
                                    buildString {
                                        append("Клиент: ").append(current.info.clientKey)
                                        current.info.videoHeight?.let { append(" · ").append(it).append("p") }
                                        if (current.info.notes.isNotBlank()) append("\nДо этого: ").append(current.info.notes)
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
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
                }
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun VideoPlayer(
    info: StreamInfo,
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
