package dev.expressive.launcher.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Photo
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import android.content.Context
import android.media.AudioManager
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Slider
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import kotlin.math.roundToInt
import dev.expressive.launcher.AppCategories
import dev.expressive.launcher.AppEntry
import dev.expressive.launcher.LauncherPrefs
import kotlinx.coroutines.delay

enum class SettingsPage(val title: String) {
    Root("Settings"),
    Quick("Quick settings"),
    Wallpaper("Wallpaper"),
    Appearance("Appearance"),
    Home("Home screen"),
    Apps("Apps"),
    System("System")
}

sealed interface Sidebar {
    data object Closed : Sidebar
    data class Settings(val page: SettingsPage = SettingsPage.Root) : Sidebar
    data class Options(val app: AppEntry) : Sidebar
    data object Search : Sidebar
    data class Rename(val app: AppEntry) : Sidebar
    data class Categorize(val app: AppEntry) : Sidebar
}

class SidebarActions(
    val navigate: (Sidebar) -> Unit,
    val close: () -> Unit,
    val edit: ((LauncherPrefs) -> LauncherPrefs) -> Unit,
    val selectWallpaper: (String) -> Unit,
    val pickPhoto: () -> Unit,
    val toggleFavorite: (String) -> Unit,
    val open: (AppEntry) -> Unit,
    val appInfo: (AppEntry) -> Unit,
    val uninstall: (AppEntry) -> Unit,
    val system: (String) -> Unit,
    val reset: () -> Unit,
    val rename: (String, String) -> Unit,
    val moveFavorite: (String, Int) -> Unit,
    val assistant: () -> Unit,
    val ambientNow: () -> Unit,
    val setCategory: (String, Int?) -> Unit
)

fun Sidebar.parent(): Sidebar = when (this) {
    is Sidebar.Settings -> if (page == SettingsPage.Root) Sidebar.Closed else Sidebar.Settings(SettingsPage.Root)
    is Sidebar.Rename -> Sidebar.Options(app)
    is Sidebar.Categorize -> Sidebar.Options(app)
    else -> Sidebar.Closed
}

private class WallOption(val id: String, val name: String)

