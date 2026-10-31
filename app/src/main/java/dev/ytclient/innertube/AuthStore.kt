package dev.ytclient.innertube

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AuthState(
    val cookie: String? = null,
    val visitorData: String? = null,
    val dataSyncId: String? = null,
) {
    val loggedIn: Boolean get() = cookieValue("SAPISID") != null

    fun cookieValue(name: String): String? =
        cookie?.split(';')
            ?.map { it.trim() }
            ?.firstOrNull { it.startsWith("$name=") }
            ?.substringAfter('=')
            ?.takeIf { it.isNotEmpty() }

    /** У аккаунтов-«брендов» DATASYNC_ID имеет вид "<id канала>||<id пользователя>". */
    val onBehalfOfUser: String?
        get() {
            val value = dataSyncId?.trim() ?: return null
            val separator = value.indexOf("||")
            if (separator <= 0 || separator + 2 >= value.length) return null
            return value.substring(0, separator).trim().takeIf { it.isNotBlank() }
        }
}

/** Хранит cookies входа в обычных SharedPreferences приложения (для прототипа достаточно). */
class AuthStore(context: Context) {
    private val prefs = context.getSharedPreferences("auth", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(
        AuthState(
            cookie = prefs.getString("cookie", null),
            visitorData = prefs.getString("visitorData", null),
            dataSyncId = prefs.getString("dataSyncId", null),
        ),
    )
    val state: StateFlow<AuthState> = _state.asStateFlow()
    val current: AuthState get() = _state.value

    fun save(cookie: String, visitorData: String?, dataSyncId: String?) {
        prefs.edit()
            .putString("cookie", cookie)
            .putString("visitorData", visitorData)
            .putString("dataSyncId", dataSyncId)
            .apply()
        _state.value = AuthState(cookie, visitorData, dataSyncId)
    }

    fun clear() {
        prefs.edit().clear().apply()
        _state.value = AuthState()
    }
}
