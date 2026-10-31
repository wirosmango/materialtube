package dev.ytclient.innertube

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Универсальный разбор лент (поиск, главная, «похожие»): обходит JSON целиком и собирает все видео,
 * не завися от точной вложенности. Понимает старые renderer'ы и новый `lockupViewModel`.
 * Shorts и плейлисты пропускаются. Токен продолжения берётся из последнего `continuationItemRenderer`.
 */
internal object FeedParser {

    private val rendererKeys = setOf("videoRenderer", "compactVideoRenderer", "gridVideoRenderer")

    fun parse(root: JsonElement?): SearchPage {
        val videos = ArrayList<VideoItem>()
        var token: String? = null

        fun walk(element: JsonElement?) {
            when (element) {
                is JsonObject -> for ((key, value) in element) {
                    when {
                        key in rendererKeys -> renderer(value)?.let { videos += it }
                        key == "lockupViewModel" -> lockup(value)?.let { videos += it }
                        key == "continuationItemRenderer" -> findToken(value)?.let { token = it }
                        else -> walk(value)
                    }
                }
                is JsonArray -> element.forEach { walk(it) }
                else -> Unit
            }
        }

        walk(root)
        return SearchPage(videos.distinctBy { it.id }, token)
    }

    /**
     * Состояние лайка текущего ролика: первое поле `likeStatus` в ответе `next`
     * (LIKE → true, INDIFFERENT/DISLIKE → false, не нашли → null).
     */
    fun likeStatus(root: JsonElement?): LikeStatus? {
        var result: LikeStatus? = null

        fun walk(element: JsonElement?) {
            if (result != null) return
            when (element) {
                is JsonObject -> for ((key, value) in element) {
                    if (key == "likeStatus") {
                        when (value.string()) {
                            "LIKE" -> result = LikeStatus.Like
                            "DISLIKE" -> result = LikeStatus.Dislike
                            "INDIFFERENT" -> result = LikeStatus.None
                        }
                    }
                    if (result != null) return
                    walk(value)
                }
                is JsonArray -> element.forEach { walk(it) }
                else -> Unit
            }
        }

        walk(root)
        return result
    }

    /**
     * Токен первой страницы комментариев из ответа `next`: continuationItemRenderer внутри секции
     * `comment-item-section` или панели `engagement-panel-comments-section`.
     */
    fun commentsToken(root: JsonElement?): String? {
        var result: String? = null

        fun walk(element: JsonElement?) {
            if (result != null) return
            when (element) {
                is JsonObject -> {
                    if (element["sectionIdentifier"].string() == "comment-item-section" ||
                        element["targetId"].string() == "engagement-panel-comments-section" ||
                        element["panelIdentifier"].string() == "engagement-panel-comments-section"
                    ) {
                        findToken(element)?.let { result = it; return }
                    }
                    for (value in element.values) walk(value)
                }
                is JsonArray -> element.forEach { walk(it) }
                else -> Unit
            }
        }

        walk(root)
        return result
    }

    /** Профиль аккаунта из ответа account_menu (`activeAccountHeaderRenderer`) или accounts_list (`accountItem`). */
    fun account(root: JsonElement?): AccountInfo? {
        var result: AccountInfo? = null

        fun walk(element: JsonElement?) {
            if (result != null) return
            when (element) {
                is JsonObject -> for ((key, value) in element) {
                    if (key == "activeAccountHeaderRenderer" || key == "accountItem") {
                        val name = value.at("accountName").text()
                        if (name != null) {
                            result = AccountInfo(
                                name = name,
                                handle = value.at("channelHandle").text() ?: value.at("accountByline").text(),
                                avatar = value.at("accountPhoto", "thumbnails").array().lastOrNull()
                                    .at("url").string().https(),
                            )
                            return
                        }
                    }
                    walk(value)
                    if (result != null) return
                }
                is JsonArray -> for (item in element) {
                    walk(item)
                    if (result != null) return
                }
                else -> Unit
            }
        }

        walk(root)
        return result
    }

