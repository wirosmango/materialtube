package dev.ytclient.player

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.provider.MediaStore
import dev.ytclient.innertube.StreamInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.OutputStream
import java.nio.ByteBuffer

/**
 * Скачивание ролика в «Загрузки». Раздельные video/audio-потоки склеиваются в MP4;
 * готовый muxed-файл используется напрямую. Читает кусками по 1 МиБ с заголовком Range.
 */
object Downloader {
    private const val CHUNK = 1024L * 1024L

    /**
     * HLS скачивается сегментами. Для обычных потоков раздельные дорожки сначала
     * скачиваются во временные файлы и объединяются через MediaMuxer.
     */
    suspend fun download(
        context: Context,
        http: OkHttpClient,
        info: StreamInfo,
        title: String,
        audioOnly: Boolean = false,
    ): String = when {
        info.hlsUrl != null && info.isHls -> downloadHls(context, http, info, title, audioOnly)
        audioOnly -> downloadAudio(context, http, info, title)
        info.videoUrl != null && info.audioUrl != null -> downloadSeparate(context, http, info, title)
        else -> downloadDirect(context, http, info, title)
    }

    private suspend fun downloadAudio(
        context: Context,
        http: OkHttpClient,
        info: StreamInfo,
        title: String,
    ): String = withContext(Dispatchers.IO) {
        val source = info.audioUrl ?: error("Для этого видео нет аудиопотока")
        val temp = File.createTempFile("dl-audio", ".mp4", context.cacheDir)
        try {
            downloadFile(http, source, info.userAgent, temp)
            val name = fileName(title, info, ".m4a")
            val (out, finish) = openOutput(context, name, "audio/mp4")
            var done = false
            try {
                out.use { stream -> temp.inputStream().use { it.copyTo(stream) } }
                done = true
            } finally {
                finish(done)
            }
            name
        } finally {
            temp.delete()
        }
    }

    private suspend fun downloadSeparate(
        context: Context,
        http: OkHttpClient,
        info: StreamInfo,
        title: String,
    ): String = withContext(Dispatchers.IO) {
        val video = File.createTempFile("dl-video", ".mp4", context.cacheDir)
        val audio = File.createTempFile("dl-audio", ".mp4", context.cacheDir)
        val muxed = File.createTempFile("dl-muxed", ".mp4", context.cacheDir)
        try {
            downloadFile(http, info.videoUrl!!, info.userAgent, video)
            downloadFile(http, info.audioUrl!!, info.userAgent, audio)
            mux(listOf(video to "video/", audio to "audio/"), muxed)
            val name = fileName(title, info)
            val (out, finish) = openOutput(context, name, "video/mp4")
            var done = false
            try {
                out.use { stream -> muxed.inputStream().use { it.copyTo(stream) } }
                done = true
            } finally {
                finish(done)
            }
            name
        } finally {
            video.delete()
            audio.delete()
            muxed.delete()
        }
    }