@Composable
fun SidebarHost(
    sidebar: Sidebar,
    prefs: LauncherPrefs,
    managerApps: List<AppEntry>,
    favorites: Set<String>,
    favoriteOrder: List<String>,
    usage: Map<String, Int>,
    recents: List<AppEntry>,
    photo: ImageBitmap?,
    actions: SidebarActions,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxHeight(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 36.dp, bottomStart = 36.dp)
    ) {
        AnimatedContent(
            targetState = sidebar,
            transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(100)) },
            label = "sidebar",
            modifier = Modifier.safeDrawingPadding()
        ) { state ->
            when (state) {
                is Sidebar.Settings -> SettingsPageContent(state.page, prefs, managerApps, photo, actions)
                is Sidebar.Options -> OptionsPage(state.app, prefs, favorites, favoriteOrder, usage, actions)
                Sidebar.Search -> SearchPage(managerApps, recents, prefs, actions)
                is Sidebar.Rename -> RenamePage(state.app, actions)
                is Sidebar.Categorize -> CategoryPage(state.app, actions)
                Sidebar.Closed -> Spacer(Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun rememberFirstFocus(key: Any): FocusRequester {
    val requester = remember(key) { FocusRequester() }
    LaunchedEffect(key) {
        repeat(10) {
            delay(80)
            if (requester.tryFocus()) return@LaunchedEffect
        }
    }
    return requester
}

@Composable
private fun SettingsPageContent(
    page: SettingsPage,
    prefs: LauncherPrefs,
    managerApps: List<AppEntry>,
    photo: ImageBitmap?,
    actions: SidebarActions
) {
    val first = rememberFirstFocus(page)
    val back: (() -> Unit)? = if (page == SettingsPage.Root) null else {
        { actions.navigate(Sidebar.Settings(SettingsPage.Root)) }
    }

    if (page == SettingsPage.Apps) {
        AppsPage(prefs, managerApps, first, back, actions)
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        PageHeader(page.title, back)
        when (page) {
            SettingsPage.Root -> RootPage(prefs, managerApps, first, actions)
            SettingsPage.Quick -> QuickPage(first, actions)
            SettingsPage.Wallpaper -> WallpaperPage(prefs, photo, first, actions)
            SettingsPage.Appearance -> AppearancePage(prefs, first, actions)
            SettingsPage.Home -> HomePage(prefs, first, actions)
            SettingsPage.System -> SystemPage(first, actions)
            SettingsPage.Apps -> Spacer(Modifier)
        }
    }
}

@Composable
private fun RootPage(
    prefs: LauncherPrefs,
    managerApps: List<AppEntry>,
    first: FocusRequester,
    actions: SidebarActions
) {
    val wallpaperName = when (prefs.wallpaper) {
        "photo" -> "Your photo"
        "accent" -> "Match accent"
        else -> WallpaperPresets.firstOrNull { it.id == prefs.wallpaper }?.name ?: "Ink"
    }
    val shown = managerApps.count { it.packageName !in prefs.hidden }
    NavItem("Quick settings", "Volume, assistant, ambient clock", Icons.Rounded.Tune, first) {
        actions.navigate(Sidebar.Settings(SettingsPage.Quick))
    }
    NavItem("Wallpaper", wallpaperName, Icons.Rounded.Photo, null) {
        actions.navigate(Sidebar.Settings(SettingsPage.Wallpaper))
    }
    NavItem("Appearance", "Accent, icon shape, clock", Icons.Rounded.Palette, null) {
        actions.navigate(Sidebar.Settings(SettingsPage.Appearance))
    }
    NavItem("Home screen", "Cards, rows and sorting", Icons.Rounded.Dashboard, null) {
        actions.navigate(Sidebar.Settings(SettingsPage.Home))
    }
    NavItem("Apps", "$shown shown", Icons.Rounded.Apps, null) {
        actions.navigate(Sidebar.Settings(SettingsPage.Apps))
    }
    NavItem("System", "Network, display, default home", Icons.Rounded.Settings, null) {
        actions.navigate(Sidebar.Settings(SettingsPage.System))
    }
    SideItem(
        title = "Reset customization",
        subtitle = "Back to the default look",
        icon = Icons.Rounded.Refresh,
        onClick = actions.reset
    )
}

@Composable
private fun WallpaperPage(
    prefs: LauncherPrefs,
    photo: ImageBitmap?,
    first: FocusRequester,
    actions: SidebarActions
) {
    val options = WallpaperPresets.map { WallOption(it.id, it.name) } +
        WallOption("accent", "Match accent") + WallOption("photo", "Photo")
    SideLabel("Choose")
    options.chunked(2).forEachIndexed { rowIndex, rowItems ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            rowItems.forEachIndexed { index, option ->
                WallpaperTile(
                    option = option,
                    selected = prefs.wallpaper == option.id,
                    photo = photo,
                    modifier = Modifier.weight(1f),
                    requester = if (rowIndex == 0 && index == 0) first else null,
                    onClick = {
                        if (option.id == "photo" && photo == null) actions.pickPhoto() else actions.selectWallpaper(option.id)
                    }
                )
            }
            if (rowItems.size == 1) Spacer(Modifier.weight(1f))
        }
    }
    SideItem(
        title = if (photo == null) "Choose a photo" else "Choose another photo",
        icon = Icons.Rounded.AddPhotoAlternate,
        onClick = actions.pickPhoto
    )
    SideLabel("Dimming")
    ChoiceGroup(listOf("Off", "Soft", "Strong"), prefs.dim, null) { index ->
        actions.edit { it.copy(dim = index) }
    }
}

@Composable
private fun AppearancePage(prefs: LauncherPrefs, first: FocusRequester, actions: SidebarActions) {
    SideLabel("Accent color")
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        (Accents + LocalWallpaperAccent.current).forEachIndexed { index, accent ->
            AccentDot(
                accent = accent,
                selected = index == prefs.accent,
                modifier = Modifier.weight(1f),
                requester = if (index == 0) first else null
            ) { actions.edit { it.copy(accent = index) } }
        }
    }
    SideLabel("Icon shape")
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        IconShapes.forEachIndexed { index, shape ->
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val selected = index == prefs.iconShape
                FocusSurface(
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                    shapeAt = shape.shapeAt,
                    containerColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                    focusedContainerColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                    ringColor = MaterialTheme.colorScheme.onSurface,
                    ringWidth = 2.dp,
                    focusedScale = 1.08f,
                    onClick = { actions.edit { it.copy(iconShape = index) } }
                ) {
                    if (selected) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = "Selected",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.align(Alignment.Center).size(22.dp)
                        )
                    }
                }
                Text(
                    text = shape.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }
    SideLabel("Motion")
    ChoiceGroup(listOf("Expressive", "Calm"), if (prefs.calmMotion) 1 else 0, null) { index ->
        actions.edit { it.copy(calmMotion = index == 1) }
    }
    SideLabel("Clock")
    ChoiceGroup(listOf("Large", "Compact", "Off"), prefs.clockMode, null) { index ->
        actions.edit { it.copy(clockMode = index) }
    }
    SideLabel("Time format")
    ChoiceGroup(listOf("System", "12-hour", "24-hour"), prefs.timeFormat, null) { index ->
        actions.edit { it.copy(timeFormat = index) }
    }
    SwitchItem("Show date", null, prefs.showDate, null) {
        actions.edit { it.copy(showDate = !it.showDate) }
    }
    SwitchItem("Show greeting", null, prefs.showGreeting, null) {
        actions.edit { it.copy(showGreeting = !it.showGreeting) }
    }
    SwitchItem("Status icons", "Network and battery in the header", prefs.showStatus, null) {
        actions.edit { it.copy(showStatus = !it.showStatus) }
    }
    SideLabel("Ambient clock after")
    ChoiceGroup(listOf("Off", "2 min", "5 min", "10 min"), prefs.ambient, null) { index ->
        actions.edit { it.copy(ambient = index) }
    }
}

