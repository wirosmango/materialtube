package dev.ytclient.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.ytclient.innertube.LikeStatus

/** Кнопка лайка: статус null («неизвестно») показывается как не нажатая. */
@Composable
fun LikeButton(status: LikeStatus?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val checked = status == LikeStatus.Like
    FilledTonalIconToggleButton(checked = checked, onCheckedChange = { onClick() }, modifier = modifier) {
        Icon(
            imageVector = if (checked) Icons.Rounded.ThumbUp else Icons.Outlined.ThumbUp,
            contentDescription = if (checked) "Убрать лайк" else "Нравится",
        )
    }
}

@Composable
fun DislikeButton(status: LikeStatus?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val checked = status == LikeStatus.Dislike
    FilledTonalIconToggleButton(checked = checked, onCheckedChange = { onClick() }, modifier = modifier) {
        Icon(
            imageVector = if (checked) Icons.Rounded.ThumbDown else Icons.Outlined.ThumbDown,
            contentDescription = if (checked) "Убрать дизлайк" else "Не нравится",
        )
    }
}

@Composable
fun CommentsButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    FilledTonalIconButton(onClick = onClick, modifier = modifier) {
        Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = "Комментарии")
    }
}

@Composable
fun ShareButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    FilledTonalIconButton(onClick = onClick, modifier = modifier) {
        Icon(Icons.Rounded.Share, contentDescription = "Поделиться")
    }
}
