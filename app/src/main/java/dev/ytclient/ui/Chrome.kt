package dev.ytclient.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Сколько места снизу списки оставляют под парящую панель навигации. */
private var barClearance by mutableStateOf(112.dp)

/** Отступ снизу под панель навигации; зависит от выбранного стиля панели. */
val FloatingBarClearance: Dp get() = barClearance

fun applyBarClearance(value: Dp) {
    barClearance = value
}

/** Стандартная панель MD3: во всю ширину у нижнего края, подпись у каждого пункта. */
@Composable
fun StandardNavBar(
    items: List<NavItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBar(modifier = modifier) {
        items.forEachIndexed { index, item ->
            NavigationBarItem(
                selected = index == selectedIndex,
                onClick = { onSelect(index) },
                icon = { Icon(item.icon, contentDescription = item.label) },
                label = { Text(item.label) },
            )
        }
    }
}

data class NavItem(val label: String, val icon: ImageVector)

/**
 * Парящая панель (прямоугольник со скруглёнными углами) вместо обычной NavigationBar: у активной вкладки светлая плашка,
 * которая пружинисто растягивается и меняет цвет.
 */
@Composable
fun FloatingNavBar(
    items: List<NavItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        tonalElevation = 0.dp,
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier.padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEachIndexed { index, item ->
                NavPill(item = item, selected = index == selectedIndex, onClick = { onSelect(index) })
            }
        }
    }
}

@Composable
private fun NavPill(item: NavItem, selected: Boolean, onClick: () -> Unit) {
    val width by animateDpAsState(
        targetValue = if (selected) 80.dp else 56.dp,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow),
        label = "pillWidth",
    )
    val container by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "pillContainer",
    )
    val tint by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "pillTint",
    )

    Box(
        modifier = Modifier
            .height(52.dp)
            .width(width)
            .clip(RoundedCornerShape(22.dp))
            .background(container)
            .clickable(onClickLabel = item.label, role = Role.Tab, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(item.icon, contentDescription = item.label, tint = tint)
    }
}

/** Шапка как на макете: крупное название слева, поиск справа, без фона. */
@Composable
fun AppTopBar(title: String, onSearch: (() -> Unit)? = null, onSettings: (() -> Unit)? = null) {
    TopAppBar(
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Medium),
            )
        },
        actions = {
            if (onSearch != null) {
                IconButton(onClick = onSearch) {
                    Icon(Icons.Rounded.Search, contentDescription = "Поиск")
                }
            }
            if (onSettings != null) {
                IconButton(onClick = onSettings) {
                    Icon(Icons.Rounded.Settings, contentDescription = "Настройки")
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = Color.Transparent,
        ),
    )
}