@Composable
private fun HomePage(prefs: LauncherPrefs, first: FocusRequester, actions: SidebarActions) {
    SideLabel("Card style")
    ChoiceGroup(listOf("Banner", "Icon"), prefs.cardStyle, first) { index ->
        actions.edit { it.copy(cardStyle = index) }
    }
    SideLabel("Card size")
    ChoiceGroup(listOf("Small", "Medium", "Large"), prefs.cardSize, null) { index ->
        actions.edit { it.copy(cardSize = index) }
    }
    SwitchItem("Show app names", null, prefs.showLabels, null) {
        actions.edit { it.copy(showLabels = !it.showLabels) }
    }
    SideLabel("Rows")
    SwitchItem("Favorites", "Apps you starred", prefs.showFavorites, null) {
        actions.edit { it.copy(showFavorites = !it.showFavorites) }
    }
    SwitchItem("Recent", "Apps you opened last", prefs.showRecent, null) {
        actions.edit { it.copy(showRecent = !it.showRecent) }
    }
    SwitchItem("Most used", "Apps you open the most", prefs.showMostUsed, null) {
        actions.edit { it.copy(showMostUsed = !it.showMostUsed) }
    }
    SideLabel("Sort all apps")
    ChoiceGroup(listOf("A to Z", "Newest", "Most used"), prefs.sort, null) { index ->
        actions.edit { it.copy(sort = index) }
    }
    SideLabel("All apps")
    SwitchItem("Show the All apps row", null, prefs.showAllRow, null) {
        actions.edit { it.copy(showAllRow = !it.showAllRow) }
    }
    SwitchItem("Group by category", "Games, video, music and more", prefs.groupCategories, null) {
        actions.edit { it.copy(groupCategories = !it.groupCategories) }
    }
}

