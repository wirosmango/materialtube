package dev.ytclient.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.ytclient.Graph
import dev.ytclient.innertube.Comment
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Нижняя шторка с комментариями. [token] — токен первой страницы (из `watchNext`);
 * null означает, что комментарии у ролика недоступны.
 */
@Composable
fun CommentsSheet(token: String?, onDismiss: () -> Unit) {
    val items = remember(token) { mutableStateListOf<Comment>() }
    var next by remember(token) { mutableStateOf(token) }
    var createParams by remember(token) { mutableStateOf<String?>(null) }
    var loading by remember(token) { mutableStateOf(false) }
    var error by remember(token) { mutableStateOf<String?>(null) }
    var loadedOnce by remember(token) { mutableStateOf(false) }
    var commentText by remember(token) { mutableStateOf("") }
    var sending by remember(token) { mutableStateOf(false) }
    var requestMore by remember(token) { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    suspend fun loadPage() {
        val t = next ?: return
        loading = true
        error = null
        try {
            val page = Graph.innerTube.comments(t)
            items += page.items.filter { n -> items.none { it.id == n.id } }
            createParams = createParams ?: page.createCommentParams
            next = page.continuation.takeIf { page.items.isNotEmpty() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: "Не удалось загрузить комментарии"
        }
        loading = false
        loadedOnce = true
    }

    LaunchedEffect(token, requestMore) { if (token != null && !loading) loadPage() }

    fun refresh() {
        items.clear()
        next = token
        error = null
        loadedOnce = false
        requestMore++
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            Text(
                "Комментарии",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            if (Graph.auth.current.loggedIn) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = commentText,
                        onValueChange = { commentText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Написать комментарий") },
                        maxLines = 4,
                        enabled = !sending,
                    )
                    Button(
                        enabled = commentText.isNotBlank() && !sending,
                        onClick = {
                            scope.launch {
                                sending = true
                                try {
                                    val params = createParams ?: error("Не найден токен для комментария")
                                    Graph.innerTube.createComment(params, commentText.trim())
                                    commentText = ""
                                    refresh()
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    Toast.makeText(context, e.message ?: "Не удалось отправить комментарий", Toast.LENGTH_LONG).show()
                                } finally {
                                    sending = false
                                }
                            }
                        },
                    ) { Text(if (sending) "…" else "Отправить") }
                }
            } else {
                Text(
                    "Войдите в аккаунт, чтобы писать комментарии и ставить лайки",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
            }
            when {
                token == null -> Message("Комментарии недоступны")
                items.isEmpty() && loading -> Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) {
                    LoadingIndicator()
                }
                items.isEmpty() && error != null -> Column(
                    Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(error!!, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                    Button(onClick = { requestMore++ }) { Text("Повторить") }
                }
                items.isEmpty() && loadedOnce -> Message("Комментариев нет или не удалось их разобрать")
                else -> LazyColumn(Modifier.weight(1f, fill = false)) {
                    items(items, key = { it.id }) { comment ->
                        CommentRow(comment) { liked ->
                            val index = items.indexOfFirst { it.id == comment.id }
                            if (index < 0) return@CommentRow
                            val previous = items[index]
                            items[index] = previous.copy(liked = liked)
                            scope.launch {
                                try {
                                    Graph.innerTube.reactComment(comment.id, liked)
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    if (index < items.size && items[index].id == comment.id) items[index] = previous
                                    Toast.makeText(context, e.message ?: "Не удалось поставить лайк", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                    if (next != null) {
                        item(key = "more") {
                            Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) {
                                if (error != null) {
                                    Button(onClick = { requestMore++ }) { Text("Повторить") }
                                } else {
                                    LoadingIndicator()
                                    LaunchedEffect(next) { if (!loading) requestMore++ }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Message(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(32.dp),
    )
}

@Composable
private fun CommentRow(c: Comment, onLike: (Boolean) -> Unit) {
    val context = LocalContext.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (c.avatar != null) {
            AsyncImage(
                model = c.avatar,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(36.dp).clip(CircleShape),
            )
        } else {
            Icon(Icons.Rounded.AccountCircle, null, Modifier.size(36.dp), MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                listOfNotNull(c.author.ifBlank { null }, c.published).joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(c.text, style = MaterialTheme.typography.bodyMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledTonalIconButton(
                    onClick = {
                        if (Graph.auth.current.loggedIn) onLike(c.liked != true)
                        else Toast.makeText(context, "Войдите в аккаунт, чтобы ставить лайки", Toast.LENGTH_SHORT).show()
                    },
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = if (c.liked == true) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Icon(Icons.Rounded.ThumbUp, contentDescription = "Лайк")
                }
                c.likes?.let {
                    Text("$it лайков", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
