package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val IronFocusDarkColorScheme = darkColorScheme(
    primary = EmberPrimary,
    onPrimary = OnEmberPrimary,
    primaryContainer = EmberPrimaryContainer,
    onPrimaryContainer = OnEmberPrimaryContainer,
    secondary = TitaniumSecondary,
    onSecondary = OnTitaniumSecondary,
    secondaryContainer = TitaniumSecondaryContainer,
    onSecondaryContainer = OnTitaniumSecondaryContainer,
    tertiary = PhosphorEmerald,
    onTertiary = OnPhosphorEmerald,
    tertiaryContainer = PhosphorEmeraldContainer,
    onTertiaryContainer = OnPhosphorEmeraldContainer,
    error = SignalCrimson,
    onError = OnSignalCrimson,
    errorContainer = SignalCrimsonContainer,
    onErrorContainer = OnSignalCrimsonContainer,
    background = ObsidianVoid,
    onBackground = TextPrimary,
    surface = ObsidianSurface,
    onSurface = TextPrimary,
    surfaceVariant = GunmetalCard,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLowest = ObsidianVoid,
    surfaceContainerLow = ObsidianSurface,
    surfaceContainer = GunmetalCard,
    surfaceContainerHigh = GunmetalElevated,
    surfaceContainerHighest = SteelBorder,
    outline = SteelBorder,
    outlineVariant = SteelBorderSubtle,
)

private val IronFocusShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun IronFocusTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = IronFocusDarkColorScheme,
        typography = IronFocusTypography,
        shapes = IronFocusShapes,
        content = content
    )
}