    /** Идентификаторы Shorts: любой `reelWatchEndpoint` в ответе (полки, карточки, переходы), по порядку. */
    fun reelIds(root: JsonElement?): List<String> {
        val ids = LinkedHashSet<String>()

        fun walk(element: JsonElement?) {
            when (element) {
                is JsonObject -> for ((key, value) in element) {
                    if (key == "reelWatchEndpoint") value.at("videoId").string()?.let { ids += it }
                    walk(value)
                }
                is JsonArray -> element.forEach { walk(it) }
                else -> Unit
            }
        }

        walk(root)
        return ids.toList()
    }

    /** Первый `browseId`, похожий на идентификатор канала (UC…), внутри элемента. */
    internal fun findChannelId(e: JsonElement?): String? = when (e) {
        is JsonObject -> e["browseId"].string()?.takeIf { it.startsWith("UC") }
            ?: e.values.firstNotNullOfOrNull { findChannelId(it) }
        is JsonArray -> e.firstNotNullOfOrNull { findChannelId(it) }
        else -> null
    }

    /** Первое булево поле с одним из [keys] (например `subscribed`) внутри элемента. */
    internal fun findBoolean(e: JsonElement?, vararg keys: String): Boolean? = when (e) {
        is JsonObject -> {
            var found: Boolean? = null
            for ((k, v) in e) {
                if (k in keys) found = v.string()?.toBooleanStrictOrNull()
                if (found == null) found = findBoolean(v, *keys)
                if (found != null) break
            }
            found
        }
        is JsonArray -> e.firstNotNullOfOrNull { findBoolean(it, *keys) }
        else -> null
    }

    /** Каналы из ответа `browse` (FEchannels): старые renderer'ы и новый `lockupViewModel` с типом CHANNEL. */
    fun channels(root: JsonElement?): List<Channel> {
        val result = ArrayList<Channel>()

        fun walk(element: JsonElement?) {
            when (element) {
                is JsonObject -> for ((key, value) in element) {
                    when (key) {
                        "channelRenderer", "gridChannelRenderer" -> {
                            val id = value.at("channelId").string()
                            val name = value.at("title").text()
                            if (id != null && name != null) {
                                result += Channel(
                                    id, name,
                                    value.at("thumbnail", "thumbnails").array().lastOrNull().at("url").string().https(),
                                )
                            }
                        }
                        "guideEntryRenderer" -> {
                            // Боковое меню (guide): подписки лежат записями с browseId канала.
                            val id = value.at("navigationEndpoint", "browseEndpoint", "browseId").string()
                            val name = value.at("formattedTitle").text() ?: value.at("title").text()
                            if (id != null && id.startsWith("UC") && name != null) {
                                result += Channel(
                                    id, name,
                                    value.at("thumbnail", "thumbnails", 0, "url").string().https(),
                                )
                            }
                        }
                        "lockupViewModel" -> {
                            if (value.at("contentType").string() == "LOCKUP_CONTENT_TYPE_CHANNEL") {
                                val id = value.at("contentId").string()
                                val name = value.at("metadata", "lockupMetadataViewModel", "title", "content").string()
                                if (id != null && name != null) {
                                    result += Channel(
                                        id, name,
                                        value.at(
                                            "contentImage", "decoratedAvatarViewModel", "avatar", "avatarViewModel",
                                            "image", "sources", 0, "url",
                                        ).string().https(),
                                    )
                                }
                            } else {
                                walk(value)
                            }
                        }
                        else -> walk(value)
                    }
                }
                is JsonArray -> element.forEach { walk(it) }
                else -> Unit
            }
        }

        walk(root)
        return result.distinctBy { it.id }
    }

    /** Владелец ролика в ответе `next`: канал и состояние подписки (по кнопке внутри videoOwnerRenderer). */
    fun owner(root: JsonElement?): Pair<Channel?, Boolean?> {
        var owner: JsonElement? = null

        fun walk(element: JsonElement?) {
            if (owner != null) return
            when (element) {
                is JsonObject -> for ((key, value) in element) {
                    if (key == "videoOwnerRenderer") { owner = value; return }
                    walk(value)
                    if (owner != null) return
                }
                is JsonArray -> element.forEach { walk(it); if (owner != null) return }
                else -> Unit
            }
        }

        walk(root)
        val o = owner ?: return null to null
        val id = findChannelId(o) ?: return null to null
        val channel = Channel(
            id = id,
            name = o.at("title").text().orEmpty(),
            avatar = o.at("thumbnail", "thumbnails").array().lastOrNull().at("url").string().https(),
        )
        return channel to findBoolean(o, "subscribed", "isSubscribed")
    }