    private fun downloadFile(http: OkHttpClient, url: String, userAgent: String, target: File) {
        var position = 0L
        var total = Long.MAX_VALUE
        target.outputStream().use { stream ->
            while (position < total) {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", userAgent)
                    .header("Accept", "*/*")
                    .header("Referer", "https://www.youtube.com/")
                    .header("Range", "bytes=$position-${position + CHUNK - 1}")
                    .build()
                http.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) error("Сервер ответил ${response.code}")
                    response.header("Content-Range")?.substringAfter('/')?.toLongOrNull()?.let { total = it }
                    val body = response.body ?: error("Пустой ответ")
                    val bytes = body.bytes()
                    if (bytes.isEmpty()) total = position
                    stream.write(bytes)
                    position += bytes.size
                    if (response.code == 200 || (total == Long.MAX_VALUE && bytes.size < CHUNK)) total = position
                }
            }
        }
    }

    private suspend fun downloadHls(
        context: Context,
        http: OkHttpClient,
        info: StreamInfo,
        title: String,
        audioOnly: Boolean,
    ): String =
        withContext(Dispatchers.IO) {
            val name = fileName(title, info, if (audioOnly) ".m4a" else ".mp4")
            val mime = if (audioOnly) "audio/mp4" else "video/mp4"
            fun get(url: String, range: String? = null): okhttp3.Response {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", info.userAgent)
                    .header("Accept", "*/*")
                    .header("Referer", "https://www.youtube.com/")
                    .apply { if (range != null) header("Range", range) }.build()
                val response = http.newCall(request).execute()
                if (!response.isSuccessful) {
                    val code = response.code
                    response.close()
                    error("Сервер ответил $code")
                }
                return response
            }
            fun text(url: String) = get(url, null).use { it.body?.string().orEmpty() }

            val masterUrl = info.hlsUrl!!
            val master = text(masterUrl)
            val lines = master.lines()

            // Варианты видео: #EXT-X-STREAM-INF, следующая строка — адрес. Берём лучший не выше 480p (H.264 предпочтительнее).
            class Variant(val url: String, val height: Int, val bandwidth: Long, val avc: Boolean, val audioGroup: String?)
            val variants = ArrayList<Variant>()
            for ((i, line) in lines.withIndex()) {
                if (!line.startsWith("#EXT-X-STREAM-INF:")) continue
                val uri = lines.drop(i + 1).firstOrNull { it.isNotBlank() && !it.startsWith("#") } ?: continue
                val attrs = parseAttrs(line.substringAfter(':'))
                val height = attrs["RESOLUTION"]?.substringAfter('x')?.toIntOrNull() ?: 0
                variants += Variant(
                    resolve(masterUrl, uri), height, attrs["BANDWIDTH"]?.toLongOrNull() ?: 0,
                    attrs["CODECS"]?.contains("avc1") == true, attrs["AUDIO"],
                )
            }
            val videoVariants = variants.filter { it.height > 0 }
            val pool = videoVariants.filter { it.avc }.ifEmpty { videoVariants }
            val chosen = pool.filter { it.height in 1..480 }.maxByOrNull { it.height * 1_000_000_000L + it.bandwidth }
                ?: pool.minByOrNull { it.height }
            if (chosen == null && !audioOnly) error("В списке потоков нет видео")

            // Звук: дорожка из #EXT-X-MEDIA нужной группы; если не нашли — любая звуковая; затем отдельные звуковые варианты.
            val audioMedia = lines.filter { it.startsWith("#EXT-X-MEDIA:") }
                .map { parseAttrs(it.substringAfter(':')) }
                .filter { it["TYPE"] == "AUDIO" && it["URI"] != null }
            fun pickAudio(list: List<Map<String, String>>) =
                (list.firstOrNull { it["DEFAULT"] == "YES" } ?: list.firstOrNull())?.get("URI")
            val audioUrl: String? = (
                pickAudio(audioMedia.filter { chosen?.audioGroup != null && it["GROUP-ID"] == chosen.audioGroup })
                    ?: pickAudio(audioMedia)
                )?.let { resolve(masterUrl, it) }
                ?: variants.filter { it.height == 0 }.maxByOrNull { it.bandwidth }?.url

            val cache = context.cacheDir
            val report = StringBuilder()
            if (audioOnly && audioUrl == null) error("В этом видео нет отдельной звуковой дорожки")
            val videoFile = File.createTempFile("dl-video", ".mp4", cache)
            val audioFile = if (audioUrl != null) File.createTempFile("dl-audio", ".mp4", cache) else null
            val muxedFile = File.createTempFile("dl-out", ".mp4", cache)
            try {
                fun describe(label: String, st: Stats) =
                    "$label: ${st.segments} сегм., ${st.bytes / 1024} КБ, по плейлисту ${st.seconds.toInt()} с" +
                        (if (st.byteRanges) ", byterange" else "") + "; "
                if (audioOnly) {
                    report.append(describe("звук", fetchPlaylist(::get, audioUrl!!, audioFile!!)))
                    report.append(mux(listOf(audioFile to "audio/"), muxedFile))
                } else {
                    report.append(describe("видео", fetchPlaylist(::get, chosen!!.url, videoFile)))
                    if (audioUrl != null && audioFile != null) {
                        report.append(describe("звук", fetchPlaylist(::get, audioUrl, audioFile)))
                        report.append(mux(listOf(videoFile to "video/", audioFile to "audio/"), muxedFile))
                    } else {
                        // Звук мог быть внутри видеопотока; если его там нет, не молчим.
                        report.append(mux(listOf(videoFile to "video/", videoFile to "audio/"), muxedFile))
                    }
                }
                val (out, finish) = openOutput(context, name, mime)
                var done = false
                try {
                    out.use { o -> muxedFile.inputStream().use { it.copyTo(o) } }
                    done = true
                } finally {
                    finish(done)
                }
            } finally {
                videoFile.delete(); audioFile?.delete(); muxedFile.delete()
            }
            name + "\n" + report
        }

    /** Итог скачивания одного плейлиста. */
    private class Stats(val segments: Int, val bytes: Long, val seconds: Double, val byteRanges: Boolean)

    /** Скачивает медиаплейлист, включая появляющиеся позже сегменты до #EXT-X-ENDLIST. */
    private fun fetchPlaylist(get: (String, String?) -> okhttp3.Response, playlistUrl: String, target: File): Stats {
        class Part(val url: String, val range: String?)
        val parts = ArrayList<Part>()
        val seen = HashSet<String>()
        var seconds = 0.0
        val lastEnd = HashMap<String, Long>()
        var ranges = false
        var finished = false
        var idlePolls = 0
        fun rangeOf(spec: String, url: String): String {
            val length = spec.substringBefore('@').toLong()
            val start = spec.substringAfter('@', "").toLongOrNull() ?: (lastEnd[url] ?: 0L)
            lastEnd[url] = start + length
            return "bytes=$start-${start + length - 1}"
        }
        while (!finished && idlePolls < 30) {
            val playlist = get(playlistUrl, null).use { it.body?.string().orEmpty() }
            var nextRange: String? = null
            var segmentSeconds = 0.0
            val before = parts.size
            for (line in playlist.lines()) {
                when {
                    line.startsWith("#EXT-X-ENDLIST") -> finished = true
                    line.startsWith("#EXT-X-MAP:") -> {
                        val attrs = parseAttrs(line.substringAfter(':'))
                        attrs["URI"]?.let { ref ->
                            val url = resolve(playlistUrl, ref)
                            val range = attrs["BYTERANGE"]?.let { ranges = true; rangeOf(it, url) }
                            if (seen.add("$url|$range")) parts += Part(url, range)
                        }
                    }
                    line.startsWith("#EXTINF:") ->
                        segmentSeconds = line.substringAfter(':').substringBefore(',').toDoubleOrNull() ?: 0.0
                    line.startsWith("#EXT-X-BYTERANGE:") -> nextRange = line.substringAfter(':').trim()
                    line.isNotBlank() && !line.startsWith("#") -> {
                        val url = resolve(playlistUrl, line.trim())
                        val range = nextRange?.let { ranges = true; rangeOf(it, url) }
                        if (seen.add("$url|$range")) {
                            parts += Part(url, range)
                            seconds += segmentSeconds
                        }
                        segmentSeconds = 0.0
                        nextRange = null
                    }
                }
            }
            if (parts.size == before && !finished) idlePolls++ else idlePolls = 0
            if (!finished) Thread.sleep(500)
        }
        if (parts.isEmpty()) error("Пустой список сегментов")
        var total = 0L
        target.outputStream().use { out ->
            for (part in parts) {
                val bytes = try {
                    get(part.url, part.range).use { it.body?.bytes() ?: ByteArray(0) }
                } catch (e: Exception) {
                    Thread.sleep(300)
                    get(part.url, part.range).use { it.body?.bytes() ?: ByteArray(0) }
                }
                out.write(bytes)
                total += bytes.size
            }
        }
        return Stats(parts.size, total, seconds, ranges)
    }

    /** Склеивает видео из одного файла и звук из другого в один mp4 (MediaExtractor + MediaMuxer). */
    private fun mux(sources: List<Pair<File, String>>, output: File): String {
        val report = StringBuilder()
        val muxer = MediaMuxer(output.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val extractors = ArrayList<MediaExtractor>()
        try {
            val mapping = ArrayList<Pair<MediaExtractor, Int>>() // extractor → индекс дорожки в muxer
            for ((file, prefix) in sources) {
                val ex = MediaExtractor().apply { setDataSource(file.path) }
                extractors += ex
                val track = (0 until ex.trackCount).firstOrNull {
                    ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith(prefix) == true
                } ?: error("В потоке нет дорожки $prefix")
                ex.selectTrack(track)
                mapping += ex to muxer.addTrack(ex.getTrackFormat(track))
            }
            muxer.start()
            val buffer = ByteBuffer.allocate(2 * 1024 * 1024)
            val info = MediaCodec.BufferInfo()
            val counts = IntArray(mapping.size)
            val lastTimes = LongArray(mapping.size)
            val active = BooleanArray(mapping.size) { true }
            while (active.any { it }) {
                var selected = -1
                var selectedTime = Long.MAX_VALUE
                for (i in mapping.indices) {
                    val time = mapping[i].first.sampleTime
                    if (time >= 0 && time < selectedTime) {
                        selected = i
                        selectedTime = time
                    } else if (time < 0) {
                        active[i] = false
                    }
                }
                if (selected < 0) break
                val (ex, outTrack) = mapping[selected]
                buffer.clear()
                val size = ex.readSampleData(buffer, 0)
                if (size < 0) {
                    active[selected] = false
                    continue
                }
                info.set(0, size, ex.sampleTime, ex.sampleFlags)
                muxer.writeSampleData(outTrack, buffer, info)
                counts[selected]++
                lastTimes[selected] = info.presentationTimeUs
                ex.advance()
            }
            mapping.forEachIndexed { index, (_, outTrack) ->
                report.append(" дорожка ").append(outTrack).append(": ")
                    .append(counts[index]).append(" кадров/блоков, ")
                    .append(lastTimes[index] / 1_000_000).append(" с;")
            }
            muxer.stop()
        } finally {
            extractors.forEach { it.release() }
            muxer.release()
        }
        return report.toString()
    }

    private fun parseAttrs(text: String): Map<String, String> {
        val result = HashMap<String, String>()
        Regex("([A-Z0-9-]+)=(\"[^\"]*\"|[^,]*)").findAll(text).forEach {
            result[it.groupValues[1]] = it.groupValues[2].trim('"')
        }
        return result
    }

    private fun resolve(base: String, ref: String): String =
        if (ref.startsWith("http")) ref else java.net.URI(base).resolve(ref).toString()

    private fun fileName(title: String, info: StreamInfo, ext: String = ".mp4") =
        title.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().take(80).ifBlank { info.videoId } + ext

    private suspend fun downloadDirect(context: Context, http: OkHttpClient, info: StreamInfo, title: String): String =
        withContext(Dispatchers.IO) {
            val url = info.muxedUrl ?: error("Для этого видео нет готового файла для скачивания")
            val name = title.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().take(80).ifBlank { info.videoId } + ".mp4"

            val (out, finish) = openOutput(context, name, "video/mp4")
            var done = false
            try {
                out.use { stream ->
                    var position = 0L
                    var total = Long.MAX_VALUE
                    while (position < total) {
                        val request = Request.Builder()
                            .url(url)
                            .header("User-Agent", info.userAgent)
                            .header("Range", "bytes=$position-${position + CHUNK - 1}")
                            .build()
                        http.newCall(request).execute().use { response ->
                            if (!response.isSuccessful) error("Сервер ответил ${response.code}")
                            response.header("Content-Range")?.substringAfter('/')?.toLongOrNull()?.let { total = it }
                            val body = response.body ?: error("Пустой ответ")
                            val bytes = body.bytes()
                            if (bytes.isEmpty()) total = position
                            stream.write(bytes)
                            position += bytes.size
                            if (total == Long.MAX_VALUE && bytes.size < CHUNK) total = position
                        }
                    }
                }
                done = true
            } finally {
                finish(done)
            }
            name
        }

    private fun openOutput(context: Context, name: String, mime: String): Pair<OutputStream, (Boolean) -> Unit> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("Не удалось создать файл в «Загрузках»")
            val stream = resolver.openOutputStream(uri) ?: error("Не удалось открыть файл")
            return stream to { ok ->
                if (ok) {
                    resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
                } else {
                    resolver.delete(uri, null, null)
                }
            }
        }
        // Android 8–9: без разрешения на запись во внешнюю память — в папку приложения.
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
        val file = File(dir, name)
        return file.outputStream() to { ok -> if (!ok) file.delete() }
    }
}
