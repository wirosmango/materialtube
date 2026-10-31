package dev.ytclient.ui

import android.app.Activity
import android.content.Context
import android.graphics.Typeface
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import dev.ytclient.AppFont
import dev.ytclient.AppTheme

/** [theme] null — тема ещё не выбрана, берём светлую или тёмную по системной. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun YtTheme(
    theme: AppTheme? = null,
    font: AppFont = AppFont.System,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val darkTheme = when (theme) {
        AppTheme.Light, AppTheme.NothingLight -> false
        AppTheme.Dark, AppTheme.Amoled, AppTheme.NothingDark -> true
        null -> isSystemInDarkTheme()
    }
    val nothing = theme == AppTheme.NothingLight || theme == AppTheme.NothingDark
    val family = remember(context, nothing, font) {
        when (font) {
            AppFont.MedeoSans -> assetFont(context, "medeo_sans")
            AppFont.GoogleSans -> assetFont(context, "google_sans")
                ?: FontFamily(Typeface.create("google-sans", Typeface.NORMAL)) // на Pixel есть в системе
            // Системный шрифт; в темах Nothing заголовки остаются точечными (Ndot), если файл есть.
            AppFont.System -> if (nothing) nothingFont(context) else null
        }
    }
    val headingsOnly = font == AppFont.System && nothing
    val base = when {
        theme == AppTheme.NothingLight -> nothingLight()
        theme == AppTheme.NothingDark -> nothingDark()
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> darkColorScheme()
        else -> lightColorScheme()
    }
    val colorScheme = if (theme == AppTheme.Amoled) base.toAmoled() else base

    // Значки в строке состояния и панели навигации должны читаться на фоне выбранной темы, а не системной.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = context.findActivity()?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        typography = buildTypography(family, headingsOnly, darkTheme),
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}

/** Тёмная палитра с чисто чёрным фоном: акценты остаются из системных цветов (Monet), поверхности — чёрные. */
private fun ColorScheme.toAmoled(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceDim = Color.Black,
    surfaceContainerHigh = Color.Black, // фон приложения
    surfaceContainerLowest = Color(0xFF141414), // плавающая панель и заглушки превью
    surfaceContainerLow = Color(0xFF0A0A0A),
    surfaceContainer = Color(0xFF0F0F0F),
    surfaceContainerHighest = Color(0xFF1C1C1C),
    surfaceBright = Color(0xFF242424),
)

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private val NothingRed = Color(0xFFD71921)

/**
 * Стиль Nothing: монохром (белое и чёрное, серые карточки), единственный акцент — красный, как светодиод.
 * Шрифт Ndot (точечный) в проект не входит, поэтому заголовки набраны моноширинным шрифтом.
 * Активная плашка навигации и кнопки — контрастные (чёрные на светлом, белые на тёмном), кнопка «играть» — красная.
 */
private fun nothingLight() = lightColorScheme(
    primary = NothingRed, onPrimary = Color.White,
    primaryContainer = NothingRed, onPrimaryContainer = Color.White,
    secondary = Color(0xFF1A1A1A), onSecondary = Color.White,
    secondaryContainer = Color(0xFF111111), onSecondaryContainer = Color.White,
    tertiary = Color(0xFF5B5B5B), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE2E2E2), onTertiaryContainer = Color(0xFF111111),
    background = Color(0xFFF3F3F3), onBackground = Color(0xFF111111),
    surface = Color(0xFFF3F3F3), onSurface = Color(0xFF111111),
    surfaceVariant = Color(0xFFE2E2E2), onSurfaceVariant = Color(0xFF5B5B5B),
    surfaceDim = Color(0xFFE6E6E6), surfaceBright = Color.White,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF7F7F7),
    surfaceContainer = Color(0xFFEDEDED),
    surfaceContainerHigh = Color(0xFFF3F3F3), // фон приложения
    surfaceContainerHighest = Color(0xFFE2E2E2),
    outline = Color(0xFF8A8A8A), outlineVariant = Color(0xFFCFCFCF),
    error = NothingRed, onError = Color.White,
)

private fun nothingDark() = darkColorScheme(
    primary = NothingRed, onPrimary = Color.White,
    primaryContainer = NothingRed, onPrimaryContainer = Color.White,
    secondary = Color(0xFFEDEDED), onSecondary = Color.Black,
    secondaryContainer = Color(0xFFF2F2F2), onSecondaryContainer = Color.Black,
    tertiary = Color(0xFFA0A0A0), onTertiary = Color.Black,
    tertiaryContainer = Color(0xFF2A2A2A), onTertiaryContainer = Color(0xFFEDEDED),
    background = Color.Black, onBackground = Color(0xFFF2F2F2),
    surface = Color.Black, onSurface = Color(0xFFF2F2F2),
    surfaceVariant = Color(0xFF242424), onSurfaceVariant = Color(0xFFA0A0A0),
    surfaceDim = Color.Black, surfaceBright = Color(0xFF2A2A2A),
    surfaceContainerLowest = Color(0xFF161616),
    surfaceContainerLow = Color(0xFF0D0D0D),
    surfaceContainer = Color(0xFF121212),
    surfaceContainerHigh = Color.Black, // фон приложения
    surfaceContainerHighest = Color(0xFF242424),
    outline = Color(0xFF6E6E6E), outlineVariant = Color(0xFF333333),
    error = NothingRed, onError = Color.White,
)

/** Ndot (nothingfont) из assets/fonts/ndot.(ttf|otf), если файл положен; иначе моноширинный шрифт. */
private fun nothingFont(context: Context): FontFamily =
    listOf("ndot", "Ndot-57", "Ndot-55").firstNotNullOfOrNull { assetFont(context, it) } ?: FontFamily.Monospace

/** Шрифт из assets/fonts/<name>.(ttf|otf); null, если файла нет. */
private fun assetFont(context: Context, name: String): FontFamily? {
    for (ext in listOf("ttf", "otf")) {
        try {
            val path = "fonts/$name.$ext"
            context.assets.open(path).close()
            return FontFamily(Font(path, context.assets))
        } catch (e: Exception) {
            // файла нет — пробуем дальше
        }
    }
    return null
}

/** [family] null — шрифт по умолчанию. В тёмной теме заголовки белые, а не тонированные палитрой. */
private fun buildTypography(family: FontFamily?, headingsOnly: Boolean, dark: Boolean): Typography {
    val base = Typography()
    val white = if (dark) Color.White else Color.Unspecified

    fun TextStyle.heading() = copy(fontFamily = family ?: fontFamily, color = white)
    fun TextStyle.label() = if (family != null) copy(fontFamily = family) else this
    fun TextStyle.body() = if (family != null && !headingsOnly) copy(fontFamily = family) else this

    return base.copy(
        displayLarge = base.displayLarge.heading(),
        displayMedium = base.displayMedium.heading(),
        displaySmall = base.displaySmall.heading(),
        headlineLarge = base.headlineLarge.heading(),
        headlineMedium = base.headlineMedium.heading(),
        headlineSmall = base.headlineSmall.heading(),
        titleLarge = base.titleLarge.heading(),
        titleMedium = base.titleMedium.heading(),
        titleSmall = base.titleSmall.heading(),
        labelLarge = base.labelLarge.label(),
        labelMedium = base.labelMedium.label(),
        labelSmall = base.labelSmall.label(),
        bodyLarge = base.bodyLarge.body(),
        bodyMedium = base.bodyMedium.body(),
        bodySmall = base.bodySmall.body(),
    )
}
