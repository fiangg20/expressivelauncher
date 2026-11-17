package dev.expressive.launcher

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap

val AppEntry.synthetic: Boolean get() = packageName.contains(':')

fun syntheticEntry(packageName: String, label: String, icon: ImageBitmap, tint: Color): AppEntry = AppEntry(
    packageName = packageName,
    activityName = "",
    label = label,
    systemLabel = label,
    icon = icon,
    banner = null,
    tint = tint,
    category = -1,
    autoCategory = -1,
    isTv = false,
    isSystem = false,
    installedAt = 0L
)

data class Scene(val name: String, val packageName: String, val volume: Int, val dim: Int)
