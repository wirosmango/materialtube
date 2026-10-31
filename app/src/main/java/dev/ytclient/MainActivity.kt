package dev.ytclient

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.ytclient.player.PlaybackMeta
import dev.ytclient.ui.MiniPlayer
import dev.ytclient.ui.miniPlayerClearance
import dev.ytclient.ui.SettingsScreen
import dev.ytclient.ui.StandardNavBar
import dev.ytclient.ui.applyBarClearance
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.ytclient.ui.ChannelScreen
import dev.ytclient.ui.ChannelsRow
import dev.ytclient.ui.LocalOpenChannel
import dev.ytclient.ui.SubscriptionsViewModel
import dev.ytclient.ui.AccountScreen
import dev.ytclient.ui.FeedScreen
import dev.ytclient.ui.FloatingNavBar
import dev.ytclient.ui.LoginScreen
import dev.ytclient.ui.NavItem
import dev.ytclient.ui.PlayerScreen
import dev.ytclient.ui.SearchScreen
import dev.ytclient.ui.ShortsScreen
import dev.ytclient.ui.YtTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val theme by Graph.settings.theme.collectAsStateWithLifecycle()
            val font by Graph.settings.font.collectAsStateWithLifecycle()
            YtTheme(theme, font) {
                YtApp()
            }
        }
    }
}

private enum class Tab(val label: String, val icon: ImageVector) {
    Home("Главная", Icons.Rounded.Home),
    Shorts("Shorts", Icons.Rounded.SmartDisplay),
    Subscriptions("Подписки", Icons.Rounded.Subscriptions),
    Account("Профиль", Icons.Rounded.AccountCircle),
}

private val navItems = Tab.entries.map { NavItem(it.label, it.icon) }

