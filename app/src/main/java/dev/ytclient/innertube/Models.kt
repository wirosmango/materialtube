package dev.ytclient.innertube

data class VideoItem(
    val id: String,
    val title: String,
    val channel: String?,
    val duration: String?,
    val views: String?,
    val published: String?,
    val channelAvatar: String? = null,
    val channelId: String? = null,
) {
    val thumbnail: String get() = "https://i.ytimg.com/vi/$id/mqdefault.jpg"
}

data class SearchPage(
    val items: List<VideoItem>,
    val continuation: String?,
)

/** Профиль вошедшего аккаунта: имя, @handle канала и аватарка. */
data class AccountInfo(
    val name: String,
    val handle: String?,
    val avatar: String?,
)

enum class LikeStatus { Like, Dislike, None }

/**
 * Ответ `next` для ролика: «похожие», реакция пользователя (null — не удалось определить)
 * и токен, по которому грузятся комментарии (null — комментарии недоступны).
 */
data class WatchNext(
    val related: List<VideoItem>,
    val status: LikeStatus?,
    val commentsToken: String?,
    /** Канал, который загрузил ролик, и подписан ли на него пользователь (null — неизвестно). */
    val channel: Channel? = null,
    val subscribed: Boolean? = null,
)

data class Channel(
    val id: String,
    val name: String,
    val avatar: String?,
)

data class ChannelInfo(
    val id: String,
    val name: String,
    val avatar: String?,
    val subscribers: String?,
    val subscribed: Boolean?,
)

data class ChannelPage(
    val info: ChannelInfo,
    val videos: SearchPage,
)

data class Comment(
    val id: String,
    val author: String,
    val avatar: String?,
    val text: String,
    val published: String?,
    val likes: String?,
    val liked: Boolean? = null,
)

data class CommentsPage(
    val items: List<Comment>,
    val continuation: String?,
    val createCommentParams: String? = null,
)

/**
 * Что нашлось в ответе `player`. Плеер выбирает источник так:
 * live → HLS; иначе video+audio (склейка) → muxed → HLS.
 */
data class StreamInfo(
    val videoId: String,
    val title: String,
    val author: String?,
    val durationSeconds: Long?,
    val isLive: Boolean,
    val hlsUrl: String?,
    val muxedUrl: String?,
    val videoUrl: String?,
    val audioUrl: String?,
    val videoHeight: Int?,
    /** User-Agent клиента, с которым получены ссылки; плеер должен слать такой же. */
    val userAgent: String,
    val clientKey: String,
    /** Высоты (p) доступных раздельных mp4-потоков, по убыванию: для выбора качества. */
    val availableHeights: List<Int> = emptyList(),
    /** Почему не сработали клиенты, стоявшие в цепочке раньше (для отладки); пусто, если сработал первый. */
    val notes: String = "",
) {
    /** Единственный источник — HLS: качеством управляет сам плеер (дорожки), а не выбор ссылки. */
    val isHls: Boolean get() = videoUrl == null && muxedUrl == null && hlsUrl != null
}

class InnerTubeException(message: String, cause: Throwable? = null) : Exception(message, cause)
