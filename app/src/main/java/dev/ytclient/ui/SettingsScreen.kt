package dev.ytclient.ui

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.ripple
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ytclient.AppFont
import dev.ytclient.AppTheme
import dev.ytclient.Graph
import dev.ytclient.NavShape
import dev.ytclient.NavStyle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class SettingsPage(val title: String) {
    Main("Настройки"),
    Navigation("Навигация"),
    Appearance("Оформление"),
    Feed("Лента"),
}

private val ItemIconSize = 40.dp

/** Отступы пункта по вертикали: вместе со значком дают высоту строки 70 dp, как на макете. */
private val ItemVerticalPadding = 15.dp

/** Ширина значка и отступов слева: от неё начинаются разделители между пунктами. */
private val ItemTextInset = 16.dp + ItemIconSize + 16.dp

/**
 * Настройки в духе приложения «Настройки» Android 16 (как в HuegradApp): на главной странице категории одним
 * скруглённым блоком, внутри каждой категории настройки собраны в такие же группы под небольшими заголовками.
 * [bottomInset] — место снизу под мини-плеер, чтобы он не закрывал последние пункты.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    bottomInset: Dp = 0.dp,
    onOpenVideo: (String) -> Unit = {},
) {
    var page by rememberSaveable { mutableStateOf(SettingsPage.Main) }
    var showEasterEggGame by rememberSaveable { mutableStateOf(false) }
    var loadingRandom by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // «Назад» с вложенной страницы возвращает в список, с главной страницы закрывает настройки.
    val goBack: () -> Unit = {
        if (page == SettingsPage.Main) {
            onBack()
        } else {
            page = SettingsPage.Main
        }
    }
    val openRandomVideo: () -> Unit = {
        if (!loadingRandom) {
            loadingRandom = true
            scope.launch {
                try {
                    val feedItems = Graph.innerTube.feed("FEwhat_to_watch").items.filter { !it.isShort }
                    val items = feedItems.ifEmpty {
                        val query = listOf("lofi", "science", "nature", "space", "music", "technology").random()
                        Graph.innerTube.search(query).items.filter { !it.isShort }
                    }
                    val id = items.randomOrNull()?.id
                    if (id != null) {
                        onOpenVideo(id)
                    } else {
                        Toast.makeText(context, "Не удалось найти случайное видео", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Toast.makeText(context, "Не удалось загрузить случайное видео", Toast.LENGTH_SHORT).show()
                } finally {
                    loadingRandom = false
                }
            }
        }
    }
    BackHandler(onBack = goBack)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text(page.title) },
                navigationIcon = {
                    CookieBackButton(
                        onClick = goBack,
                        onLongHold = openRandomVideo,
                    )
                },
            )
        },
    ) { padding ->
        AnimatedContent(
            targetState = page,
            modifier = Modifier.padding(padding).fillMaxSize(),
            transitionSpec = {
                if (targetState != SettingsPage.Main) {
                    (slideInHorizontally { it / 4 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 4 } + fadeOut())
                } else {
                    (slideInHorizontally { -it / 4 } + fadeIn()) togetherWith (slideOutHorizontally { it / 4 } + fadeOut())
                }
            },
            label = "settingsPage",
        ) { current ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 20.dp + bottomInset),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when (current) {
                    SettingsPage.Main -> MainPage(
                        onOpen = { page = it },
                        onOpenVersionGame = { showEasterEggGame = true },
                    )
                    SettingsPage.Navigation -> NavigationPage()
                    SettingsPage.Appearance -> AppearancePage()
                    SettingsPage.Feed -> FeedPage()
                }
            }
        }
    }

    if (showEasterEggGame) {
        Surface(Modifier.fillMaxSize()) {
            ExpressiveFlappyGame(onClose = { showEasterEggGame = false })
        }
    }
}

/**
 * Кнопка «назад» в зубчатом кружке (`MaterialShapes.Cookie12Sided`), который медленно вращается, как в HuegradApp.
 * Пасхалка: удержание кнопки в течение 3 секунд запускает [onLongHold].
 */