@Composable
private fun YtApp() {
    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
    // openVideoId — ролик в плеере; playerExpanded = false — он свёрнут в мини-плеер и продолжает играть.
    var openVideoId by rememberSaveable { mutableStateOf<String?>(null) }
    var playerExpanded by rememberSaveable { mutableStateOf(false) }
    var showSearch by rememberSaveable { mutableStateOf(false) }
    var showLogin by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var openChannelId by rememberSaveable { mutableStateOf<String?>(null) }
    val subscriptionsVm: SubscriptionsViewModel = viewModel()
    val channels by subscriptionsVm.channels.collectAsStateWithLifecycle()
    val openVideo: (String) -> Unit = {
        openVideoId = it
        playerExpanded = true
    }

    val playback = Graph.playback
    val meta by playback.meta.collectAsStateWithLifecycle()
    val playbackError by playback.error.collectAsStateWithLifecycle()
    // Последний показанный ролик: пока плашка уезжает вниз после закрытия, meta уже null.
    var lastMeta by remember { mutableStateOf<PlaybackMeta?>(null) }
    LaunchedEffect(meta) { meta?.let { lastMeta = it } }

    // После перезапуска процесса «свёрнутый» ролик есть в сохранённом состоянии, а плеера уже нет.
    LaunchedEffect(Unit) {
        if (openVideoId != null && !playerExpanded && playback.meta.value == null) openVideoId = null
    }
    // Фонового воспроизведения пока нет: при уходе из приложения ставим на паузу.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) playback.pause()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    // Shorts играют со звуком: чтобы они не накладывались на мини-плеер, ставим его на паузу.
    LaunchedEffect(tab) { if (tab == Tab.Shorts) playback.pause() }

    CompositionLocalProvider(LocalOpenChannel provides { openChannelId = it }) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Crossfade(targetState = tab, animationSpec = tween(220), label = "tab") { current ->
            when (current) {
                Tab.Home -> FeedScreen(
                    title = "MaterialTube",
                    browseId = "FEwhat_to_watch",
                    requiresLogin = false,
                    loginHint = "Войдите в аккаунт, чтобы увидеть рекомендации",
                    onOpenVideo = openVideo,
                    onLogin = { showLogin = true },
                    onSearch = { showSearch = true },
                )
                Tab.Shorts -> ShortsScreen(active = !showSearch && !(openVideoId != null && playerExpanded) && !showLogin)
                Tab.Subscriptions -> FeedScreen(
                    title = "Подписки",
                    browseId = "FEsubscriptions",
                    requiresLogin = true,
                    loginHint = "Войдите в аккаунт, чтобы увидеть подписки",
                    onOpenVideo = openVideo,
                    onLogin = { showLogin = true },
                    onSearch = { showSearch = true },
                    header = if (channels.isNotEmpty()) {
                        { ChannelsRow(channels = channels, onOpen = { openChannelId = it }) }
                    } else {
                        null
                    },
                )
                Tab.Account -> AccountScreen(
                    onLogin = { showLogin = true },
                    onSearch = { showSearch = true },
                    onSettings = { showSettings = true },
                    onOpenVideo = openVideo,
                )
            }
        }

        val navStyle by Graph.settings.navStyle.collectAsStateWithLifecycle()
        // Поверх вкладок мини-плеер виден, пока ролик свёрнут (и не на Shorts, где свой звук и полноэкранный пейджер).
        val overlayOpen = showSearch || openChannelId != null || showSettings
        val miniClearance = miniPlayerClearance()
        val miniVisible = openVideoId != null && !playerExpanded && meta?.videoId == openVideoId &&
            tab != Tab.Shorts && !showLogin
        SideEffect {
            val base = if (navStyle == NavStyle.Standard) 96.dp else 112.dp
            applyBarClearance(if (miniVisible && !overlayOpen) base + miniClearance else base)
        }
        if (navStyle == NavStyle.Standard) {
            StandardNavBar(
                items = navItems,
                selectedIndex = tab.ordinal,
                onSelect = { tab = Tab.entries[it] },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        } else {
            FloatingNavBar(
                items = navItems,
                selectedIndex = tab.ordinal,
                onSelect = { tab = Tab.entries[it] },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }

        if (showSearch) {
            Surface(Modifier.fillMaxSize()) {
                SearchScreen(
                    onOpenVideo = openVideo,
                    onBack = { showSearch = false },
                )
            }
        }

        openVideoId?.let { id ->
            if (playerExpanded) {
                Surface(Modifier.fillMaxSize()) {
                    // «Назад» сворачивает ролик в мини-плеер, а не закрывает его.
                    PlayerScreen(
                        videoId = id,
                        onBack = { playerExpanded = false },
                        onOpenVideo = openVideo,
                    )
                }
            }
        }

        openChannelId?.let { id ->
            Surface(Modifier.fillMaxSize()) {
                ChannelScreen(
                    channelId = id,
                    onBack = { openChannelId = null },
                    onOpenVideo = {
                        openChannelId = null
                        openVideo(it)
                    },
                )
            }
        }

        if (showSettings) {
            Surface(Modifier.fillMaxSize()) {
                SettingsScreen(onBack = { showSettings = false })
            }
        }

        // Над панелью навигации; когда поверх вкладок открыт другой экран (поиск, канал, настройки), панели не видно,
        // и плашка прижимается к низу.
        val miniBottom = when {
            overlayOpen -> 8.dp
            navStyle == NavStyle.Standard -> 80.dp + 8.dp // высота NavigationBar + зазор
            else -> 76.dp + 8.dp // плавающая панель: 64 dp высота + 12 dp отступ снизу + зазор
        }
        AnimatedVisibility(
            visible = miniVisible,
            enter = slideInVertically(
                animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
            ) { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = miniBottom),
        ) {
            (meta ?: lastMeta)?.let { shown ->
                MiniPlayer(
                    meta = shown,
                    error = playbackError,
                    player = playback.player,
                    onExpand = { playerExpanded = true },
                    onClose = {
                        playback.close()
                        openVideoId = null
                    },
                )
            }
        }

        if (showLogin) {
            Surface(Modifier.fillMaxSize()) {
                LoginScreen(
                    onDone = { showLogin = false },
                    onCancel = { showLogin = false },
                )
            }
        }
    }
    }
}
