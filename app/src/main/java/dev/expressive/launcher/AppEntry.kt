package dev.expressive.launcher

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap

@Immutable
data class AppEntry(
    val packageName: String,
    val activityName: String,
    val label: String,
    val systemLabel: String,
    val icon: ImageBitmap,
    val banner: ImageBitmap?,
    val tint: Color,
    val category: Int,
    val autoCategory: Int,
    val isTv: Boolean,
    val isSystem: Boolean,
    val installedAt: Long
)
