package dev.expressive.launcher

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap

@Immutable
data class AppEntry(
    val packageName: String,
    val activityName: String,
    val label: String,
    val icon: ImageBitmap,
    val banner: ImageBitmap?,
    val isTv: Boolean,
    val isSystem: Boolean
)
