package dev.expressive.launcher.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val ExpressiveDark = darkColorScheme(
    primary = Color(0xFFCABEFF),
    onPrimary = Color(0xFF2F1B7A),
    primaryContainer = Color(0xFF473A92),
    onPrimaryContainer = Color(0xFFE7DEFF),
    secondary = Color(0xFF6FDBC3),
    onSecondary = Color(0xFF00382E),
    secondaryContainer = Color(0xFF0F5043),
    onSecondaryContainer = Color(0xFF8CF8DF),
    tertiary = Color(0xFFFFB0CB),
    onTertiary = Color(0xFF5E1136),
    tertiaryContainer = Color(0xFF7B2A4D),
    onTertiaryContainer = Color(0xFFFFD9E4),
    background = Color(0xFF0F0E15),
    onBackground = Color(0xFFE8E3EE),
    surface = Color(0xFF0F0E15),
    onSurface = Color(0xFFE8E3EE),
    surfaceVariant = Color(0xFF47454F),
    onSurfaceVariant = Color(0xFFCAC5D2),
    surfaceDim = Color(0xFF0F0E15),
    surfaceBright = Color(0xFF353240),
    surfaceContainerLowest = Color(0xFF0A090F),
    surfaceContainerLow = Color(0xFF181620),
    surfaceContainer = Color(0xFF1C1A25),
    surfaceContainerHigh = Color(0xFF272431),
    surfaceContainerHighest = Color(0xFF322F3C),
    outline = Color(0xFF948F9D),
    outlineVariant = Color(0xFF47454F)
)

private val LauncherShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(30.dp),
    extraLarge = RoundedCornerShape(40.dp)
)

private val LauncherTypography = Typography().let {
    it.copy(
        displayMedium = it.displayMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-1.2).sp),
        headlineLarge = it.headlineLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        titleLarge = it.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = it.labelLarge.copy(fontWeight = FontWeight.SemiBold)
    )
}

@Composable
fun LauncherTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ExpressiveDark,
        shapes = LauncherShapes,
        typography = LauncherTypography,
        content = content
    )
}