@Composable
private fun SystemPage(first: FocusRequester, actions: SidebarActions) {
    NavItem("Wi-Fi and network", null, Icons.Rounded.Wifi, first) {
        actions.system(android.provider.Settings.ACTION_WIFI_SETTINGS)
    }
    NavItem("Display", null, Icons.Rounded.Tv, null) {
        actions.system(android.provider.Settings.ACTION_DISPLAY_SETTINGS)
    }
    NavItem("Sound", null, Icons.Rounded.VolumeUp, null) {
        actions.system(android.provider.Settings.ACTION_SOUND_SETTINGS)
    }
    NavItem("Bluetooth", null, Icons.Rounded.Bluetooth, null) {
        actions.system(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)
    }
    NavItem("Default home app", "Make this launcher your home", Icons.Rounded.Home, null) {
        actions.system(android.provider.Settings.ACTION_HOME_SETTINGS)
    }
    NavItem("Manage apps", null, Icons.Rounded.Apps, null) {
        actions.system(android.provider.Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS)
    }
    NavItem("All system settings", null, Icons.Rounded.Settings, null) {
        actions.system(android.provider.Settings.ACTION_SETTINGS)
    }
}

@Composable
private fun AppsPage(
    prefs: LauncherPrefs,
    managerApps: List<AppEntry>,
    first: FocusRequester,
    back: (() -> Unit)?,
    actions: SidebarActions
) {
    val shape = IconShapes.getOrElse(prefs.iconShape) { IconShapes.first() }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        item { PageHeader("Apps", back) }
        item {
            SwitchItem("Show system apps", "Apps that came with your device", prefs.showSystem, first) {
                actions.edit { it.copy(showSystem = !it.showSystem) }
            }
        }
        item { SideLabel("Shown on the home screen") }
        items(managerApps, key = { it.packageName }) { app ->
            val shown = app.packageName !in prefs.hidden
            SideItem(
                title = app.label,
                leading = {
                    Image(
                        bitmap = app.icon,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp).clip(shape.still)
                    )
                },
                trailing = {
                    Switch(
                        checked = shown,
                        onCheckedChange = null,
                        thumbContent = {
                            Icon(
                                imageVector = if (shown) Icons.Rounded.Check else Icons.Rounded.Close,
                                contentDescription = null,
                                modifier = Modifier.size(SwitchDefaults.IconSize)
                            )
                        }
                    )
                },
                onClick = {
                    actions.edit {
                        it.copy(hidden = if (shown) it.hidden + app.packageName else it.hidden - app.packageName)
                    }
                }
            )
        }
    }
}

@Composable
private fun OptionsPage(
    app: AppEntry,
    prefs: LauncherPrefs,
    favorites: Set<String>,
    favoriteOrder: List<String>,
    usage: Map<String, Int>,
    actions: SidebarActions
) {
    val favoriteIndex = favoriteOrder.indexOf(app.packageName)
    val opens = usage[app.packageName] ?: 0
    val first = rememberFirstFocus(app.packageName)
    val shape = IconShapes.getOrElse(prefs.iconShape) { IconShapes.first() }
    val isFavorite = app.packageName in favorites
    val meta = listOfNotNull(
        if (app.isTv) "Made for TV" else null,
        if (isFavorite) "Favorite" else null,
        if (app.isSystem) "System app" else null,
        if (opens > 0) "Opened $opens times" else null
    ).joinToString(" · ").ifEmpty { "Installed app" }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp, start = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Image(
                bitmap = app.icon,
                contentDescription = null,
                modifier = Modifier.size(64.dp).clip(shape.still)
            )
            Column {
                Text(
                    text = app.label,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        SideItem(
            title = "Open",
            icon = Icons.Rounded.PlayArrow,
            requester = first,
            onClick = {
                actions.close()
                actions.open(app)
            }
        )
        SideItem(
            title = if (isFavorite) "Remove from favorites" else "Add to favorites",
            icon = if (isFavorite) Icons.Rounded.StarBorder else Icons.Rounded.Star,
            onClick = {
                actions.toggleFavorite(app.packageName)
                actions.close()
            }
        )
        if (isFavorite) {
            SideItem(
                title = "Move earlier",
                icon = Icons.Rounded.ChevronLeft,
                enabled = favoriteIndex > 0,
                onClick = { actions.moveFavorite(app.packageName, -1) }
            )
            SideItem(
                title = "Move later",
                icon = Icons.Rounded.ChevronRight,
                enabled = favoriteIndex in 0 until favoriteOrder.lastIndex,
                onClick = { actions.moveFavorite(app.packageName, 1) }
            )
        }
        SideItem(
            title = "Rename",
            subtitle = if (app.label != app.systemLabel) "Originally ${app.systemLabel}" else null,
            icon = Icons.Rounded.Edit,
            onClick = { actions.navigate(Sidebar.Rename(app)) }
        )
        SideItem(
            title = "Category",
            subtitle = AppCategories.name(app.category) + if (app.category != app.autoCategory) " (custom)" else " (automatic)",
            icon = Icons.Rounded.Apps,
            onClick = { actions.navigate(Sidebar.Categorize(app)) }
        )
        SideItem(
            title = "Hide from home screen",
            subtitle = "Bring it back from Settings, Apps",
            icon = Icons.Rounded.VisibilityOff,
            onClick = {
                actions.edit { it.copy(hidden = it.hidden + app.packageName) }
                actions.close()
            }
        )
        SideItem(
            title = "App info",
            icon = Icons.Rounded.Info,
            onClick = {
                actions.close()
                actions.appInfo(app)
            }
        )
        if (!app.isSystem) {
            SideItem(
                title = "Uninstall",
                icon = Icons.Rounded.Delete,
                tint = MaterialTheme.colorScheme.error,
                onClick = {
                    actions.close()
                    actions.uninstall(app)
                }
            )
        }
    }
}

@Composable
private fun PageHeader(title: String, onBack: (() -> Unit)?) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (onBack != null) {
            val shapeAt = remember { percentMorph(50, 30) }
            FocusSurface(
                modifier = Modifier.size(44.dp),
                shapeAt = shapeAt,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ringColor = MaterialTheme.colorScheme.primary,
                ringWidth = 2.dp,
                onClick = onBack
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.align(Alignment.Center).size(22.dp)
                )
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun SideLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 18.dp, bottom = 6.dp)
    )
}