    /** Шапка канала из ответа `browse`: имя и аватар из channelMetadataRenderer, подписчики и подписка — поиском. */
    fun channelHeader(root: JsonElement?, channelId: String): ChannelInfo {
        val meta = root.at("metadata", "channelMetadataRenderer")
        var subscribers: String? = null

        fun walk(element: JsonElement?) {
            if (subscribers != null) return
            when (element) {
                is JsonObject -> for ((key, value) in element) {
                    if (key == "subscriberCountText") { value.text()?.let { subscribers = it; return } }
                    if (key == "content" || key == "simpleText") {
                        val t = value.string()
                        if (t != null && (t.contains("подписчик") || t.contains("ubscriber"))) { subscribers = t; return }
                    }
                    walk(value)
                    if (subscribers != null) return
                }
                is JsonArray -> element.forEach { walk(it); if (subscribers != null) return }
                else -> Unit
            }
        }

        walk(root.at("header"))
        return ChannelInfo(
            id = channelId,
            name = meta.at("title").string().orEmpty(),
            avatar = meta.at("avatar", "thumbnails").array().firstOrNull().at("url").string().https(),
            subscribers = subscribers,
            subscribed = findBoolean(root.at("header"), "subscribed", "isSubscribed"),
        )
    }

    private fun renderer(r: JsonElement): VideoItem? {
        val id = r.at("videoId").string() ?: return null
        val title = r.at("title").text() ?: return null
        return VideoItem(
            id = id,
            title = title,
            channel = r.at("ownerText").text()
                ?: r.at("longBylineText").text()
                ?: r.at("shortBylineText").text(),
            duration = r.at("lengthText").text(),
            views = r.at("viewCountText").text() ?: r.at("shortViewCountText").text(),
            published = r.at("publishedTimeText").text(),
            channelId = findChannelId(r.at("ownerText")) ?: findChannelId(r.at("longBylineText"))
                ?: findChannelId(r.at("shortBylineText")),
            channelAvatar = (
                r.at(
                    "channelThumbnailSupportedRenderers", "channelThumbnailWithLinkRenderer",
                    "thumbnail", "thumbnails", 0, "url",
                ).string() ?: r.at("channelThumbnail", "thumbnails", 0, "url").string()
                ).https(),
        )
    }

    private fun lockup(l: JsonElement): VideoItem? {
        if (l.at("contentType").string() != "LOCKUP_CONTENT_TYPE_VIDEO") return null
        val id = l.at("contentId").string() ?: return null
        val meta = l.at("metadata", "lockupMetadataViewModel")
        val title = meta.at("title", "content").string() ?: return null

        // Строки метаданных: канал, затем «просмотры · давность».
        val parts = meta.at("metadata", "contentMetadataViewModel", "metadataRows").array()
            .flatMap { it.at("metadataParts").array() }
            .mapNotNull { it.at("text", "content").string() }

        val duration = l.at("contentImage", "thumbnailViewModel", "overlays").array()
            .firstNotNullOfOrNull { overlay ->
                overlay.at("thumbnailBottomOverlayViewModel", "badges").array()
                    .firstNotNullOfOrNull { it.at("thumbnailBadgeViewModel", "text").string() }
            }

        return VideoItem(
            id = id,
            title = title,
            channel = parts.getOrNull(0),
            duration = duration,
            views = parts.getOrNull(1),
            published = parts.getOrNull(2),
            channelId = findChannelId(meta),
            channelAvatar = meta.at(
                "image", "decoratedAvatarViewModel", "avatar", "avatarViewModel", "image", "sources", 0, "url",
            ).string().https(),
        )
    }

    /** Ссылки на аватарки иногда приходят в виде `//yt3.ggpht.com/...`. */
    private fun String?.https(): String? = this?.let { if (it.startsWith("//")) "https:$it" else it }

    internal fun findToken(e: JsonElement?): String? = when (e) {
        is JsonObject -> e["continuationCommand"].at("token").string()
            ?: e.values.firstNotNullOfOrNull { findToken(it) }
        is JsonArray -> e.firstNotNullOfOrNull { findToken(it) }
        else -> null
    }
}
