package dev.ytclient.ui

import androidx.activity.compose.BackHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ytclient.AppFont
import dev.ytclient.AppTheme
import dev.ytclient.NavStyle
import dev.ytclient.Graph

/** Настройки. Пока одна: тема (светлая, тёмная, AMOLED). */
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val saved by Graph.settings.theme.collectAsStateWithLifecycle()
    val selected = saved ?: if (isSystemInDarkTheme()) AppTheme.Dark else AppTheme.Light

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Тема", style = MaterialTheme.typography.titleMedium)
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

            val navStyle by Graph.settings.navStyle.collectAsStateWithLifecycle()
            Text("Нижняя панель", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NavStyle.entries.forEach { n ->
                    FilterChip(
                        selected = n == navStyle,
                        onClick = { Graph.settings.setNavStyle(n) },
                        label = { Text(n.label) },
                    )
                }
            }

            val font by Graph.settings.font.collectAsStateWithLifecycle()
            Text("Шрифт", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppFont.entries.forEach { f ->
                    FilterChip(
                        selected = f == font,
                        onClick = { Graph.settings.setFont(f) },
                        label = { Text(f.label) },
                    )
                }
            }
            Text(
                "Medeo Sans и Google Sans нужно положить в assets/fonts (medeo_sans.ttf, google_sans.ttf). " +
                    "Без файла Google Sans берётся из системы, если он есть (Pixel), иначе системный шрифт.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
