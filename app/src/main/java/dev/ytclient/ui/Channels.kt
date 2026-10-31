package dev.ytclient.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.ytclient.innertube.Channel

/** Открыть страницу канала: функцию задаёт корень приложения, карточки и плеер просто вызывают её. */
val LocalOpenChannel = staticCompositionLocalOf<(String) -> Unit> { {} }

@Composable
fun ChannelAvatarImage(url: String?, size: Dp, modifier: Modifier = Modifier) {
    if (url != null) {
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
    } else {
        Icon(
            imageVector = Icons.Rounded.AccountCircle,
            contentDescription = null,
            modifier = modifier.size(size),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** «Подписаться» / «Вы подписаны»: подписанному показывается спокойная кнопка, остальным — акцентная. */
@Composable
fun SubscribeButton(subscribed: Boolean?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    if (subscribed == true) {
        FilledTonalButton(onClick = onClick, modifier = modifier, contentPadding = PaddingValues(horizontal = 16.dp)) {
            Text("Вы подписаны")
        }
    } else {
        Button(onClick = onClick, modifier = modifier, contentPadding = PaddingValues(horizontal = 16.dp)) {
            Text("Подписаться")
        }
    }
}

/** Горизонтальный ряд аватарок подписок; нажатие открывает страницу канала. */
@Composable
fun ChannelsRow(channels: List<Channel>, onOpen: (String) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(channels, key = { it.id }) { channel ->
            Column(
                modifier = Modifier.width(72.dp).clickable { onOpen(channel.id) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ChannelAvatarImage(channel.avatar, 64.dp)
                Text(
                    text = channel.name,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
