package dev.ytclient.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.C
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import kotlinx.coroutines.delay

private val Speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

/**
 * Свои кнопки плеера вместо стандартного контроллера Media3: цвета из динамической палитры (Monet),
 * «пауза» — скруглённый квадрат, «играть» — круг (форма плавно перетекает). Настройки (скорость, качество, звук)
 * открываются шестерёнкой.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlayerControls(
    player: Player,
    fullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    qualityOptions: List<Int>,
    qualityCurrent: Int?,
    onQuality: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isPlaying by remember(player) { mutableStateOf(player.isPlaying) }
    var buffering by remember(player) { mutableStateOf(player.playbackState == Player.STATE_BUFFERING) }
    var speed by remember(player) { mutableFloatStateOf(player.playbackParameters.speed) }
    var audioGroups by remember(player) { mutableStateOf(audioGroupsOf(player.currentTracks)) }
    var audioVersion by remember(player) { mutableIntStateOf(0) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(value: Boolean) { isPlaying = value }
            override fun onPlaybackStateChanged(state: Int) { buffering = state == Player.STATE_BUFFERING }
            override fun onPlaybackParametersChanged(parameters: PlaybackParameters) { speed = parameters.speed }
            override fun onTracksChanged(tracks: Tracks) {
                audioGroups = audioGroupsOf(tracks)
                audioVersion++
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    var visible by remember { mutableStateOf(true) }
    var touch by remember { mutableIntStateOf(0) }
    var settingsOpen by remember { mutableStateOf(false) }
    var position by remember(player) { mutableLongStateOf(0L) }
    var duration by remember(player) { mutableLongStateOf(0L) }
    var scrubbing by remember { mutableStateOf(false) }
    var scrubFraction by remember { mutableFloatStateOf(0f) }

    // Позиция опрашивается, только пока кнопки видны.
    LaunchedEffect(player, visible) {
        while (visible) {
            if (!scrubbing) position = player.currentPosition
            duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0L
            delay(250)
        }
    }
    // Кнопки сами прячутся через несколько секунд, если видео идёт и с ними никто не взаимодействует.
    LaunchedEffect(visible, isPlaying, settingsOpen, touch, scrubbing) {
        if (visible && isPlaying && !settingsOpen && !scrubbing) {
            delay(3500)
            visible = false
        }
    }

    // Подсказка о перемотке двойным нажатием («−10 с» слева, «+10 с» справа) и значок ускорения при зажатии.
    var hint by remember { mutableStateOf<String?>(null) }
    var hintLeft by remember { mutableStateOf(false) }
    var hintKey by remember { mutableIntStateOf(0) }
    var boost by remember { mutableStateOf(false) }
    LaunchedEffect(hintKey) {
        if (hintKey > 0) {
            delay(600)
            hint = null
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .playerGestures(
                player = player,
                onTap = { visible = !visible },
                onDoubleTap = { offset, size ->
                    val third = size.width / 3f
                    when {
                        offset.x < third -> {
                            player.seekTo((player.currentPosition - 10_000).coerceAtLeast(0))
                            hint = "−10 с"; hintLeft = true; hintKey++
                        }
                        offset.x > 2 * third -> {
                            val target = player.currentPosition + 10_000
                            val total = player.duration.takeIf { it != C.TIME_UNSET && it > 0 }
                            player.seekTo(if (total != null) target.coerceAtMost(total) else target)
                            hint = "+10 с"; hintLeft = false; hintKey++
                        }
                        else -> if (player.isPlaying) player.pause() else player.play()
                    }
                },
                onBoost = { boost = it },
            ),
    ) {
        if (buffering) LoadingIndicator(Modifier.align(Alignment.Center))

        hint?.let {
            GestureBadge(
                text = it,
                modifier = Modifier
                    .align(if (hintLeft) Alignment.CenterStart else Alignment.CenterEnd)
                    .padding(horizontal = 40.dp),
            )
        }
        if (boost) {
            GestureBadge("${BoostSpeed.toInt()}× ▶▶", Modifier.align(Alignment.TopCenter).padding(top = 16.dp))
        }

        AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f))) {
                ControlButton(
                    icon = Icons.Rounded.Settings,
                    description = "Настройки",
                    onClick = { touch++; settingsOpen = true },
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                )

                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ControlButton(
                        icon = Icons.Rounded.Replay10,
                        description = "Назад на 10 секунд",
                        onClick = { touch++; player.seekTo((player.currentPosition - 10_000).coerceAtLeast(0)) },
                    )
                    PlayPauseButton(
                        playing = isPlaying,
                        onClick = {
                            touch++
                            if (player.isPlaying) player.pause() else player.play()
                        },
                    )
                    ControlButton(
                        icon = Icons.Rounded.Forward10,
                        description = "Вперёд на 10 секунд",
                        onClick = {
                            touch++
                            val target = player.currentPosition + 10_000
                            player.seekTo(if (duration > 0) target.coerceAtMost(duration) else target)
                        },
                    )
                }

                Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val shown = if (scrubbing) (scrubFraction * duration).toLong() else position
                        Text(
                            text = if (duration > 0) "${formatTime(shown)} / ${formatTime(duration)}" else formatTime(shown),
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White,
                            modifier = Modifier.weight(1f).padding(start = 8.dp),
                        )
                        ControlButton(
                            icon = if (fullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                            description = if (fullscreen) "Свернуть" else "Во весь экран",
                            onClick = { touch++; onToggleFullscreen() },
                            size = 40.dp,
                        )
                    }
                    if (duration > 0) {
                        Slider(
                            value = if (scrubbing) scrubFraction else (position.toFloat() / duration).coerceIn(0f, 1f),
                            onValueChange = {
                                scrubbing = true
                                scrubFraction = it
                            },
                            onValueChangeFinished = {
                                player.seekTo((scrubFraction * duration).toLong())
                                position = (scrubFraction * duration).toLong()
                                scrubbing = false
                            },
                        )
                    }
                }
            }
        }
    }

    if (settingsOpen) {
        Dialog(
            onDismissRequest = { settingsOpen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.padding(24.dp).widthIn(max = 440.dp),
            ) {
                Column(
                    Modifier.padding(20.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    SettingsSection("Скорость") {
                        Speeds.forEach { s ->
                            FilterChip(
                                selected = speed == s,
                                onClick = { player.setPlaybackSpeed(s) },
                                label = { Text(if (s == 1f) "Обычная" else "${s}×") },
                            )
                        }
                    }
                    if (qualityOptions.size >= 2) {
                        SettingsSection("Качество") {
                            qualityOptions.forEach { h ->
                                FilterChip(
                                    selected = h == qualityCurrent,
                                    onClick = {
                                        if (h != qualityCurrent) {
                                            settingsOpen = false
                                            onQuality(h)
                                        }
                                    },
                                    label = { Text("${h}p") },
                                )
                            }
                        }
                    }
                    if (audioGroups.size >= 2) {
                        SettingsSection("Звук") {
                            audioGroups.forEachIndexed { index, group ->
                                // audioVersion перечитывает выбранную дорожку после переключения.
                                val selected = audioVersion >= 0 && group.isSelected
                                val format = group.getTrackFormat(0)
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                                            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, 0))
                                            .build()
                                    },
                                    label = { Text(format.label ?: format.language ?: "Дорожка ${index + 1}") },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GestureBadge(text: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = modifier,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) { content() }
    }
}

@Composable
private fun ControlButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = modifier.size(size),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = description, modifier = Modifier.size(size * 0.55f))
        }
    }
}

/** «Пауза» — скруглённый квадрат, «играть» — круг; при переключении форма плавно перетекает. */
@Composable
private fun PlayPauseButton(playing: Boolean, onClick: () -> Unit) {
    val radius by animateDpAsState(
        targetValue = if (playing) 22.dp else 36.dp,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "playShape",
    )
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(radius),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.size(72.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (playing) "Пауза" else "Играть",
                modifier = Modifier.size(40.dp),
            )
        }
    }
}

private fun audioGroupsOf(tracks: Tracks): List<Tracks.Group> =
    tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO && it.length > 0 }

private fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
