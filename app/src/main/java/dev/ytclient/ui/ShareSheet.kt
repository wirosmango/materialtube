package dev.ytclient.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.clickable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import dev.ytclient.Graph
import dev.ytclient.player.Downloader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Шторка «Поделиться»: отправить ссылку, скопировать ссылку, скачать видео в «Загрузки». */
@Composable
fun ShareSheet(videoId: String, title: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val link = "https://youtu.be/$videoId"
    var downloading by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding()) {
            Row("Поделиться", Icons.Rounded.Share) {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, title)
                    putExtra(Intent.EXTRA_TEXT, link)
                }
                context.startActivity(Intent.createChooser(send, null))
                onDismiss()
            }
            Row("Копировать ссылку", Icons.Rounded.ContentCopy) {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("youtube-link", link))
                Toast.makeText(context, "Ссылка скопирована", Toast.LENGTH_SHORT).show()
                onDismiss()
            }
            val start = { audioOnly: Boolean ->
                if (!downloading) {
                    downloading = true
                    val app = context.applicationContext
                    Toast.makeText(app, "Скачивание началось", Toast.LENGTH_SHORT).show()
                    // Скачивание живёт в appScope: закрытие шторки или экрана его не прерывает.
                    Graph.appScope.launch {
                        val message = try {
                            val info = Graph.innerTube.resolveStream(videoId, 480, forDownload = true)
                            val result = try {
                                Downloader.download(app, Graph.http, info, title, audioOnly)
                            } catch (e: Exception) {
                                if (!e.message.orEmpty().contains("403")) throw e
                                // Ссылки googlevideo быстро истекают; получаем свежие URL один раз.
                                val refreshed = Graph.innerTube.resolveStream(videoId, 480, forDownload = true)
                                Downloader.download(app, Graph.http, refreshed, title, audioOnly)
                            }
                            "Сохранено в «Загрузки»: $result"
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            "Не удалось скачать: " + (e.message ?: "ошибка")
                        }
                        Toast.makeText(app, message, Toast.LENGTH_LONG).show()
                    }
                    onDismiss()
                }
            }
            Row("Скачать видео (MP4)", Icons.Rounded.Download) { start(false) }
            Row("Скачать только звук (M4A)", Icons.Rounded.MusicNote) { start(true) }
        }
    }
}

@Composable
private fun Row(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(text) },
        leadingContent = { Icon(icon, contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick),
    )
}
