package dev.ytclient.ui

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import dev.ytclient.Graph
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val LOGIN_URL =
    "https://accounts.google.com/ServiceLogin?service=youtube" +
        "&continue=https%3A%2F%2Fwww.youtube.com%2Fsignin%3Faction_handle_signin%3Dtrue%26app%3Ddesktop%26hl%3Dru%26next%3D%252F"

// Google не пускает вход во встроенном WebView с «wv» в User-Agent, поэтому представляемся обычным Chrome.
private const val LOGIN_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 15; Pixel 9 Pro) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/141.0.0.0 Mobile Safari/537.36"

private const val CONFIG_SCRIPT =
    "(function(){try{return JSON.stringify({v:ytcfg.get('VISITOR_DATA'),d:ytcfg.get('DATASYNC_ID')});}" +
        "catch(e){return null;}})()"

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LoginScreen(onDone: () -> Unit, onCancel: () -> Unit) {
    BackHandler(onBack = onCancel)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Вход в аккаунт Google") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
    ) { padding ->
        AndroidView(
            modifier = Modifier.padding(padding).fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.userAgentString = LOGIN_USER_AGENT

                    val cookies = CookieManager.getInstance()
                    cookies.setAcceptCookie(true)
                    cookies.setAcceptThirdPartyCookies(this, true)

                    var finished = false
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String?) {
                            if (finished || url == null) return
                            val host = Uri.parse(url).host ?: return
                            if (!host.endsWith("youtube.com")) return

                            val cookie = cookies.getCookie("https://www.youtube.com").orEmpty()
                            if (!cookie.contains("SAPISID=")) return
                            finished = true

                            // visitorData и DATASYNC_ID лежат в ytcfg страницы; без них тоже можно работать.
                            view.evaluateJavascript(CONFIG_SCRIPT) { raw ->
                                val (visitor, dataSync) = runCatching {
                                    val inner = Json.parseToJsonElement(raw).jsonPrimitive.content
                                    val obj = Json.parseToJsonElement(inner).jsonObject
                                    obj["v"]?.jsonPrimitive?.contentOrNull to
                                        obj["d"]?.jsonPrimitive?.contentOrNull
                                }.getOrDefault(null to null)

                                cookies.flush()
                                Graph.auth.save(cookie, visitor, dataSync)
                                onDone()
                            }
                        }
                    }
                    loadUrl(LOGIN_URL)
                }
            },
            onRelease = { it.destroy() },
        )
    }
}
