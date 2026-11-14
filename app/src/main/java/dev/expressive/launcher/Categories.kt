package dev.expressive.launcher

data class AppCategory(val id: Int, val name: String)

object AppCategories {
    val all = listOf(
        AppCategory(2, "Video"),
        AppCategory(0, "Games"),
        AppCategory(1, "Music"),
        AppCategory(13, "Kids"),
        AppCategory(12, "Sports"),
        AppCategory(5, "News"),
        AppCategory(4, "Social"),
        AppCategory(3, "Photos"),
        AppCategory(11, "Education"),
        AppCategory(10, "Shopping"),
        AppCategory(14, "Health"),
        AppCategory(6, "Maps"),
        AppCategory(16, "Web"),
        AppCategory(7, "Productivity"),
        AppCategory(15, "Tools"),
        AppCategory(-1, "Other")
    )

    fun name(id: Int): String = all.firstOrNull { it.id == id }?.name ?: "Other"

    private val rules: List<Pair<Int, List<String>>> = listOf(
        13 to listOf("kids", "children", "cartoon", "nickelodeon", "sesame"),
        12 to listOf("sport", "espn", "nfl", "nba", "nhl", "mlb", "soccer", "football", "fifa", "dazn", "golf"),
        0 to listOf("game", "gaming", "steam", "xbox", "playstation", "roblox", "minecraft", "arcade", "puzzle", "chess", "casino"),
        1 to listOf("music", "spotify", "deezer", "tidal", "soundcloud", "podcast", "radio", "audio", "karaoke", "shazam", "pandora"),
        2 to listOf(
            "netflix", "youtube", "hulu", "disney", "primevideo", "amazon.avod", "hbo", "plex", "kodi", "twitch",
            "vimeo", "crunchyroll", "paramount", "peacock", "video", "movie", "cinema", "stream", "iptv", "vlc",
            "tubi", "pluto", "anime"
        ),
        5 to listOf("news", "cnn", "bbc", "reuters", "bloomberg", "nytimes"),
        4 to listOf("facebook", "instagram", "twitter", "tiktok", "whatsapp", "telegram", "messenger", "discord", "snapchat", "reddit", "linkedin", "social"),
        10 to listOf("shop", "ebay", "aliexpress", "etsy", "walmart", "temu"),
        11 to listOf("learn", "education", "school", "duolingo", "khan", "course", "study", "classroom", "tutor"),
        14 to listOf("health", "fitness", "workout", "yoga", "meditat", "medical", "sleep"),
        6 to listOf("maps", "navigation", "waze", "transit"),
        15 to listOf("file", "manager", "cleaner", "settings", "tools", "utility", "calculator", "clock", "weather", "wifi", "vpn", "remote", "cast", "mirror", "backup", "usb", "speedtest", "keyboard", "terminal"),
        16 to listOf("browser", "chrome", "firefox", "opera", "puffin", "brave", "duckduckgo"),
        3 to listOf("photo", "gallery", "camera", "image", "picture"),
        7 to listOf("office", "docs", "sheets", "slides", "notes", "mail", "gmail", "outlook", "drive", "dropbox", "calendar")
    )

    fun auto(apiCategory: Int, label: String, packageName: String): Int {
        if (apiCategory in 0..7) return apiCategory
        val text = "$packageName $label".lowercase()
        for ((id, tokens) in rules) {
            if (tokens.any { text.contains(it) }) return id
        }
        return -1
    }
}
