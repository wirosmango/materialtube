package dev.ytclient.innertube

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.MessageDigest

/**
 * Прямой клиент к внутреннему API YouTube (`/youtubei/v1/...`).
 *
 * - поиск, лента и «похожие» идут через клиент `WEB`; если выполнен вход, к запросам добавляются cookies;
 * - ссылки на потоки получаются через цепочку клиентов из каталога (без входа), пока один не вернёт прямые URL.
 */
class InnerTube(
    private val http: OkHttpClient,
    private val authStore: AuthStore? = null,
    private val catalog: ClientCatalog = ClientCatalog.load(),
    var hl: String = "ru",
    var gl: String = "RU",
) {
    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var visitorData: String? = null
    private val visitorLock = Mutex()

    /**
     * Без visitorData клиент VISIONOS (HLS без токена) может отказать, а он первым в цепочке. Самый первый запрос
     * после запуска приходил без него (его выдаёт ответ на любой запрос), поэтому первое видео шло запасным клиентом
     * без выбора качества. Получаем visitorData заранее, один раз.
     */
    private suspend fun ensureVisitor() {
        if (visitorData != null) return
        visitorLock.withLock {
            if (visitorData != null) return
            try {
                post("visitor_id", webProfile()) {}
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Не вышло — продолжаем без него, как раньше.
            }
        }
    }

    /** Последний сырой ответ ленты: нужен, чтобы при неудачном разборе можно было посмотреть структуру. */
    @Volatile
    var lastFeedJson: String? = null
        private set

    private fun webProfile() = catalog.client(catalog.searchClient)

    // ---------- Поиск, лента, «похожие» ----------

    suspend fun search(query: String): SearchPage {
        val response = post("search", webProfile()) { put("query", query) }
        return FeedParser.parse(response.at("contents"))
    }

    suspend fun searchMore(continuation: String): SearchPage {
        val response = post("search", webProfile()) { put("continuation", continuation) }
        return FeedParser.parse(response.at("onResponseReceivedCommands"))
    }

    /**
     * Лента `browse`: главная (FEwhat_to_watch), подписки (FEsubscriptions), история (FEhistory).
     * Без входа главная, скорее всего, пустая, а подписки и история недоступны.
     */
    suspend fun feed(browseId: String, continuation: String? = null): SearchPage {
        val response = post("browse", webProfile()) {
            if (continuation == null) put("browseId", browseId) else put("continuation", continuation)
        }
        lastFeedJson = response.toString()
        return FeedParser.parse(
            response.at(if (continuation == null) "contents" else "onResponseReceivedActions"),
        )
    }

    /** Страница ролика: «похожие», реакция пользователя и токен комментариев (с cookies входа — персональные). */
    suspend fun watchNext(videoId: String): WatchNext {
        val response = post("next", webProfile()) { put("videoId", videoId) }
        val related = FeedParser.parse(
            response.at("contents", "twoColumnWatchNextResults", "secondaryResults"),
        ).items
        val status = if (authStore?.current?.loggedIn == true) FeedParser.likeStatus(response) else null
        val loggedIn = authStore?.current?.loggedIn == true
        val (channel, subscribed) = FeedParser.owner(response)
        return WatchNext(related, status, FeedParser.commentsToken(response), channel, if (loggedIn) subscribed else null)
    }

    /** Профиль вошедшего аккаунта (имя, @handle, аватарка); null, если нет входа или ответ не разобрался. */
    suspend fun account(): AccountInfo? {
        if (authStore?.current?.loggedIn != true) return null
        val log = StringBuilder()
        for (endpoint in listOf("account/account_menu", "account/accounts_list")) {
            try {
                val response = post(endpoint, webProfile()) {
                    if (endpoint == "account/accounts_list") {
                        put("accountReadMask", buildJsonObject { put("returnOwner", true) })
                    }
                }
                log.append("=== ").append(endpoint).append(" ===\n").append(response.toString()).append('\n')
                FeedParser.account(response)?.let { return it }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.append("=== ").append(endpoint).append(" ошибка: ").append(e.message).append('\n')
            }
        }
        lastFeedJson = log.toString()
        return null
    }

    /** Каналы, на которые подписан пользователь (нужен вход); пустой список, если разобрать не удалось. */
    suspend fun subscriptionChannels(): List<Channel> {
        if (authStore?.current?.loggedIn != true) return emptyList()
        val log = StringBuilder()
        for (endpoint in listOf("browse", "guide")) {
            try {
                val response = post(endpoint, webProfile()) {
                    if (endpoint == "browse") put("browseId", "FEchannels")
                }
                log.append("=== ").append(endpoint).append(" ===\n").append(response.toString()).append('\n')
                val channels = FeedParser.channels(response)
                if (channels.isNotEmpty()) {
                    lastFeedJson = log.toString()
                    return channels
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.append("=== ").append(endpoint).append(" ошибка: ").append(e.message).append('\n')
            }
        }
        lastFeedJson = log.toString()
        return emptyList()
    }

    /** Страница канала: вкладка «Видео» и состояние подписки. Продолжение грузится через [feed]. */
    suspend fun channel(channelId: String): ChannelPage {
        val response = post("browse", webProfile()) {
            put("browseId", channelId)
            put("params", "EgZ2aWRlb3PyBgQKAjoA") // вкладка «Видео»
        }
        lastFeedJson = response.toString()
        val header = FeedParser.channelHeader(response, channelId)
        val info = if (authStore?.current?.loggedIn == true) header else header.copy(subscribed = null)
        return ChannelPage(info, FeedParser.parse(response.at("contents")))
    }

    /** Подписаться или отписаться. Нужен вход в аккаунт. */
    suspend fun setSubscribed(channelId: String, subscribe: Boolean) {
        if (authStore?.current?.loggedIn != true) throw InnerTubeException("Нужен вход в аккаунт")
        post(if (subscribe) "subscription/subscribe" else "subscription/unsubscribe", webProfile()) {
            put("channelIds", JsonArray(listOf(JsonPrimitive(channelId))))
        }
    }

    /** Лайк, дизлайк или снятие реакции. Нужен вход в аккаунт. */
    suspend fun react(videoId: String, status: LikeStatus) {
        if (authStore?.current?.loggedIn != true) throw InnerTubeException("Нужен вход в аккаунт")
        val endpoint = when (status) {
            LikeStatus.Like -> "like/like"
            LikeStatus.Dislike -> "like/dislike"
            LikeStatus.None -> "like/removelike"
        }
        post(endpoint, webProfile()) {
            put("target", buildJsonObject { put("videoId", videoId) })
        }
    }

    /** Страница комментариев по токену из [watchNext] или из предыдущей страницы. */
    suspend fun comments(token: String): CommentsPage {
        val response = post("next", webProfile()) { put("continuation", token) }
        lastFeedJson = response.toString()
        return CommentParser.parse(response)
    }

    /** Создаёт комментарий. Нужен токен из `commentSimpleboxRenderer` и вход в аккаунт. */
    suspend fun createComment(params: String, text: String) {
        if (authStore?.current?.loggedIn != true) throw InnerTubeException("Нужен вход в аккаунт")
        if (text.isBlank()) throw InnerTubeException("Комментарий не может быть пустым")
        post("comment/create_comment", webProfile()) {
            put("createCommentParams", params)
            put("commentText", text)
        }
    }

    /** Ставит или снимает лайк с комментария. Нужен вход в аккаунт. */
    suspend fun reactComment(commentId: String, liked: Boolean) {
        if (authStore?.current?.loggedIn != true) throw InnerTubeException("Нужен вход в аккаунт")
        post(if (liked) "comment/like" else "comment/unlike", webProfile()) {
            put("target", buildJsonObject { put("commentId", commentId) })
        }
    }

    /**
     * Идентификаторы Shorts для вертикальной ленты. Формат ответов проверить не удалось:
     * пробуем вкладку Shorts, затем полки Shorts на главной. Сырые ответы кладутся в lastFeedJson.
     */
    suspend fun shorts(): List<String> {
        val log = StringBuilder()
        for (browseId in listOf("FEshorts", "FEwhat_to_watch")) {
            try {
                val response = post("browse", webProfile()) { put("browseId", browseId) }
                log.append("=== ").append(browseId).append(" ===\n").append(response.toString()).append('\n')
                val ids = FeedParser.reelIds(response)
                if (ids.isNotEmpty()) {
                    lastFeedJson = log.toString()
                    return ids
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.append("=== ").append(browseId).append(" ошибка: ").append(e.message).append('\n')
            }
        }
        lastFeedJson = log.toString()
        return emptyList()
    }

    // ---------- Воспроизведение ----------

    /** Поток для скачивания: готовый muxed-файл от ANDROID_VR, либо HLS как fallback. */
    suspend fun downloadStream(videoId: String): StreamInfo {
        for (key in catalog.playerChain.filter { it.startsWith("ANDROID_VR") }) {
            val profile = catalog.clients[key] ?: continue
            try {
                val response = player(videoId, profile)
                if (response.at("playabilityStatus", "status").string() != "OK") continue
                val info = StreamParser.parse(response, key, profile, DEFAULT_HEIGHT) ?: continue
                if (info.muxedUrl != null) return info
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // пробуем следующий клиент
            }
        }
        throw InnerTubeException("Не удалось получить файл для скачивания")
    }

    /** Один запрос `player` конкретным клиентом (сырой ответ). */
    suspend fun player(videoId: String, profile: ClientProfile): JsonObject =
        post("player", profile) {
            put("videoId", videoId)
            put("contentCheckOk", true)
            put("racyCheckOk", true)
            put(
                "playbackContext",
                buildJsonObject {
                    put(
                        "contentPlaybackContext",
                        buildJsonObject { put("html5Preference", "HTML5_PREF_WANTS") },
                    )
                },
            )
        }

    /**
     * Перебирает клиентов из `playerChain`, пока один не вернёт playabilityStatus=OK
     * и хотя бы один прямой URL. Если не получилось — исключение со всеми причинами.
     */
    suspend fun resolveStream(
        videoId: String,
        maxHeight: Int = DEFAULT_HEIGHT,
        forDownload: Boolean = false,
    ): StreamInfo {
        val failures = mutableListOf<String>()
        // Для скачивания сначала пробуем прямые потоки Android-клиентов; HLS VISIONOS оставляем fallback.
        val orderedKeys = if (forDownload) {
            catalog.playerChain.sortedBy { if (it == "VISIONOS") 1 else 0 }
        } else {
            catalog.playerChain
        }
        val keys = orderedKeys
            .flatMap { if (it == "VISIONOS") listOf(it, it) else listOf(it) }
        for (key in keys) {
            val profile = catalog.clients[key] ?: continue
            try {
                val response = player(videoId, profile)
                val status = response.at("playabilityStatus", "status").string()
                if (status != "OK") {
                    val reason = response.at("playabilityStatus", "reason").string().orEmpty()
                    failures += "$key: $status $reason".trim()
                    continue
                }
                val info = StreamParser.parse(response, key, profile, maxHeight)
                if (info == null) {
                    failures += "$key: нет прямых ссылок"
                    continue
                }
                if (forDownload && !info.isHls && info.muxedUrl == null &&
                    (info.videoUrl == null || info.audioUrl == null)
                ) {
                    failures += "$key: нет полного набора дорожек для файла"
                    continue
                }
                // Ссылка может быть выдана, но googlevideo ответит 403: проверяем до запуска плеера.
                val problem = listOfNotNull(info.videoUrl ?: info.muxedUrl, info.audioUrl)
                    .firstNotNullOfOrNull { probe(it, profile.userAgent) }
                if (problem != null) {
                    failures += "$key: видеопоток отвечает $problem"
                    continue
                }
                return info.copy(notes = failures.joinToString("; "))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failures += "$key: ${e.message}"
            }
        }
        throw InnerTubeException("Не удалось получить поток:\n" + failures.joinToString("\n"))
    }

    /**
     * Пробный запрос к потоку теми же ограниченными кусками, какими потом читает ChunkedDataSource
     * (открытый диапазон googlevideo режет 403). null — всё хорошо, иначе код ответа.
     */
    private suspend fun probe(url: String, userAgent: String): String? = withContext(Dispatchers.IO) {
        val code = try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .header("Range", "bytes=0-1048575")
                .build()
            http.newCall(request).execute().use { it.code }
        } catch (e: Exception) {
            -1
        }
        if (code in 200..299) null else "HTTP $code"
    }

    // ---------- Транспорт ----------

    private suspend fun post(
        endpoint: String,
        profile: ClientProfile,
        build: JsonObjectBuilder.() -> Unit,
    ): JsonObject {
        if (endpoint != "visitor_id") ensureVisitor()
        return postRaw(endpoint, profile, build)
    }

    private suspend fun postRaw(
        endpoint: String,
        profile: ClientProfile,
        build: JsonObjectBuilder.() -> Unit,
    ): JsonObject = withContext(Dispatchers.IO) {
        // Cookies добавляем только клиентам, которые поддерживают вход (WEB); VR/IOS идут без аккаунта.
        val auth = authStore?.current?.takeIf { it.loggedIn && profile.supportsCookieAuthentication }
        val visitor = auth?.visitorData ?: visitorData

        val body = buildJsonObject {
            put(
                "context",
                buildJsonObject {
                    put("client", clientContext(profile, visitor))
                    auth?.onBehalfOfUser?.let { id ->
                        put("user", buildJsonObject { put("onBehalfOfUser", id) })
                    }
                },
            )
            build()
        }

        val request = Request.Builder()
            .url("https://www.youtube.com/youtubei/v1/$endpoint?prettyPrint=false")
            .header("User-Agent", profile.userAgent)
            .header("X-YouTube-Client-Name", profile.clientId)
            .header("X-YouTube-Client-Version", profile.clientVersion)
            .apply {
                if (profile.isWeb) {
                    header("Origin", ORIGIN)
                    header("Referer", "$ORIGIN/")
                }
                if (auth != null) {
                    header("Cookie", auth.cookie.orEmpty())
                    auth.cookieValue("SAPISID")?.let { header("Authorization", sapisidHash(it)) }
                    header("X-Goog-AuthUser", "0")
                    header("X-Origin", ORIGIN)
                }
                visitor?.let { header("X-Goog-Visitor-Id", it) }
            }
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw InnerTubeException("HTTP ${response.code} на /$endpoint: ${text.take(200)}")
            }
            val parsed = json.parseToJsonElement(text).jsonObject
            if (visitorData == null) {
                visitorData = parsed.at("responseContext", "visitorData").string()
            }
            parsed
        }
    }

    private fun clientContext(profile: ClientProfile, visitor: String?): JsonObject = buildJsonObject {
        put("clientName", profile.clientName)
        put("clientVersion", profile.clientVersion)
        put("hl", hl)
        put("gl", gl)
        profile.osName?.let { put("osName", it) }
        profile.osVersion?.let { put("osVersion", it) }
        profile.deviceMake?.let { put("deviceMake", it) }
        profile.deviceModel?.let { put("deviceModel", it) }
        profile.androidSdkVersion?.let { put("androidSdkVersion", it) }
        visitor?.let { put("visitorData", it) }
    }

    /** `Authorization: SAPISIDHASH <время>_<sha1("<время> <SAPISID> <origin>")>` */
    private fun sapisidHash(sapisid: String): String {
        val timestamp = System.currentTimeMillis() / 1000
        val digest = MessageDigest.getInstance("SHA-1")
            .digest("$timestamp $sapisid $ORIGIN".toByteArray())
        return "SAPISIDHASH ${timestamp}_" + digest.joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val ORIGIN = "https://www.youtube.com"

        /** Качество по умолчанию, как в официальном клиенте. */
        const val DEFAULT_HEIGHT = 480
    }
}
