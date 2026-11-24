package dev.ytclient.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlin.random.Random

private enum class GamePhase { Ready, Playing, Over }

private class PipeObstacle(
    var x: Float,
    val gapCenterY: Float,
    val gapHeight: Float,
    val shapeIndex: Int,
    val colorSlot: Int,
    var passed: Boolean = false,
)

/**
 * Пасхалка в стиле мини-игры из Android 6, оформленная в Material Design 3 Expressive:
 * фигуры из [MaterialShapes] и динамические цвета Monet ([MaterialTheme.colorScheme]).
 */
@Composable
fun ExpressiveFlappyGame(onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val haptic = LocalHapticFeedback.current

    val shapes: List<Shape> = listOf(
        MaterialShapes.Cookie12Sided.toShape(),
        MaterialShapes.Sunny.toShape(),
        MaterialShapes.SoftBurst.toShape(),
        MaterialShapes.Clover4Leaf.toShape(),
        MaterialShapes.Cookie7Sided.toShape(),
        MaterialShapes.Pentagon.toShape(),
        MaterialShapes.Gem.toShape(),
        MaterialShapes.Flower.toShape(),
    )

    val colors = MaterialTheme.colorScheme
    val accentPalette = listOf(
        colors.primary to colors.onPrimary,
        colors.secondary to colors.onSecondary,
        colors.tertiary to colors.onTertiary,
        colors.primaryContainer to colors.onPrimaryContainer,
        colors.secondaryContainer to colors.onSecondaryContainer,
        colors.tertiaryContainer to colors.onTertiaryContainer,
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        colors.surfaceContainerLowest,
                        colors.primaryContainer.copy(alpha = 0.28f),
                        colors.tertiaryContainer.copy(alpha = 0.25f),
                    ),
                ),
            ),
    ) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }

        val playerSizePx = with(density) { 46.dp.toPx() }
        val playerRadiusPx = playerSizePx * 0.42f
        val playerX = widthPx * 0.26f
        val pipeWidthPx = with(density) { 64.dp.toPx() }
        val capSizePx = with(density) { 78.dp.toPx() }
        val pipeSpacingPx = with(density) { 260.dp.toPx() }
        val gapHeightPx = with(density) { 188.dp.toPx() }
        val gravityPx = with(density) { 1650.dp.toPx() }
        val jumpVelocityPx = with(density) { -560.dp.toPx() }
        val speedPx = with(density) { 195.dp.toPx() }
        val marginPx = with(density) { 96.dp.toPx() }

        var phase by remember { mutableStateOf(GamePhase.Ready) }
        var playerY by remember { mutableFloatStateOf(heightPx * 0.45f) }
        var velocityY by remember { mutableFloatStateOf(0f) }
        var score by remember { mutableIntStateOf(0) }
        var bestScore by remember { mutableIntStateOf(0) }
        var playerShapeIndex by remember { mutableIntStateOf(0) }
        var bgScroll by remember { mutableFloatStateOf(0f) }
        var gameOverTimeMs by remember { mutableLongStateOf(0L) }
        val pipes = remember { mutableStateListOf<PipeObstacle>() }

        fun spawnPipe(x: Float): PipeObstacle {
            val minY = marginPx + gapHeightPx / 2f
            val maxY = (heightPx - marginPx - gapHeightPx / 2f).coerceAtLeast(minY + 1f)
            val center = minY + Random.nextFloat() * (maxY - minY)
            return PipeObstacle(
                x = x,
                gapCenterY = center,
                gapHeight = gapHeightPx,
                shapeIndex = Random.nextInt(shapes.size),
                colorSlot = Random.nextInt(accentPalette.size),
            )
        }

        fun resetGame(startImmediately: Boolean) {
            playerY = heightPx * 0.45f
            velocityY = if (startImmediately) jumpVelocityPx else 0f
            score = 0
            pipes.clear()
            val firstX = widthPx + pipeWidthPx
            for (i in 0..3) {
                pipes += spawnPipe(firstX + i * pipeSpacingPx)
            }
            phase = if (startImmediately) GamePhase.Playing else GamePhase.Ready
        }

        LaunchedEffect(widthPx, heightPx) {
            if (pipes.isEmpty()) resetGame(startImmediately = false)
        }

        LaunchedEffect(phase) {
            if (phase != GamePhase.Playing) return@LaunchedEffect
            var lastNs = withFrameNanos { it }
            while (phase == GamePhase.Playing) {
                withFrameNanos { now ->
                    val dt = ((now - lastNs) / 1_000_000_000f).coerceIn(0f, 0.05f)
                    lastNs = now

                    velocityY += gravityPx * dt
                    playerY += velocityY * dt
                    bgScroll += speedPx * 0.25f * dt

                    if (playerY - playerRadiusPx < 0f || playerY + playerRadiusPx > heightPx) {
                        phase = GamePhase.Over
                        gameOverTimeMs = System.currentTimeMillis()
                        if (score > bestScore) bestScore = score
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        return@withFrameNanos
                    }

                    val iterator = pipes.listIterator()
                    var maxPipeX = 0f
                    while (iterator.hasNext()) {
                        val pipe = iterator.next()
                        pipe.x -= speedPx * dt
                        if (pipe.x > maxPipeX) maxPipeX = pipe.x

                        if (!pipe.passed && pipe.x + pipeWidthPx / 2f < playerX) {
                            pipe.passed = true
                            score++
                            playerShapeIndex = (playerShapeIndex + 1) % shapes.size
                        }

                        val gapTop = pipe.gapCenterY - pipe.gapHeight / 2f
                        val gapBottom = pipe.gapCenterY + pipe.gapHeight / 2f
                        val hitsX = playerX + playerRadiusPx > pipe.x && playerX - playerRadiusPx < pipe.x + pipeWidthPx
                        val hitsY = playerY - playerRadiusPx < gapTop || playerY + playerRadiusPx > gapBottom
                        if (hitsX && hitsY) {
                            phase = GamePhase.Over
                            gameOverTimeMs = System.currentTimeMillis()
                            if (score > bestScore) bestScore = score
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            return@withFrameNanos
                        }
                    }

                    pipes.removeAll { it.x + capSizePx < 0f }
                    while (pipes.size < 4) {
                        val nextX = (pipes.maxOfOrNull { it.x } ?: widthPx) + pipeSpacingPx
                        pipes += spawnPipe(nextX)
                    }
                }
            }
        }

        val onTap: () -> Unit = {
            when (phase) {
                GamePhase.Ready -> resetGame(startImmediately = true)
                GamePhase.Playing -> velocityY = jumpVelocityPx
                GamePhase.Over -> {
                    if (System.currentTimeMillis() - gameOverTimeMs > 350L) {
                        resetGame(startImmediately = true)
                    }
                }
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { onTap() })
                },
        ) {
            // Декоративные фоновые фигуры Material Expressive с лёгким параллаксом.
            for (i in 0..4) {
                val bgSize = (96 + (i % 3) * 32).dp
                val bgSizePx = with(density) { bgSize.toPx() }
                val totalSpan = widthPx + bgSizePx * 2
                val rawX = (i * widthPx * 0.35f - bgScroll * (0.6f + i * 0.1f))
                val x = ((rawX % totalSpan) + totalSpan) % totalSpan - bgSizePx
                val y = heightPx * (0.15f + (i * 0.17f) % 0.7f)
                Box(
                    Modifier
                        .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                        .size(bgSize)
                        .graphicsLayer { rotationZ = bgScroll * 0.03f * (if (i % 2 == 0) 1f else -1f) }
                        .clip(shapes[(i * 2) % shapes.size])
                        .background(colors.primary.copy(alpha = 0.06f)),
                )
            }

            // Препятствия: колонны и выразительные «шляпки» из MaterialShapes в цветах Monet.
            pipes.forEach { pipe ->
                val (containerColor, contentColor) = accentPalette[pipe.colorSlot]
                val gapTop = pipe.gapCenterY - pipe.gapHeight / 2f
                val gapBottom = pipe.gapCenterY + pipe.gapHeight / 2f
                val pipeShape = shapes[pipe.shapeIndex]
                val capOffsetX = pipe.x - (capSizePx - pipeWidthPx) / 2f

                // Верхняя колонна
                if (gapTop > 0f) {
                    Box(
                        Modifier
                            .offset { IntOffset(pipe.x.roundToInt(), 0) }
                            .size(width = with(density) { pipeWidthPx.toDp() }, height = with(density) { gapTop.toDp() })
                            .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
                            .background(containerColor.copy(alpha = 0.85f)),
                    )
                    Box(
                        Modifier
                            .offset {
                                IntOffset(
                                    capOffsetX.roundToInt(),
                                    (gapTop - capSizePx * 0.72f).roundToInt(),
                                )
                            }
                            .size(with(density) { capSizePx.toDp() })
                            .clip(pipeShape)
                            .background(containerColor),
                    ) {
                        Box(
                            Modifier
                                .align(Alignment.Center)
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(contentColor.copy(alpha = 0.3f)),
                        )
                    }
                }

                // Нижняя колонна
                val bottomHeight = (heightPx - gapBottom).coerceAtLeast(0f)
                if (bottomHeight > 0f) {
                    Box(
                        Modifier
                            .offset { IntOffset(pipe.x.roundToInt(), gapBottom.roundToInt()) }
                            .size(
                                width = with(density) { pipeWidthPx.toDp() },
                                height = with(density) { bottomHeight.toDp() },
                            )
                            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                            .background(containerColor.copy(alpha = 0.85f)),
                    )
                    Box(
                        Modifier
                            .offset {
                                IntOffset(
                                    capOffsetX.roundToInt(),
                                    (gapBottom - capSizePx * 0.28f).roundToInt(),
                                )
                            }
                            .size(with(density) { capSizePx.toDp() })
                            .clip(pipeShape)
                            .background(containerColor),
                    ) {
                        Box(
                            Modifier
                                .align(Alignment.Center)
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(contentColor.copy(alpha = 0.3f)),
                        )
                    }
                }
            }

            // Игрок: фигура Material Expressive с плавным наклоном.
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (playerX - playerSizePx / 2f).roundToInt(),
                            (playerY - playerSizePx / 2f).roundToInt(),
                        )
                    }
                    .size(with(density) { playerSizePx.toDp() })
                    .graphicsLayer {
                        rotationZ = if (phase == GamePhase.Playing) {
                            (velocityY * 0.045f).coerceIn(-30f, 55f)
                        } else {
                            0f
                        }
                    }
                    .clip(shapes[playerShapeIndex])
                    .background(colors.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = colors.onPrimary,
                    modifier = Modifier.size(24.dp),
                )
            }

            // Верхняя панель со счётом и кнопкой закрытия.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = CircleShape,
                    color = colors.surfaceContainerHigh.copy(alpha = 0.88f),
                ) {
                    Text(
                        text = "$score",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = colors.onSurface,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                }
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(colors.surfaceContainerHigh.copy(alpha = 0.88f)),
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = "Закрыть", tint = colors.onSurface)
                }
            }

            // Подсказка на старте.
            if (phase == GamePhase.Ready) {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = colors.secondaryContainer.copy(alpha = 0.94f),
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 84.dp),
                ) {
                    Text(
                        text = "Нажмите, чтобы взлететь",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
                    )
                }
            }

            // Экран проигрыша.
            if (phase == GamePhase.Over) {
                Surface(
                    shape = RoundedCornerShape(32.dp),
                    color = colors.surfaceContainerHigh.copy(alpha = 0.96f),
                    shadowElevation = 8.dp,
                    modifier = Modifier.align(Alignment.Center).padding(horizontal = 32.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Text(
                            text = "Игра окончена",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Счёт: $score  •  Рекорд: $bestScore",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(onClick = { resetGame(startImmediately = true) }) {
                                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Text("  Ещё раз")
                            }
                        }
                    }
                }
            }
        }
    }
}
