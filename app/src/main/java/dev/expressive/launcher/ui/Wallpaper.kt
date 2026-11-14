package dev.expressive.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale

@Immutable
data class WallpaperPreset(
    val id: String,
    val name: String,
    val from: Color,
    val to: Color
)

val WallpaperPresets = listOf(
    WallpaperPreset("ink", "Ink", Color(0xFF0E1014), Color(0xFF1C2330)),
    WallpaperPreset("dune", "Dune", Color(0xFF1B1713), Color(0xFF3B2D22)),
    WallpaperPreset("fjord", "Fjord", Color(0xFF0A1820), Color(0xFF1D3B4B)),
    WallpaperPreset("moss", "Moss", Color(0xFF0F150E), Color(0xFF263622)),
    WallpaperPreset("plum", "Plum", Color(0xFF181019), Color(0xFF37243F)),
    WallpaperPreset("mono", "Mono", Color(0xFF0B0B0B), Color(0xFF1F1F1F))
)

fun wallpaperPreset(id: String, accentContainer: Color): WallpaperPreset {
    if (id == "accent") {
        val base = Color(0xFF0E0F12)
        return WallpaperPreset("accent", "Match accent", base, lerp(base, accentContainer, 0.85f))
    }
    return WallpaperPresets.firstOrNull { it.id == id } ?: WallpaperPresets.first()
}

@Composable
fun PresetWallpaper(preset: WallpaperPreset, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(preset.from, preset.to),
                start = Offset.Zero,
                end = Offset(size.width, size.height)
            )
        )
    }
}

@Composable
fun WallpaperLayer(
    wallpaperId: String,
    photo: ImageBitmap?,
    dim: Int,
    accentContainer: Color,
    modifier: Modifier = Modifier
) {
    val dimAlpha = when (dim) {
        0 -> 0f
        1 -> 0.22f
        else -> 0.45f
    }
    Box(modifier) {
        if (wallpaperId == "photo" && photo != null) {
            Image(
                bitmap = photo,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            PresetWallpaper(wallpaperPreset(wallpaperId, accentContainer), Modifier.fillMaxSize())
        }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = dimAlpha)))
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.5f to Color.Transparent,
                    1f to Color(0x990E0F12)
                )
            )
        )
    }
}
