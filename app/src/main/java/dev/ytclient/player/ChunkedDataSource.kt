package dev.ytclient.player

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.TransferListener

/**
 * googlevideo отвечает 403 на открытый диапазон (`Range: bytes=0-`), который ExoPlayer шлёт по умолчанию,
 * но отдаёт 206 на ограниченный. Эта обёртка превращает один длинный запрос в цепочку запросов
 * по [chunkSize] байт, а плееру выглядит как обычный непрерывный поток.
 */
@UnstableApi
class ChunkedDataSource(
    private val upstream: DataSource,
    private val chunkSize: Long = DEFAULT_CHUNK_SIZE,
) : DataSource {

    private var spec: DataSpec? = null
    private var position = 0L
    private var end = Long.MAX_VALUE
    private var chunkRemaining = 0L
    private var chunkOpen = false

    override fun addTransferListener(transferListener: TransferListener) {
        upstream.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        spec = dataSpec
        position = dataSpec.position
        end = if (dataSpec.length == C.LENGTH_UNSET.toLong()) {
            Long.MAX_VALUE
        } else {
            dataSpec.position + dataSpec.length
        }
        openChunk()
        return if (end == Long.MAX_VALUE) C.LENGTH_UNSET.toLong() else end - dataSpec.position
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        while (true) {
            if (position >= end) return C.RESULT_END_OF_INPUT
            if (!chunkOpen) openChunk()

            val toRead = minOf(length.toLong(), chunkRemaining).toInt()
            val read = upstream.read(buffer, offset, toRead)
            if (read == C.RESULT_END_OF_INPUT) {
                closeChunk()
                if (chunkRemaining > 0) {
                    // Сервер закончил раньше, чем мы просили: это конец файла.
                    end = position
                    return C.RESULT_END_OF_INPUT
                }
                continue
            }

            position += read
            chunkRemaining -= read
            if (chunkRemaining == 0L) closeChunk()
            return read
        }
    }

    override fun getUri(): Uri? = spec?.uri

    override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders

    override fun close() {
        closeChunk()
        spec = null
    }

    private fun openChunk() {
        val base = checkNotNull(spec)
        val length = minOf(chunkSize, end - position)
        val sub = base.subrange(position - base.position, length)
        val opened = openWithFallback(sub)
        chunkOpen = true
        chunkRemaining = if (opened == C.LENGTH_UNSET.toLong()) length else opened

        // Content-Range: bytes 0-1048575/9876543 — отсюда узнаём полный размер файла.
        val total = upstream.responseHeaders.entries
            .firstOrNull { it.key.equals("Content-Range", ignoreCase = true) }
            ?.value?.firstOrNull()
            ?.substringAfter('/', "")
            ?.toLongOrNull()
        val size = total ?: base.uri.getQueryParameter("clen")?.toLongOrNull()
        if (size != null) end = minOf(end, size)
    }

    /** Один повтор после 403: одиночный отказ бывает случайным. */
    private fun openWithFallback(sub: DataSpec): Long {
        try {
            return upstream.open(sub)
        } catch (e: HttpDataSource.InvalidResponseCodeException) {
            if (e.responseCode != 403) throw e
            upstream.close()
        }
        Thread.sleep(300)
        return upstream.open(sub)
    }

    private fun closeChunk() {
        if (chunkOpen) {
            upstream.close()
            chunkOpen = false
        }
    }

    companion object {
        /** 1 МиБ: проверено, что googlevideo отдаёт такие куски. Куски по 10 МиБ ломали загрузку. */
        const val DEFAULT_CHUNK_SIZE = 1024L * 1024L
    }
}
