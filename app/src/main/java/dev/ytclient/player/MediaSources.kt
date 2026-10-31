package dev.ytclient.player

import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import dev.ytclient.innertube.StreamInfo
import okhttp3.Call

@OptIn(UnstableApi::class)
object MediaSources {

    /**
     * live → HLS; иначе раздельные video+audio (склейка) → готовый muxed → HLS.
     * User-Agent должен совпадать с клиентом, у которого получены ссылки, иначе возможен 403.
     * Прогрессивные потоки читаются кусками (ChunkedDataSource): открытый Range googlevideo не принимает.
     */
    fun create(info: StreamInfo, callFactory: Call.Factory): MediaSource {
        val httpFactory = OkHttpDataSource.Factory(callFactory)
            .setUserAgent(info.userAgent)
        val chunkedFactory = DataSource.Factory { ChunkedDataSource(httpFactory.createDataSource()) }

        fun progressive(url: String): MediaSource =
            ProgressiveMediaSource.Factory(chunkedFactory)
                .createMediaSource(MediaItem.fromUri(url))

        fun hls(url: String): MediaSource =
            HlsMediaSource.Factory(httpFactory)
                .createMediaSource(MediaItem.fromUri(url))

        val hlsUrl = info.hlsUrl
        val videoUrl = info.videoUrl
        val audioUrl = info.audioUrl
        val muxedUrl = info.muxedUrl

        return when {
            info.isLive && hlsUrl != null -> hls(hlsUrl)
            videoUrl != null && audioUrl != null ->
                MergingMediaSource(progressive(videoUrl), progressive(audioUrl))
            muxedUrl != null -> progressive(muxedUrl)
            hlsUrl != null -> hls(hlsUrl)
            else -> error("Нет воспроизводимого потока")
        }
    }
}
