package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val CrownfallDarkColorScheme = darkColorScheme(
    primary = CrownGold,
    onPrimary = DarkInkOnGold,
    primaryContainer = DeepGoldContainer,
    onPrimaryContainer = GildedHighlight,
    secondary = WarCrimson,
    onSecondary = IvoryOnCrimson,
    secondaryContainer = BloodVelvetContainer,
    onSecondaryContainer = EmberCrimson,
    tertiary = ArcaneEmerald,
    onTertiary = DarkInkOnGold,
    tertiaryContainer = DeepEmeraldContainer,
    onTertiaryContainer = TimberMoss,
    background = ObsidianVoid,
    onBackground = ParchmentIvory,
    surface = BasaltSlate,
    onSurface = ParchmentIvory,
    surfaceVariant = ForgedIron,
    onSurfaceVariant = WeatheredAsh,
    surfaceContainerLowest = ObsidianVoid,
    surfaceContainerLow = BasaltSlate,
    surfaceContainer = ForgedIron,
    surfaceContainerHigh = BurnishedCharcoal,
    surfaceContainerHighest = EtchedSteel,
    outline = EtchedSteel,
    outlineVariant = SubtleSteelBorder,
    error = EmberCrimson,
    onError = IvoryOnCrimson,
    errorContainer = BloodVelvetContainer,
    onErrorContainer = IvoryOnCrimson
)

private val CrownfallShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(22.dp)
)

@Composable
fun CrownfallTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = CrownfallDarkColorScheme,
        typography = CrownfallTypography,
        shapes = CrownfallShapes,
        content = content
    )
}
