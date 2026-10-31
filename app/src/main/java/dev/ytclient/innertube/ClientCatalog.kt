package dev.ytclient.innertube

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ClientProfile(
    val clientName: String,
    val clientVersion: String,
    val clientId: String,
    val userAgent: String,
    val osName: String? = null,
    val osVersion: String? = null,
    val deviceMake: String? = null,
    val deviceModel: String? = null,
    val androidSdkVersion: String? = null,
    /** Клиент принимает cookies входа (SAPISIDHASH). Из мобильных клиентов в каталоге такого нет. */
    val supportsCookieAuthentication: Boolean = false,
) {
    /** Веб-клиентам нужны Origin/Referer, мобильным — нет. */
    val isWeb: Boolean get() = clientName == "WEB" || clientName == "MWEB"
}

/**
 * Каталог клиентов лежит в `resources/innertube/youtube_clients.json`, а не в коде:
 * когда YouTube сломает очередной клиент, достаточно поправить версию или порядок цепочки.
 */
@Serializable
data class ClientCatalog(
    val searchClient: String,
    val playerChain: List<String>,
    val clients: Map<String, ClientProfile>,
) {
    fun client(key: String): ClientProfile =
        clients[key] ?: error("Unknown client profile: $key")

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun load(): ClientCatalog {
            val stream = checkNotNull(
                ClientCatalog::class.java.getResourceAsStream("/innertube/youtube_clients.json"),
            ) { "Missing /innertube/youtube_clients.json" }
            val text = stream.bufferedReader().use { it.readText() }
            return json.decodeFromString(ClientCatalog.serializer(), text)
        }
    }
}