@Composable
private fun SideItem(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    leading: (@Composable () -> Unit)? = null,
    tint: Color? = null,
    requester: FocusRequester? = null,
    trailing: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val shapeAt = remember { roundedMorph(18.dp, 26.dp) }
    FocusSurface(
        modifier = modifier.fillMaxWidth(),
        shapeAt = shapeAt,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        focusedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
        ringColor = MaterialTheme.colorScheme.primary,
        ringWidth = 0.dp,
        focusedScale = 1f,
        focusRequester = requester,
        onClick = { if (enabled) onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { alpha = if (enabled) 1f else 0.4f }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (leading != null) {
                leading()
            } else if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint ?: MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = tint ?: MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (trailing != null) trailing()
        }
    }
}

@Composable
private fun NavItem(
    title: String,
    subtitle: String?,
    icon: ImageVector,
    requester: FocusRequester?,
    onClick: () -> Unit
) {
    SideItem(
        title = title,
        subtitle = subtitle,
        icon = icon,
        requester = requester,
        trailing = {
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
        },
        onClick = onClick
    )
}

@Composable
private fun SwitchItem(
    title: String,
    subtitle: String?,
    checked: Boolean,
    requester: FocusRequester?,
    onToggle: () -> Unit
) {
    SideItem(
        title = title,
        subtitle = subtitle,
        requester = requester,
        trailing = {
            Switch(
                checked = checked,
                onCheckedChange = null,
                thumbContent = {
                    Icon(
                        imageVector = if (checked) Icons.Rounded.Check else Icons.Rounded.Close,
                        contentDescription = null,
                        modifier = Modifier.size(SwitchDefaults.IconSize)
                    )
                }
            )
        },
        onClick = onToggle
    )
}

@Composable
private fun ChoiceGroup(
    options: List<String>,
    selected: Int,
    requester: FocusRequester?,
    onSelect: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        options.forEachIndexed { index, label ->
            Segment(
                label = label,
                selected = index == selected,
                first = index == 0,
                last = index == options.lastIndex,
                modifier = Modifier.weight(1f),
                requester = if (index == 0) requester else null
            ) { onSelect(index) }
        }
    }
}

