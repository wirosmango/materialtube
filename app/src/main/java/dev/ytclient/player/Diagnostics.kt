package dev.ytclient.player

import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource

/**
 * Короткое описание неудавшегося HTTP-запроса к googlevideo для отладки обрывов 403:
 * какой диапазон байт, какой поток (itag), сколько осталось до `expire`, какие параметры есть в ссылке.
 * Подписи и IP не выводятся.
 */
@UnstableApi
fun describeHttpFailure(error: PlaybackException): String? {
    val e = error.cause as? HttpDataSource.InvalidResponseCodeException ?: return null
    val uri = e.dataSpec.uri
    val shown = listOf("itag", "c", "mime", "clen", "ratebypass", "rn", "cpn", "pot")
        .mapNotNull { k -> uri.getQueryParameter(k)?.let { "$k=${it.take(24)}" } }
    val present = listOf("n", "sig", "lsig").filter { uri.getQueryParameter(it) != null }
    val left = uri.getQueryParameter("expire")?.toLongOrNull()?.let { it - System.currentTimeMillis() / 1000 }
    return buildString {
        append("HTTP ${e.responseCode}, bytes ${e.dataSpec.position}+${e.dataSpec.length}")
        if (left != null) append(", до expire ${left} с")
        append("; ").append(shown.joinToString(" "))
        append("; есть: ").append(present.joinToString(","))
    }
}
