package com.example.ironfocus.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Adds a subtle, architectural dark obsidian ambient radial glow behind screen headers
 * so the dark UI feels forged and dimensional rather than flat black.
 */
fun Modifier.obsidianAtmosphere(
    accentColor: Color,
    intensity: Float = 0.14f
): Modifier = composed {
    val backgroundColor = MaterialTheme.colorScheme.background
    drawBehind {
        drawRect(color = backgroundColor)
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    accentColor.copy(alpha = intensity),
                    accentColor.copy(alpha = intensity * 0.25f),
                    Color.Transparent
                ),
                center = Offset(size.width * 0.5f, -size.height * 0.05f),
                radius = maxOf(size.width, size.height) * 0.65f
            )
        )
    }
}

/**
 * 24-hour visual timeline strip illustrating a schedule's active focus window.
 * Supports both same-day windows and overnight windows wrapping past midnight.
 */
@Composable
fun DayTimelineBar(
    startTotalMinutes: Int,
    endTotalMinutes: Int,
    isEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val activeColor = if (isEnabled) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    }
    val glowColor = if (isEnabled) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
    } else {
        Color.Transparent
    }
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val tickColor = MaterialTheme.colorScheme.outline

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
        ) {
            val totalDayMinutes = 1440f
            val barHeight = 6.dp.toPx()
            val yOffset = (size.height - barHeight) / 2f
            val corner = CornerRadius(barHeight / 2f, barHeight / 2f)

            // Base 24h track
            drawRoundRect(
                color = trackColor,
                topLeft = Offset(0f, yOffset),
                size = Size(size.width, barHeight),
                cornerRadius = corner
            )

            // Subtle 6h markers (6AM, 12PM, 6PM)
            for (quarter in 1..3) {
                val x = size.width * (quarter / 4f)
                drawLine(
                    color = tickColor,
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = 1.dp.toPx()
                )
            }

            val startFraction = (startTotalMinutes.coerceIn(0, 1439) / totalDayMinutes)
            val endFraction = (endTotalMinutes.coerceIn(0, 1439) / totalDayMinutes)

            if (endTotalMinutes > startTotalMinutes) {
                val segLeft = size.width * startFraction
                val segWidth = (size.width * (endFraction - startFraction)).coerceAtLeast(4.dp.toPx())
                if (isEnabled) {
                    drawRoundRect(
                        color = glowColor,
                        topLeft = Offset(segLeft, 0f),
                        size = Size(segWidth, size.height),
                        cornerRadius = CornerRadius(size.height / 2f, size.height / 2f)
                    )
                }
                drawRoundRect(
                    color = activeColor,
                    topLeft = Offset(segLeft, yOffset),
                    size = Size(segWidth, barHeight),
                    cornerRadius = corner
                )
            } else {
                // Overnight wraparound: [start..24:00] and [00:00..end]
                val firstLeft = size.width * startFraction
                val firstWidth = (size.width - firstLeft).coerceAtLeast(3.dp.toPx())
                drawRoundRect(
                    color = activeColor,
                    topLeft = Offset(firstLeft, yOffset),
                    size = Size(firstWidth, barHeight),
                    cornerRadius = corner
                )
                if (endFraction > 0f) {
                    val secondWidth = (size.width * endFraction).coerceAtLeast(3.dp.toPx())
                    drawRoundRect(
                        color = activeColor,
                        topLeft = Offset(0f, yOffset),
                        size = Size(secondWidth, barHeight),
                        cornerRadius = corner
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            listOf("12A", "6A", "12P", "6P", "12A").forEach { marker ->
                Text(
                    text = marker,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                )
            }
        }
    }
}