@Composable
private fun Segment(
    label: String,
    selected: Boolean,
    first: Boolean,
    last: Boolean,
    modifier: Modifier,
    requester: FocusRequester?,
    onClick: () -> Unit
) {
    val progress = animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = spring<Float>(dampingRatio = 0.65f, stiffness = Spring.StiffnessMediumLow),
        label = "segment"
    )
    val shapeAt: ShapeAt = remember(first, last) {
        { _: Float, density: Density ->
            with(density) {
                val s = progress.value.coerceIn(0f, 1f)
                val pill = 26.dp.toPx()
                val inner = lerp(5.dp.toPx(), pill, s)
                val outer = lerp(18.dp.toPx(), pill, s)
                RoundedCornerShape(
                    topStart = if (first) outer else inner,
                    topEnd = if (last) outer else inner,
                    bottomEnd = if (last) outer else inner,
                    bottomStart = if (first) outer else inner
                )
            }
        }
    }
    FocusSurface(
        modifier = modifier.height(52.dp),
        shapeAt = shapeAt,
        containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        focusedContainerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
        ringColor = MaterialTheme.colorScheme.primary,
        ringWidth = 2.dp,
        focusedScale = 1.03f,
        focusRequester = requester,
        onClick = onClick
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

@Composable
private fun AccentDot(
    accent: Accent,
    selected: Boolean,
    modifier: Modifier,
    requester: FocusRequester?,
    onClick: () -> Unit
) {
    val cookie = remember { cookieMorph(9, 0.07f, 0.1f, 1.1f) }
    val round = remember { percentMorph(50, 32) }
    FocusSurface(
        modifier = modifier.aspectRatio(1f),
        shapeAt = if (selected) cookie else round,
        containerColor = accent.primary,
        ringColor = MaterialTheme.colorScheme.onSurface,
        ringWidth = 2.dp,
        focusedScale = 1.1f,
        focusRequester = requester,
        onClick = onClick
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = "Selected",
                tint = accent.onPrimary,
                modifier = Modifier.align(Alignment.Center).size(22.dp)
            )
        }
    }
}

@Composable
private fun WallpaperTile(
    option: WallOption,
    selected: Boolean,
    photo: ImageBitmap?,
    modifier: Modifier,
    requester: FocusRequester?,
    onClick: () -> Unit
) {
    val shapeAt = remember { roundedMorph(20.dp, 28.dp) }
    val accentContainer = MaterialTheme.colorScheme.primaryContainer
    Column(modifier = modifier) {
        FocusSurface(
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 10f),
            shapeAt = shapeAt,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ringColor = MaterialTheme.colorScheme.primary,
            focusedScale = 1.04f,
            focusRequester = requester,
            onClick = onClick
        ) {
            if (option.id == "photo") {
                if (photo != null) {
                    Image(
                        bitmap = photo,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.AddPhotoAlternate,
                        contentDescription = "Choose a photo",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center).size(30.dp)
                    )
                }
            } else {
                PresetWallpaper(wallpaperPreset(option.id, accentContainer), Modifier.fillMaxSize())
            }
            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }
        Text(
            text = option.name,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 8.dp, start = 4.dp)
        )
    }
}

