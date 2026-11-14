package dev.expressive.launcher.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import android.graphics.Color as AndroidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val LocalCalmMotion = compositionLocalOf { false }

data class Accent(
    val name: String,
    val primary: Color,
    val onPrimary: Color,
    val container: Color,
    val onContainer: Color
)

val Accents = listOf(
    Accent("Violet", Color(0xFFCABEFF), Color(0xFF2F1B7A), Color(0xFF473A92), Color(0xFFE7DEFF)),
    Accent("Mint", Color(0xFF6FDBC3), Color(0xFF00382E), Color(0xFF0F5043), Color(0xFF8CF8DF)),
    Accent("Rose", Color(0xFFFFB0CB), Color(0xFF5E1136), Color(0xFF7B2A4D), Color(0xFFFFD9E4)),
    Accent("Amber", Color(0xFFFFB95C), Color(0xFF472A00), Color(0xFF653E00), Color(0xFFFFDDB1)),
    Accent("Sky", Color(0xFF9FCAFF), Color(0xFF00315C), Color(0xFF00497F), Color(0xFFD2E4FF)),
    Accent("Graphite", Color(0xFFC9CCD6), Color(0xFF2B2E36), Color(0xFF3B3F4A), Color(0xFFE1E3EC))
)

val LocalWallpaperAccent = compositionLocalOf { Accents.first() }

fun accentFromSeed(seed: Color): Accent {
    val hsv = FloatArray(3)
    AndroidColor.colorToHSV(seed.toArgb(), hsv)
    val neutral = hsv[1] < 0.1f
    val hue = hsv[0]
    val sat = hsv[1].coerceIn(0.35f, 0.9f)
    fun tone(s: Float, v: Float): Color =
        Color(AndroidColor.HSVToColor(floatArrayOf(hue, if (neutral) 0.04f else s, v)))
    return Accent("Wallpaper", tone(sat * 0.4f, 0.97f), tone(sat, 0.28f), tone(sat * 0.75f, 0.42f), tone(sat * 0.12f, 0.99f))
}

private val BaseDark = darkColorScheme(
    secondary = Color(0xFFBFC6DC),
    onSecondary = Color(0xFF293041),
    secondaryContainer = Color(0xFF2E3340),
    onSecondaryContainer = Color(0xFFDCE2F9),
    background = Color(0xFF0E0F12),
    onBackground = Color(0xFFE4E2E6),
    surface = Color(0xFF0E0F12),
    onSurface = Color(0xFFE4E2E6),
    surfaceVariant = Color(0xFF44474F),
    onSurfaceVariant = Color(0xFFC4C6D0),
    surfaceDim = Color(0xFF0E0F12),
    surfaceBright = Color(0xFF34363B),
    surfaceContainerLowest = Color(0xFF0A0B0D),
    surfaceContainerLow = Color(0xFF15171B),
    surfaceContainer = Color(0xFF1A1C20),
    surfaceContainerHigh = Color(0xFF23262B),
    surfaceContainerHighest = Color(0xFF2D3036),
    outline = Color(0xFF8E9099),
    outlineVariant = Color(0xFF44474F)
)

private val LauncherShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp)
)

private val BaseTypography = Typography(
    displayLarge = TextStyle(fontSize = 72.sp, lineHeight = 76.sp, fontWeight = FontWeight.Medium, letterSpacing = (-2).sp),
    displayMedium = TextStyle(fontSize = 44.sp, lineHeight = 48.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-1).sp),
    displaySmall = TextStyle(fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.6).sp),
    headlineLarge = TextStyle(fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp),
    headlineSmall = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.1.sp),
    titleSmall = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.1.sp),
    bodyLarge = TextStyle(fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.1.sp),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.1.sp),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.2.sp),
    labelLarge = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.3.sp),
    labelSmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.3.sp)
)

@Composable
fun LauncherTheme(accentIndex: Int, calmMotion: Boolean, wallpaperSeed: Color, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val typography = remember { BaseTypography.withFamily(launcherFontFamily(context)) }
    val dynamicAccent = remember(wallpaperSeed) { accentFromSeed(wallpaperSeed) }
    val accent = if (accentIndex >= Accents.size) dynamicAccent else Accents[accentIndex]
    val scheme = remember(accent) {
        BaseDark.copy(
            primary = accent.primary,
            onPrimary = accent.onPrimary,
            primaryContainer = accent.container,
            onPrimaryContainer = accent.onContainer,
            tertiary = accent.primary,
            onTertiary = accent.onPrimary,
            tertiaryContainer = accent.container,
            onTertiaryContainer = accent.onContainer
        )
    }
    CompositionLocalProvider(LocalCalmMotion provides calmMotion, LocalWallpaperAccent provides dynamicAccent) {
        MaterialTheme(
            colorScheme = scheme,
            shapes = LauncherShapes,
            typography = typography,
            content = content
        )
    }
}