@Composable
private fun CookieBackButton(onClick: () -> Unit, onLongHold: () -> Unit) {
    // toShape() сам @Composable и запоминает результат, поэтому в remember его оборачивать нельзя.
    val shape = MaterialShapes.Cookie12Sided.toShape()
    val transition = rememberInfiniteTransition(label = "cookieRotation")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "cookieRotationAngle",
    )
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnLongHold by rememberUpdatedState(onLongHold)
    val interactionSource = remember { MutableInteractionSource() }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    Box(
        modifier = Modifier
            .padding(start = 4.dp, end = 8.dp)
            .size(48.dp)
            .clip(CircleShape)
            .indication(interactionSource, ripple())
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = "Назад"
                onClick(label = "Назад") {
                    currentOnClick()
                    true
                }
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val press = PressInteraction.Press(down.position)
                    scope.launch { interactionSource.emit(press) }
                    var triggered = false
                    val holdJob = scope.launch {
                        delay(3000L)
                        triggered = true
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        currentOnLongHold()
                    }
                    val up = waitForUpOrCancellation()
                    holdJob.cancel()
                    scope.launch {
                        interactionSource.emit(
                            if (up != null) PressInteraction.Release(press) else PressInteraction.Cancel(press),
                        )
                    }
                    if (up != null && !triggered) {
                        currentOnClick()
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        // Вращается только зубчатый фон, стрелка остаётся на месте.
        Box(
            Modifier
                .size(38.dp)
                .graphicsLayer { rotationZ = rotation }
                .clip(shape)
                .background(MaterialTheme.colorScheme.primaryContainer),
        )
        Icon(
            Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = "Назад",
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(22.dp),
        )
    }
}

// ---------- Главная страница ----------

@Composable
private fun MainPage(onOpen: (SettingsPage) -> Unit, onOpenVersionGame: () -> Unit) {
    val context = LocalContext.current
    var versionTaps by remember { mutableIntStateOf(0) }
    var lastTapAt by remember { mutableLongStateOf(0L) }
    SettingsCard {
        SettingsItem(
            icon = Icons.Rounded.Navigation,
            title = "Навигация",
            subtitle = "Нижняя панель, её форма и название страницы",
            onClick = { onOpen(SettingsPage.Navigation) },
        )
        ItemDivider()
        SettingsItem(
            icon = Icons.Rounded.Palette,
            title = "Оформление",
            subtitle = "Тема и шрифт",
            onClick = { onOpen(SettingsPage.Appearance) },
        )
        ItemDivider()
        SettingsItem(
            icon = Icons.Rounded.SmartDisplay,
            title = "Лента",
            subtitle = "Shorts на главной",
            onClick = { onOpen(SettingsPage.Feed) },
        )
        ItemDivider()
        // Пустой пункт на месте «Иконки приложения» из HuegradApp: без значка и текста, только место в списке.
        Spacer(Modifier.fillMaxWidth().height(ItemIconSize + ItemVerticalPadding * 2))
        ItemDivider()
        SettingsItem(
            icon = Icons.Rounded.Info,
            title = "Версия",
            subtitle = appVersion(context),
            onClick = {
                val now = System.currentTimeMillis()
                versionTaps = if (now - lastTapAt <= 2000L) versionTaps + 1 else 1
                lastTapAt = now
                if (versionTaps >= 7) {
                    versionTaps = 0
                    onOpenVersionGame()
                }
            },
        )
    }
}

@Suppress("DEPRECATION")
private fun appVersion(context: Context): String = try {
    val packages = context.packageManager
    val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        packages.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
    } else {
        packages.getPackageInfo(context.packageName, 0)
    }
    info.versionName ?: "—"
} catch (e: Exception) {
    "—"
}

/** Крупный скруглённый блок, в который сгруппированы пункты. */
@Composable
private fun SettingsCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(content = content)
    }
}

@Composable
private fun ItemDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = ItemTextInset),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

