package dev.ytclient

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.OutputStream

/**
 * Скачивание видео в галерею (Movies/MaterialTube). Берётся готовый файл «картинка+звук» (обычно 360p):
 * раздельные потоки пришлось бы склеивать, а HLS файлом не является. Работает в своём scope, поэтому
 * продолжается, пока приложение живо, даже если закрыть экран плеера.
 */
class Downloader(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _progress = MutableStateFlow<Map<String, Int>>(emptyMap())
    /** Процент скачивания по id видео; записи нет — не качается. */
    val progress: StateFlow<Map<String, Int>> = _progress.asStateFlow()

    fun start(videoId: String, title: String) {
        if (_progress.value.containsKey(videoId)) return
        _progress.update { it + (videoId to 0) }
        toast("Скачивание началось")
        scope.launch {
            try {
                val info = Graph.innerTube.resolveStream(videoId, forDownload = true)
                val url = info.muxedUrl ?: error("Нет файла для скачивания")
                val name = title.replace(Regex("[\\\\/:*?\"<>|\\n\\r]"), "_").trim().take(80).ifBlank { videoId }
                val where = save(videoId, name, url, info.userAgent)
                toast("Сохранено: $where")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                toast("Не удалось скачать: ${e.message}")
            } finally {
                _progress.update { it - videoId }
            }
        }
    }

    private suspend fun save(videoId: String, name: String, url: String, userAgent: String): String =
        withContext(Dispatchers.IO) {
            var finish: () -> Unit = {}
            var abort: () -> Unit = {}
            val out: OutputStream
            val where: String
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, "$name.mp4")
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/MaterialTube")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                    ?: error("Не удалось создать файл")
                out = resolver.openOutputStream(uri) ?: error("Не удалось открыть файл")
                finish = {
                    resolver.update(uri, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null)
                }
                abort = { resolver.delete(uri, null, null) }
                where = "Movies/MaterialTube/$name.mp4"
            } else {
                val dir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: error("Нет доступа к памяти")
                val file = File(dir, "$name.mp4")
                out = file.outputStream()
                abort = { file.delete() }
                where = file.absolutePath
            }

            try {
                out.use { stream ->
                    var position = 0L
                    var total = Long.MAX_VALUE
                    while (position < total) {
                        // Ограниченные куски: googlevideo режет открытый диапазон 403.
                        val request = Request.Builder()
                            .url(url)
                            .header("User-Agent", userAgent)
                            .header("Range", "bytes=$position-${position + CHUNK - 1}")
                            .build()
                        Graph.http.newCall(request).execute().use { response ->
                            if (response.code !in 200..299) error("HTTP ${response.code}")
                            response.header("Content-Range")?.substringAfter('/', "")?.toLongOrNull()
                                ?.let { total = it }
                            val body = response.body ?: error("Пустой ответ")
                            val written = body.byteStream().copyTo(stream)
                            if (written == 0L) total = position
                            position += written
                            if (response.code == 200) total = position // сервер отдал файл целиком
                        }
                        if (total != Long.MAX_VALUE && total > 0) {
                            val percent = (position * 100 / total).toInt().coerceIn(0, 100)
                            _progress.update { it + (videoId to percent) }
                        }
                    }
                }
                finish()
            } catch (e: Exception) {
                abort()
                throw e
            }
            where
        }

    private fun toast(text: String) {
        android.os.Handler(context.mainLooper).post { Toast.makeText(context, text, Toast.LENGTH_LONG).show() }
    }

    private companion object {
        const val CHUNK = 1024L * 1024L
    }
}
