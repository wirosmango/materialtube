package dev.ytclient

import android.content.Context
import dev.ytclient.innertube.AuthStore
import dev.ytclient.innertube.InnerTube
import dev.ytclient.player.PlaybackSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.Dns
import okhttp3.OkHttpClient
import java.net.Inet4Address
import java.net.InetAddress

/** Минимальный «DI»: один OkHttp, одно хранилище входа и один InnerTube на всё приложение. */
object Graph {
    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /** Для работ, которые должны пережить закрытие экрана (скачивание). */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val http: OkHttpClient by lazy { OkHttpClient.Builder().dns(Ipv4FirstDns).build() }
    val downloader: Downloader by lazy { Downloader(appContext) }
    val settings: AppSettings by lazy { AppSettings(appContext) }
    val auth: AuthStore by lazy { AuthStore(appContext) }
    val innerTube: InnerTube by lazy { InnerTube(http, auth) }

    /** Плеер, который переживает экран ролика: на нём держится мини-плеер. */
    val playback: PlaybackSession by lazy { PlaybackSession(appContext, http) }
}

/**
 * Только IPv4, если он есть. Ссылки googlevideo привязаны к IP, с которого был запрос `player`;
 * при двух семействах адресов часть соединений уходит по IPv6 (особенно через VPN) и получает 403.
 */
private object Ipv4FirstDns : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        val all = Dns.SYSTEM.lookup(hostname)
        val v4 = all.filterIsInstance<Inet4Address>()
        return v4.ifEmpty { all }
    }
}
