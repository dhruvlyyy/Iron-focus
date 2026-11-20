package com.example.ironfocus.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import java.util.Locale

private data class DurationPreset(
    val label: String,
    val days: Int,
    val hours: Int,
    val minutes: Int
)

private val DEFAULT_PRESETS = listOf(
    DurationPreset("15m", 0, 0, 15),
    DurationPreset("1h", 0, 1, 0),
    DurationPreset("4h", 0, 4, 0),
    DurationPreset("12h", 0, 12, 0),
    DurationPreset("24h", 1, 0, 0),
    DurationPreset("3d", 3, 0, 0)
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DurationPickerSection(
    days: Int,
    hours: Int,
    minutes: Int,
    onDurationChange: (days: Int, hours: Int, minutes: Int) -> Unit,
    modifier: Modifier = Modifier,
    showPresets: Boolean = true
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            DurationUnitDial(
                label = stringResource(R.string.label_days),
                value = days,
                onIncrement = { onDurationChange((days + 1).coerceAtMost(30), hours, minutes) },
                onDecrement = { onDurationChange((days - 1).coerceAtLeast(0), hours, minutes) },
                canDecrement = days > 0,
                canIncrement = days < 30,
                testTagPrefix = "duration_days",
                modifier = Modifier.weight(1f)
            )
            DurationUnitDial(
                label = stringResource(R.string.label_hours),
                value = hours,
                onIncrement = { onDurationChange(days, (hours + 1).coerceAtMost(23), minutes) },
                onDecrement = { onDurationChange(days, (hours - 1).coerceAtLeast(0), minutes) },
                canDecrement = hours > 0,
                canIncrement = hours < 23,
                testTagPrefix = "duration_hours",
                modifier = Modifier.weight(1f)
            )
            DurationUnitDial(
                label = stringResource(R.string.label_minutes),
                value = minutes,
                onIncrement = { onDurationChange(days, hours, (minutes + 1).coerceAtMost(59)) },
                onDecrement = {
                    val nextMin = (minutes - 1).coerceAtLeast(0)
                    if (days == 0 && hours == 0 && nextMin == 0) {
                        onDurationChange(0, 0, 1)
                    } else {
                        onDurationChange(days, hours, nextMin)
                    }
                },
                canDecrement = !(days == 0 && hours == 0 && minutes <= 1) && minutes > 0,
                canIncrement = minutes < 59,
                testTagPrefix = "duration_minutes",
                modifier = Modifier.weight(1f)
            )
        }

        if (showPresets) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                DEFAULT_PRESETS.forEach { preset ->
                    val selected = days == preset.days && hours == preset.hours && minutes == preset.minutes
                    FilterChip(
                        selected = selected,
                        onClick = { onDurationChange(preset.days, preset.hours, preset.minutes) },
                        label = {
                            Text(
                                text = preset.label,
                                style = MaterialTheme.typography.labelMedium
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.primary,
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selected,
                            borderColor = MaterialTheme.colorScheme.outlineVariant,
                            selectedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                        ),
                        modifier = Modifier.testTag("duration_preset_${preset.label}")
                    )
                }
            }
        }
    }
}

@Composable
private fun DurationUnitDial(
    label: String,
    value: Int,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    canDecrement: Boolean,
    canIncrement: Boolean,
    testTagPrefix: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            IconButton(
                onClick = onIncrement,
                enabled = canIncrement,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("${testTagPrefix}_inc")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Increase $label",
                    tint = if (canIncrement) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                    }
                )
            }

            Text(
                text = String.format(Locale.US, "%02d", value),
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontFamily = MaterialTheme.typography.displayLarge.fontFamily
                ),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("${testTagPrefix}_value")
            )

            IconButton(
                onClick = onDecrement,
                enabled = canDecrement,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("${testTagPrefix}_dec")
            ) {
                Icon(
                    imageVector = Icons.Default.Remove,
                    contentDescription = "Decrease $label",
                    tint = if (canDecrement) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                    }
                )
            }
        }
    }
}
