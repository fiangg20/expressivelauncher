package dev.expressive.launcher

data class LauncherPrefs(
    val wallpaper: String = "ink",
    val dim: Int = 1,
    val accent: Int = 0,
    val iconShape: Int = 0,
    val calmMotion: Boolean = false,
    val clockMode: Int = 0,
    val timeFormat: Int = 0,
    val showDate: Boolean = true,
    val showGreeting: Boolean = false,
    val cardStyle: Int = 0,
    val cardSize: Int = 1,
    val showLabels: Boolean = true,
    val showFavorites: Boolean = true,
    val showRecent: Boolean = true,
    val showMostUsed: Boolean = false,
    val sort: Int = 0,
    val showSystem: Boolean = false,
    val hidden: Set<String> = emptySet()
)