@Composable
private fun QuickPage(first: FocusRequester, actions: SidebarActions) {
    val context = LocalContext.current
    val audio = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var volume by remember { mutableFloatStateOf(audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()) }

    SideLabel("Media volume")
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = Icons.Rounded.VolumeUp,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Slider(
            value = volume,
            onValueChange = {
                volume = it
                runCatching { audio.setStreamVolume(AudioManager.STREAM_MUSIC, it.roundToInt(), 0) }
            },
            valueRange = 0f..maxVolume.toFloat(),
            steps = (maxVolume - 1).coerceAtLeast(0),
            modifier = Modifier.weight(1f).focusRequester(first)
        )
        Text(
            text = volume.roundToInt().toString(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
    SideLabel("Shortcuts")
    NavItem("Assistant", "Ask or search by voice", Icons.Rounded.Mic, null) { actions.assistant() }
    NavItem("Ambient clock", "Dim the screen and show the time", Icons.Rounded.Schedule, null) { actions.ambientNow() }
    NavItem("Wi-Fi and network", null, Icons.Rounded.Wifi, null) {
        actions.system(android.provider.Settings.ACTION_WIFI_SETTINGS)
    }
    NavItem("Bluetooth", null, Icons.Rounded.Bluetooth, null) {
        actions.system(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)
    }
    NavItem("Display", null, Icons.Rounded.Tv, null) {
        actions.system(android.provider.Settings.ACTION_DISPLAY_SETTINGS)
    }
}

@Composable
private fun fieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    disabledIndicatorColor = Color.Transparent
)

@Composable
private fun SearchPage(
    apps: List<AppEntry>,
    recents: List<AppEntry>,
    prefs: LauncherPrefs,
    actions: SidebarActions
) {
    val shape = IconShapes.getOrElse(prefs.iconShape) { IconShapes.first() }
    var query by remember { mutableStateOf("") }
    val first = rememberFirstFocus("search")
    val results = remember(query, apps, recents) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) {
            recents.take(8)
        } else {
            apps.filter { it.label.lowercase().contains(q) || AppCategories.name(it.category).lowercase().contains(q) }
                .sortedWith(
                    compareByDescending<AppEntry> { it.label.lowercase().startsWith(q) }
                        .thenBy { it.label.lowercase() }
                )
                .take(40)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        PageHeader("Search", actions.close)
        TextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().focusRequester(first),
            singleLine = true,
            shape = RoundedCornerShape(26.dp),
            placeholder = { Text("Search apps") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            colors = fieldColors(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    results.firstOrNull()?.let {
                        actions.close()
                        actions.open(it)
                    }
                }
            )
        )
        SideLabel(if (query.isBlank()) "Recent" else "Results")
        if (results.isEmpty()) {
            Text(
                text = if (query.isBlank()) "Nothing opened yet." else "No apps match that.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(results, key = { it.packageName }) { app ->
                SideItem(
                    title = app.label,
                    leading = {
                        Image(
                            bitmap = app.icon,
                            contentDescription = null,
                            modifier = Modifier.size(36.dp).clip(shape.still)
                        )
                    },
                    onClick = {
                        actions.close()
                        actions.open(app)
                    }
                )
            }
        }
    }
}

@Composable
private fun RenamePage(app: AppEntry, actions: SidebarActions) {
    var text by remember(app.packageName) { mutableStateOf(app.label) }
    val first = rememberFirstFocus("rename:" + app.packageName)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        PageHeader("Rename") { actions.navigate(Sidebar.Options(app)) }
        TextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth().focusRequester(first),
            singleLine = true,
            shape = RoundedCornerShape(26.dp),
            colors = fieldColors(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = {
                    actions.rename(app.packageName, text)
                    actions.close()
                }
            )
        )
        Spacer(Modifier.height(8.dp))
        SideItem(
            title = "Save",
            icon = Icons.Rounded.Check,
            onClick = {
                actions.rename(app.packageName, text)
                actions.close()
            }
        )
        SideItem(
            title = "Use the original name",
            subtitle = app.systemLabel,
            icon = Icons.Rounded.Refresh,
            onClick = {
                actions.rename(app.packageName, "")
                actions.close()
            }
        )
    }
}

@Composable
private fun CheckMark() {
    Icon(
        imageVector = Icons.Rounded.Check,
        contentDescription = "Selected",
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(24.dp)
    )
}

@Composable
private fun CategoryPage(app: AppEntry, actions: SidebarActions) {
    val first = rememberFirstFocus("category:" + app.packageName)
    val custom = app.category != app.autoCategory
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        PageHeader("Category") { actions.navigate(Sidebar.Options(app)) }
        SideItem(
            title = "Automatic",
            subtitle = AppCategories.name(app.autoCategory),
            requester = first,
            trailing = if (!custom) {
                { CheckMark() }
            } else {
                null
            },
            onClick = {
                actions.setCategory(app.packageName, null)
                actions.close()
            }
        )
        AppCategories.all.forEach { category ->
            SideItem(
                title = category.name,
                trailing = if (custom && category.id == app.category) {
                    { CheckMark() }
                } else {
                    null
                },
                onClick = {
                    actions.setCategory(app.packageName, if (category.id == app.autoCategory) null else category.id)
                    actions.close()
                }
            )
        }
    }
}
