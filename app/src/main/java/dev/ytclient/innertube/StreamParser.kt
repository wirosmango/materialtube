package dev.ytclient.innertube

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** Разбирает `streamingData` из ответа `player`. Форматы без прямого `url` (с шифрованной подписью) пропускаются. */
internal object StreamParser {

    private data class Format(
        val url: String,
        val mime: String,
        val bitrate: Int,
        val height: Int,
    )

    fun parse(
        response: JsonObject,
        clientKey: String,
        profile: ClientProfile,
        maxHeight: Int,
    ): StreamInfo? {
        val streaming = response.at("streamingData") ?: return null
        val details = response.at("videoDetails")

        val hls = streaming.at("hlsManifestUrl").string()
        val muxed = streaming.at("formats").array().mapNotNull { format(it) }
        val adaptive = streaming.at("adaptiveFormats").array().mapNotNull { format(it) }

        val videos = adaptive.filter { it.mime.startsWith("video/mp4") && it.height > 0 }
        val byQuality = compareBy<Format>({ it.height }, { it.bitrate })
        // Лучшее качество не выше запрошенного; если все выше — берём самое низкое из доступных.
        val video = videos.filter { it.height <= maxHeight }.maxWithOrNull(byQuality)
            ?: videos.minWithOrNull(byQuality)
        val audio = adaptive
            .filter { it.mime.startsWith("audio/mp4") }
            .maxByOrNull { it.bitrate }
        val bestMuxed = muxed.maxByOrNull { it.height }

        // ANDROID_VR без PO-токена отдаёт раздельные потоки только на первую минуту (потом 403);
        // готовый поток «картинка+звук» (360p) работает целиком, поэтому для него берём его.
        // VISIONOS отдаёт HLS без токена: из него плеер сам выберет качество.
        val clientName = profile.clientName
        val hlsOnly = clientName == "VISIONOS" && hls != null
        val preferMuxed = clientName.startsWith("ANDROID_VR") && bestMuxed != null
        val hasPair = video != null && audio != null && !hlsOnly && !preferMuxed
        if (!hasPair && bestMuxed == null && hls == null) return null

        return StreamInfo(
            videoId = details.at("videoId").string().orEmpty(),
            title = details.at("title").string().orEmpty(),
            author = details.at("author").string(),
            durationSeconds = details.at("lengthSeconds").long(),
            isLive = details.at("isLive").string() == "true" ||
                details.at("isLiveContent").string() == "true" && hls != null && !hasPair,
            hlsUrl = hls,
            muxedUrl = if (hlsOnly) null else bestMuxed?.url,
            videoUrl = if (hasPair) video?.url else null,
            audioUrl = if (hasPair) audio?.url else null,
            videoHeight = if (hasPair) video?.height else bestMuxed?.height,
            userAgent = profile.userAgent,
            clientKey = clientKey,
            availableHeights = if (hasPair) videos.map { it.height }.distinct().sortedDescending() else emptyList(),
        )
    }

    private fun format(element: JsonElement): Format? {
        val url = element.at("url").string() ?: return null
        return Format(
            url = url,
            mime = element.at("mimeType").string().orEmpty(),
            bitrate = element.at("bitrate").int() ?: 0,
            height = element.at("height").int() ?: 0,
        )
    }
}