/** Пункт списка: круглый значок, название и пояснение. [onClick] = null — пункт только показывает информацию. */
@Composable
private fun SettingsItem(icon: ImageVector, title: String, subtitle: String, onClick: (() -> Unit)?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = ItemVerticalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            Modifier.size(ItemIconSize).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(22.dp),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ---------- Вложенные страницы ----------

/** Заголовок раздела, как в Настройках Android: небольшой текст акцентного цвета над группой. */
@Composable
private fun SectionTitle(title: String, description: String? = null) {
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        if (description != null) {
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Заголовок раздела и под ним скруглённая группа с настройками. */
@Composable
private fun SettingsSection(
    title: String,
    description: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    SectionTitle(title, description)
    SettingsCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
private fun NavigationPage() {
    val navStyle by Graph.settings.navStyle.collectAsStateWithLifecycle()
    SettingsSection(
        title = "Нижняя панель навигации",
        description = if (navStyle == NavStyle.Standard) {
            "Форма и название страницы настраиваются у плавающей панели."
        } else {
            null
        },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NavStyle.entries.forEach { n ->
                FilterChip(
                    selected = n == navStyle,
                    onClick = { Graph.settings.setNavStyle(n) },
                    label = { Text(n.label) },
                )
            }
        }
    }
    if (navStyle == NavStyle.Expressive) {
        val navShape by Graph.settings.navShape.collectAsStateWithLifecycle()
        SettingsSection("Форма плавающей панели") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NavShape.entries.forEach { n ->
                    FilterChip(
                        selected = n == navShape,
                        onClick = { Graph.settings.setNavShape(n) },
                        label = { Text(n.label) },
                    )
                }
            }
        }
        val navShowLabel by Graph.settings.navShowLabel.collectAsStateWithLifecycle()
        SettingsSection("Вид панели") {
            SwitchRow(
                title = "Название страницы на панели",
                subtitle = "Подпись рядом со значком активной вкладки",
                checked = navShowLabel,
                onCheckedChange = { Graph.settings.setNavShowLabel(it) },
            )
        }
    }
}

@Composable
private fun AppearancePage() {
    val saved by Graph.settings.theme.collectAsStateWithLifecycle()
    val selected = saved ?: if (isSystemInDarkTheme()) AppTheme.Dark else AppTheme.Light
    SettingsSection("Тема приложения") {
        AppTheme.entries.chunked(3).forEach { rowThemes ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                rowThemes.forEach { theme ->
                    ThemeOption(
                        theme = theme,
                        selected = theme == selected,
                        onClick = { Graph.settings.setTheme(theme) },
                        modifier = Modifier.weight(1f),
                    )
                }
                // Пустые ячейки в неполном ряду, чтобы карточки не растягивались.
                repeat(3 - rowThemes.size) { Box(Modifier.weight(1f)) }
            }
        }
    }

    val font by Graph.settings.font.collectAsStateWithLifecycle()
    SettingsSection(
        title = "Шрифт",
        description = "Medeo Sans и Google Sans нужно положить в assets/fonts (medeo_sans.ttf, google_sans.ttf). " +
            "Без файла Google Sans берётся из системы, если он есть (Pixel), иначе системный шрифт.",
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppFont.entries.forEach { f ->
                FilterChip(
                    selected = f == font,
                    onClick = { Graph.settings.setFont(f) },
                    label = { Text(f.label) },
                )
            }
        }
    }

    val frostedGlass by Graph.settings.frostedGlass.collectAsStateWithLifecycle()
    SettingsSection("Эффекты") {
        SwitchRow(
            title = "Матовое стекло",
            subtitle = "Полупрозрачная панель навигации с размытием",
            checked = frostedGlass,
            onCheckedChange = { Graph.settings.setFrostedGlass(it) },
        )
    }
}

@Composable
private fun FeedPage() {
    val shortsInFeed by Graph.settings.shortsInFeed.collectAsStateWithLifecycle()
    SettingsSection("Главная лента") {
        SwitchRow(
            title = "Shorts в ленте",
            subtitle = "Полка с короткими видео на главной",
            checked = shortsInFeed,
            onCheckedChange = { Graph.settings.setShortsInFeed(it) },
        )
    }
}

/** Строка настройки с переключателем: название, пояснение и Switch; нажимается вся строка. */
@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ThemeOption(theme: AppTheme, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val isNothing = theme == AppTheme.NothingLight || theme == AppTheme.NothingDark
    // Мини-превью: фон темы, «карточка» с буквами «Aa» и кружок-акцент.
    val (background, card) = when (theme) {
        AppTheme.Light -> Color(0xFFF4F0F4) to Color.White
        AppTheme.Dark -> Color(0xFF1C1B1F) to Color(0xFF2B2930)
        AppTheme.Amoled -> Color.Black to Color(0xFF141414)
        AppTheme.NothingLight -> Color(0xFFF3F3F3) to Color.White
        AppTheme.NothingDark -> Color.Black to Color(0xFF161616)
    }
    val lightCard = theme == AppTheme.Light || theme == AppTheme.NothingLight
    val textColor = if (lightCard) Color(0xFF111111) else Color.White
    // Акцент: у обычных тем — цвет Monet (динамический, для светлой/тёмной палитры), у Nothing — красный.
    val context = LocalContext.current
    val monet = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (lightCard) dynamicLightColorScheme(context).primary else dynamicDarkColorScheme(context).primary
    } else {
        MaterialTheme.colorScheme.primary
    }
    val accent = if (isNothing) Color(0xFFD71921) else monet

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(
            width = if (selected) 3.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
        modifier = modifier,
    ) {
        Column(
            Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(88.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(background)
                    .padding(8.dp),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .align(Alignment.BottomCenter)
                        .clip(RoundedCornerShape(10.dp))
                        .background(card),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Aa", color = textColor, style = MaterialTheme.typography.titleLarge)
                }
                Box(
                    Modifier
                        .size(18.dp)
                        .align(Alignment.TopStart)
                        .clip(CircleShape)
                        .background(accent),
                )
            }
            Text(
                theme.label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
        }
    }
}
