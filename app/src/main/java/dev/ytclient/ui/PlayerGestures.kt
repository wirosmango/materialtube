package dev.ytclient.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import androidx.media3.common.Player

/** Скорость, с которой идёт видео, пока палец зажат. */
const val BoostSpeed = 2f

/**
 * Жесты поверх видео: нажатие, двойное нажатие и зажатие. Пока палец зажат, видео идёт на [BoostSpeed],
 * после отпускания возвращается прежняя скорость; [onBoost] сообщает о начале и конце ускорения.
 */
@Composable
fun Modifier.playerGestures(
    player: Player,
    onTap: () -> Unit,
    onDoubleTap: (position: Offset, size: IntSize) -> Unit,
    onBoost: (Boolean) -> Unit,
): Modifier {
    val tap by rememberUpdatedState(onTap)
    val doubleTap by rememberUpdatedState(onDoubleTap)
    val boost by rememberUpdatedState(onBoost)
    return pointerInput(player) {
        var boosting = false
        var savedSpeed = 1f
        detectTapGestures(
            onPress = {
                tryAwaitRelease()
                if (boosting) {
                    boosting = false
                    player.setPlaybackSpeed(savedSpeed)
                    boost(false)
                }
            },
            onLongPress = {
                savedSpeed = player.playbackParameters.speed
                boosting = true
                player.setPlaybackSpeed(BoostSpeed)
                boost(true)
            },
            onDoubleTap = { doubleTap(it, size) },
            onTap = { tap() },
        )
    }
}
