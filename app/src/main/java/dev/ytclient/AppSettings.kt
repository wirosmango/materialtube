package dev.ytclient

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppTheme(val label: String) {
    Light("Светлая"),
    Dark("Тёмная"),
    Amoled("AMOLED"),
    NothingLight("Nothing светлая"),
    NothingDark("Nothing тёмная"),
}

enum class NavStyle(val label: String) {
    Expressive("MD3E: плавающая"),
    Standard("MD3: стандартная"),
}

enum class AppFont(val label: String) {
    System("Системный"),
    MedeoSans("Medeo Sans"),
    GoogleSans("Google Sans"),
}

/** Настройки приложения в SharedPreferences. Тема null — пока пользователь ничего не выбирал, идём за системной. */
class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _theme = MutableStateFlow(
        prefs.getString("theme", null)?.let { name -> AppTheme.entries.firstOrNull { it.name == name } },
    )
    val theme: StateFlow<AppTheme?> = _theme.asStateFlow()

    fun setTheme(theme: AppTheme) {
        prefs.edit().putString("theme", theme.name).apply()
        _theme.value = theme
    }

    private val _font = MutableStateFlow(
        prefs.getString("font", null)?.let { name -> AppFont.entries.firstOrNull { it.name == name } } ?: AppFont.System,
    )
    val font: StateFlow<AppFont> = _font.asStateFlow()

    fun setFont(font: AppFont) {
        prefs.edit().putString("font", font.name).apply()
        _font.value = font
    }

    private val _navStyle = MutableStateFlow(
        prefs.getString("navStyle", null)?.let { name -> NavStyle.entries.firstOrNull { it.name == name } }
            ?: NavStyle.Expressive,
    )
    val navStyle: StateFlow<NavStyle> = _navStyle.asStateFlow()

    fun setNavStyle(style: NavStyle) {
        prefs.edit().putString("navStyle", style.name).apply()
        _navStyle.value = style
    }
}
