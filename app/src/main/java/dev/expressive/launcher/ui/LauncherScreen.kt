package dev.expressive.launcher.ui

import android.content.pm.PackageManager
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.expressive.launcher.AppCategories
import dev.expressive.launcher.AppEntry
import dev.expressive.launcher.LauncherPrefs
import dev.expressive.launcher.LauncherViewModel
import dev.expressive.launcher.launchApp
import dev.expressive.launcher.launchAssistant
import dev.expressive.launcher.openAppInfo
import dev.expressive.launcher.openSystemSettings
import dev.expressive.launcher.toast
import dev.expressive.launcher.uninstallApp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class FocusRegistry {
    val map = HashMap<String, FocusRequester>()
    var last: String? = null
}

private class SidebarMemory(var value: Sidebar)

@Immutable
private data class Section(val id: String, val title: String, val apps: List<AppEntry>)

@Immutable
private data class ClockText(
    val time: String,
    val period: String,
    val date: String,
    val greeting: String
)

private fun categorySections(apps: List<AppEntry>): List<Section> {
    val grouped = apps.groupBy { app -> if (AppCategories.all.any { it.id == app.category }) app.category else -1 }
    return AppCategories.all.mapNotNull { category ->
        grouped[category.id]?.takeIf { it.isNotEmpty() }?.let { Section("cat:${category.id}", category.name, it) }
    }
}

@Composable
fun LauncherScreen(viewModel: LauncherViewModel) {
    val context = LocalContext.current
    val apps by viewModel.apps.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val recents by viewModel.recents.collectAsStateWithLifecycle()
    val usage by viewModel.usage.collectAsStateWithLifecycle()
    val names by viewModel.names.collectAsStateWithLifecycle()
    val customCategories by viewModel.categories.collectAsStateWithLifecycle()
    val quickSignal by viewModel.quickSignal.collectAsStateWithLifecycle()
    val ambient by viewModel.ambient.collectAsStateWithLifecycle()
    val homeSignal by viewModel.homeSignal.collectAsStateWithLifecycle()
    val prefs by viewModel.settings.collectAsStateWithLifecycle()
    val photo by viewModel.photo.collectAsStateWithLifecycle()
    val status = rememberStatus()

    val isTv = remember { context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) }
    val iconShape = IconShapes.getOrElse(prefs.iconShape) { IconShapes.first() }

    val named = remember(apps, names, customCategories) {
        if (names.isEmpty() && customCategories.isEmpty()) {
            apps
        } else {
            apps.map { app ->
                val customName = names[app.packageName]
                val customCategory = customCategories[app.packageName]
                app.copy(
                    label = if (customName.isNullOrBlank()) app.label else customName,
                    category = customCategory ?: app.autoCategory
                )
            }
        }
    }
    val managerApps = remember(named, prefs.showSystem) { named.filter { prefs.showSystem || !it.isSystem } }
    val visible = remember(managerApps, prefs.hidden) { managerApps.filter { it.packageName !in prefs.hidden } }
    val byPackage = remember(visible) { visible.associateBy { it.packageName } }
    val allApps = remember(visible, prefs.sort, usage) {
        when (prefs.sort) {
            1 -> visible.sortedByDescending { it.installedAt }
            2 -> visible.sortedWith(
                compareByDescending<AppEntry> { usage[it.packageName] ?: 0 }.thenBy { it.label.lowercase() }
            )
            else -> visible.sortedBy { it.label.lowercase() }
        }
    }
    val favoriteApps = remember(byPackage, favorites) { favorites.mapNotNull { byPackage[it] } }
    val recentApps = remember(byPackage, recents) { recents.mapNotNull { byPackage[it] } }
    val searchRecents = remember(managerApps, recents) {
        val lookup = managerApps.associateBy { it.packageName }
        recents.mapNotNull { lookup[it] }
    }
    val mostUsedApps = remember(byPackage, usage) {
        usage.entries
            .filter { it.value > 0 && it.key in byPackage }
            .sortedByDescending { it.value }
            .take(12)
            .mapNotNull { byPackage[it.key] }
    }
    val favoriteSet = remember(favorites) { favorites.toSet() }
    val sections = remember(
        prefs.showFavorites, prefs.showRecent, prefs.showMostUsed, prefs.showAllRow, prefs.groupCategories,
        favoriteApps, recentApps, mostUsedApps, allApps
    ) {
        buildList {
            if (prefs.showFavorites && favoriteApps.isNotEmpty()) add(Section("favorites", "Favorites", favoriteApps))
            if (prefs.showRecent && recentApps.isNotEmpty()) add(Section("recent", "Recent", recentApps))
            if (prefs.showMostUsed && mostUsedApps.isNotEmpty()) add(Section("most", "Most used", mostUsedApps))
            if (prefs.showAllRow && allApps.isNotEmpty()) {
                if (prefs.groupCategories) addAll(categorySections(allApps)) else add(Section("all", "All apps", allApps))
            }
        }
    }
    val firstKey = sections.firstOrNull()?.let { "${it.id}:${it.apps.first().packageName}" }

    val focused = remember { mutableStateOf<AppEntry?>(null) }
    var sidebar by remember { mutableStateOf<Sidebar>(Sidebar.Closed) }
    val memory = remember { SidebarMemory(Sidebar.Settings()) }
    if (sidebar !is Sidebar.Closed) memory.value = sidebar
    val sidebarOpen = sidebar !is Sidebar.Closed
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val registry = remember { FocusRegistry() }
    val initialDone = remember { booleanArrayOf(false) }
    val depth = animateFloatAsState(
        targetValue = if (sidebarOpen) 1f else 0f,
        animationSpec = spring<Float>(dampingRatio = 0.9f, stiffness = Spring.StiffnessMedium),
        label = "depth"
    )

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            viewModel.importPhoto(uri) { ok -> if (!ok) context.toast("Could not use that photo") }
        }
    }

    val now by produceState(initialValue = System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(60_000L - value % 60_000L)
        }
    }
    val systemIs24 = remember { DateFormat.is24HourFormat(context) }
    val use24 = when (prefs.timeFormat) {
        1 -> false
        2 -> true
        else -> systemIs24
    }
    val clock = remember(now, use24) {
        val date = Date(now)
        val hour = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY)
        ClockText(
            time = SimpleDateFormat(if (use24) "HH:mm" else "h:mm", Locale.getDefault()).format(date),
            period = if (use24) "" else SimpleDateFormat("a", Locale.getDefault()).format(date),
            date = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(date),
            greeting = when {
                hour < 5 -> "Late night"
                hour < 12 -> "Good morning"
                hour < 18 -> "Good afternoon"
                else -> "Good evening"
            }
        )
    }

    val open: (AppEntry) -> Unit = { app ->
        viewModel.markLaunched(app.packageName)
        context.launchApp(app)
    }

    val actions = SidebarActions(
        navigate = { sidebar = it },
        close = { sidebar = Sidebar.Closed },
        edit = { viewModel.edit(it) },
        selectWallpaper = { viewModel.selectWallpaper(it) },
        pickPhoto = {
            runCatching { photoPicker.launch("image/*") }
                .onFailure { context.toast("No photo picker on this device") }
        },
        toggleFavorite = { viewModel.toggleFavorite(it) },
        open = open,
        appInfo = { context.openAppInfo(it.packageName) },
        uninstall = { context.uninstallApp(it.packageName) },
        system = { context.openSystemSettings(it) },
        reset = {
            viewModel.resetCustomization()
            context.toast("Customization reset")
        },
        rename = { packageName, name -> viewModel.rename(packageName, name) },
        moveFavorite = { packageName, delta -> viewModel.moveFavorite(packageName, delta) },
        assistant = { context.launchAssistant() },
        ambientNow = {
            sidebar = Sidebar.Closed
            viewModel.startAmbient()
        },
        setCategory = { packageName, id -> viewModel.setCategory(packageName, id) }
    )

    LaunchedEffect(quickSignal) {
        if (quickSignal > 0) {
            val current = sidebar
            sidebar = if (current is Sidebar.Settings && current.page == SettingsPage.Quick) {
                Sidebar.Closed
            } else {
                Sidebar.Settings(SettingsPage.Quick)
            }
        }
    }

    LaunchedEffect(homeSignal) {
        if (homeSignal > 0) {
            sidebar = Sidebar.Closed
            listState.animateScrollToItem(0)
        }
    }

    LaunchedEffect(firstKey) {
        val key = firstKey ?: return@LaunchedEffect
        if (!isTv || initialDone[0]) return@LaunchedEffect
        repeat(12) {
            delay(80)
            if (registry.map[key]?.tryFocus() == true) {
                initialDone[0] = true
                return@LaunchedEffect
            }
        }
    }

    LaunchedEffect(sidebarOpen) {
        if (!sidebarOpen && isTv) {
            val key = registry.last ?: return@LaunchedEffect
            repeat(8) {
                delay(100)
                if (registry.map[key]?.tryFocus() == true) return@LaunchedEffect
            }
        }
    }

    BackHandler(enabled = !sidebarOpen) {
        scope.launch { listState.animateScrollToItem(0) }
    }
    BackHandler(enabled = sidebarOpen) {
        sidebar = sidebar.parent()
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0E0F12))
    ) {
        val compact = maxWidth < 600.dp
        val pad = if (compact) 20.dp else 56.dp
        val scale = when (prefs.cardSize) {
            0 -> 0.86f
            2 -> 1.18f
            else -> 1f
        }
        val baseWidth = if (compact) 150.dp else maxWidth * 0.165f
        val bannerStyle = prefs.cardStyle == 0
        val itemWidth = if (bannerStyle) baseWidth * scale else baseWidth * 0.52f * scale
        val sidebarWidth = if (compact) maxWidth * 0.88f else 440.dp

        WallpaperLayer(
            wallpaperId = prefs.wallpaper,
            photo = photo,
            dim = prefs.dim,
            accentContainer = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.fillMaxSize()
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .focusProperties { canFocus = !sidebarOpen }
                .graphicsLayer {
                    val s = 1f - 0.03f * depth.value
                    scaleX = s
                    scaleY = s
                    transformOrigin = TransformOrigin(0f, 0.5f)
                }
        ) {
            Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
                Header(
                    clock = clock,
                    prefs = prefs,
                    compact = compact,
                    pad = pad,
                    focused = focused,
                    favorites = favoriteSet,
                    hint = if (isTv) "Hold OK for options" else "Long press for options",
                    iconShape = iconShape,
                    registry = registry,
                    status = status,
                    onHeaderFocused = { focused.value = null },
                    onSearch = { sidebar = Sidebar.Search },
                    onAssistant = { context.launchAssistant() },
                    onSettings = { sidebar = Sidebar.Settings() }
                )

                if (apps.isEmpty()) {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else if (sections.isEmpty()) {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = pad),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            text = "Nothing to show here. Open Settings, then Home screen or Apps, to change what appears.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(bottom = 32.dp)
                    ) {
                        items(sections, key = { it.id }) { section ->
                            AppRow(
                                section = section,
                                bannerStyle = bannerStyle,
                                showLabels = prefs.showLabels,
                                itemWidth = itemWidth,
                                pad = pad,
                                favorites = favoriteSet,
                                iconShape = iconShape,
                                registry = registry,
                                onFocused = { focused.value = it },
                                onOpen = open,
                                onMenu = { sidebar = Sidebar.Options(it) }
                            )
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind { drawRect(Color.Black.copy(alpha = 0.5f * depth.value.coerceIn(0f, 1f))) }
                .then(
                    if (sidebarOpen) {
                        Modifier.pointerInput(Unit) { detectTapGestures { sidebar = Sidebar.Closed } }
                    } else {
                        Modifier
                    }
                )
        )

        AnimatedVisibility(
            visible = sidebarOpen,
            modifier = Modifier.align(Alignment.CenterEnd),
            enter = slideInHorizontally(
                animationSpec = spring<IntOffset>(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)
            ) { it } + fadeIn(tween(120)),
            exit = slideOutHorizontally(animationSpec = tween<IntOffset>(200)) { it } + fadeOut(tween(120))
        ) {
            SidebarHost(
                sidebar = memory.value,
                prefs = prefs,
                managerApps = managerApps,
                favorites = favoriteSet,
                favoriteOrder = favorites,
                usage = usage,
                recents = searchRecents,
                photo = photo,
                actions = actions,
                modifier = Modifier.width(sidebarWidth)
            )
        }

        AnimatedVisibility(
            visible = ambient,
            enter = fadeIn(tween(700)),
            exit = fadeOut(tween(250))
        ) {
            AmbientClock(clock = clock, now = now, status = status, showStatus = prefs.showStatus)
        }
    }
}

@Composable
private fun AmbientClock(clock: ClockText, now: Long, status: State<StatusInfo>, showStatus: Boolean) {
    val minute = remember(now) { ((now / 60_000L) % 60L).toInt() }
    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        val blockWidth = 380.dp
        val blockHeight = 200.dp
        val fx = ((minute * 37) % 60) / 60f
        val fy = ((minute * 53) % 60) / 60f
        val x = animateDpAsState(
            targetValue = (maxWidth - blockWidth).coerceAtLeast(0.dp) * fx,
            animationSpec = tween(4000),
            label = "ambientX"
        )
        val y = animateDpAsState(
            targetValue = (maxHeight - blockHeight).coerceAtLeast(0.dp) * fy,
            animationSpec = tween(4000),
            label = "ambientY"
        )
        Column(
            modifier = Modifier.offset { IntOffset(x.value.roundToPx(), y.value.roundToPx()) }
        ) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = clock.time,
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontSize = 88.sp,
                        lineHeight = 92.sp,
                        fontWeight = FontWeight.Normal,
                        fontFeatureSettings = "tnum"
                    ),
                    color = Color(0xFFE4E2E6).copy(alpha = 0.85f),
                    maxLines = 1
                )
                if (clock.period.isNotEmpty()) {
                    Text(
                        text = clock.period,
                        style = MaterialTheme.typography.titleLarge,
                        color = Color(0xFFE4E2E6).copy(alpha = 0.55f),
                        modifier = Modifier.padding(bottom = 10.dp)
                    )
                }
            }
            Text(
                text = clock.date,
                style = MaterialTheme.typography.titleMedium,
                color = Color(0xFFE4E2E6).copy(alpha = 0.55f)
            )
            if (showStatus) {
                Spacer(Modifier.height(12.dp))
                StatusChips(status)
            }
        }
    }
}

@Composable
private fun Header(
    clock: ClockText,
    prefs: LauncherPrefs,
    compact: Boolean,
    pad: Dp,
    focused: State<AppEntry?>,
    favorites: Set<String>,
    hint: String,
    iconShape: IconShape,
    registry: FocusRegistry,
    status: State<StatusInfo>,
    onHeaderFocused: () -> Unit,
    onSearch: () -> Unit,
    onAssistant: () -> Unit,
    onSettings: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = pad, end = pad, top = if (compact) 12.dp else 24.dp, bottom = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            if (prefs.clockMode != 2) Clock(clock, prefs, compact)
            Spotlight(
                focused = focused,
                favorites = favorites,
                hint = hint,
                iconShape = iconShape,
                compact = compact
            )
        }
        Row(
            modifier = Modifier.padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp)
        ) {
            if (prefs.showStatus && !compact) {
                StatusChips(status)
                Spacer(Modifier.width(4.dp))
            }
            HeaderButton(registry, "search", Icons.Rounded.Search, "Search", compact, onHeaderFocused, onSearch)
            HeaderButton(registry, "assistant", Icons.Rounded.Mic, "Assistant", compact, onHeaderFocused, onAssistant)
            HeaderButton(registry, "settings", Icons.Rounded.Settings, "Settings", compact, onHeaderFocused, onSettings)
        }
    }
}

@Composable
private fun Clock(clock: ClockText, prefs: LauncherPrefs, compact: Boolean) {
    val large = prefs.clockMode == 0
    val size = if (large) {
        if (compact) 52.sp else 72.sp
    } else {
        if (compact) 28.sp else 34.sp
    }
    Column {
        if (prefs.showGreeting) {
            Text(
                text = clock.greeting,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = clock.time,
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = size,
                    lineHeight = size * 1.05f,
                    fontFeatureSettings = "tnum"
                ),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1
            )
            if (clock.period.isNotEmpty()) {
                Text(
                    text = clock.period,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = if (large) 12.dp else 4.dp)
                )
            }
            if (!large && prefs.showDate) {
                Text(
                    text = clock.date,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 6.dp, bottom = 6.dp)
                )
            }
        }
        if (large && prefs.showDate) {
            Text(
                text = clock.date,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun HeaderButton(
    registry: FocusRegistry,
    key: String,
    icon: ImageVector,
    description: String,
    compact: Boolean,
    onFocused: () -> Unit,
    onClick: () -> Unit
) {
    val requester = remember { FocusRequester() }
    DisposableEffect(key) {
        registry.map[key] = requester
        onDispose { registry.map.remove(key) }
    }
    val shapeAt = remember { cookieMorph(8, 0f, 0.1f, 1.4f) }
    FocusSurface(
        modifier = Modifier.size(if (compact) 44.dp else 56.dp),
        shapeAt = shapeAt,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        focusedContainerColor = MaterialTheme.colorScheme.primaryContainer,
        ringColor = MaterialTheme.colorScheme.primary,
        ringWidth = 2.dp,
        focusedScale = 1.14f,
        focusRequester = requester,
        onFocusChange = {
            if (it) {
                registry.last = key
                onFocused()
            }
        },
        onClick = onClick
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.align(Alignment.Center).size(if (compact) 22.dp else 26.dp)
        )
    }
}

@Composable
private fun Spotlight(
    focused: State<AppEntry?>,
    favorites: Set<String>,
    hint: String,
    iconShape: IconShape,
    compact: Boolean
) {
    val app = focused.value
    Box(
        modifier = Modifier.fillMaxWidth().height(if (compact) 64.dp else 80.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Crossfade(targetState = app, animationSpec = tween(180), label = "spotlight") { target ->
            if (target == null) {
                Text(
                    text = "Select an app",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val meta = listOfNotNull(
                    if (target.category != -1) AppCategories.name(target.category) else null,
                    if (target.isTv) "Made for TV" else null,
                    if (target.packageName in favorites) "Favorite" else null
                ).joinToString(" · ").ifEmpty { hint }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Image(
                        bitmap = target.icon,
                        contentDescription = null,
                        modifier = Modifier.size(if (compact) 44.dp else 56.dp).clip(iconShape.still)
                    )
                    Column {
                        Text(
                            text = target.label,
                            style = if (compact) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = meta,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppRow(
    section: Section,
    bannerStyle: Boolean,
    showLabels: Boolean,
    itemWidth: Dp,
    pad: Dp,
    favorites: Set<String>,
    iconShape: IconShape,
    registry: FocusRegistry,
    onFocused: (AppEntry) -> Unit,
    onOpen: (AppEntry) -> Unit,
    onMenu: (AppEntry) -> Unit
) {
    Column {
        Text(
            text = section.title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = pad, top = 8.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = pad, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(if (bannerStyle) 16.dp else 22.dp)
        ) {
            items(section.apps, key = { it.packageName }) { app ->
                AppItem(
                    app = app,
                    keyId = "${section.id}:${app.packageName}",
                    banner = bannerStyle,
                    width = itemWidth,
                    showLabel = showLabels,
                    isFavorite = app.packageName in favorites,
                    iconShape = iconShape,
                    registry = registry,
                    onFocused = { onFocused(app) },
                    onClick = { onOpen(app) },
                    onLongClick = { onMenu(app) }
                )
            }
        }
    }
}

@Composable
private fun AppItem(
    app: AppEntry,
    keyId: String,
    banner: Boolean,
    width: Dp,
    showLabel: Boolean,
    isFavorite: Boolean,
    iconShape: IconShape,
    registry: FocusRegistry,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val requester = remember { FocusRequester() }
    DisposableEffect(keyId) {
        registry.map[keyId] = requester
        onDispose { if (registry.map[keyId] === requester) registry.map.remove(keyId) }
    }
    var focused by remember { mutableStateOf(false) }
    val labelAlpha = animateFloatAsState(
        targetValue = if (focused) 1f else 0.7f,
        animationSpec = tween(160),
        label = "label"
    )
    val base = MaterialTheme.colorScheme.surfaceContainerHigh
    val high = MaterialTheme.colorScheme.surfaceContainerHighest
    val onFocusChange: (Boolean) -> Unit = { has ->
        focused = has
        if (has) {
            registry.last = keyId
            onFocused()
        }
    }

    Column(
        modifier = Modifier.width(width),
        horizontalAlignment = if (banner) Alignment.Start else Alignment.CenterHorizontally
    ) {
        if (banner) {
            val shapeAt = remember { roundedMorph(20.dp, 32.dp) }
            val bannerImage = app.banner
            FocusSurface(
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                shapeAt = shapeAt,
                containerColor = lerp(base, app.tint, 0.18f),
                focusedContainerColor = lerp(high, app.tint, 0.3f),
                ringColor = MaterialTheme.colorScheme.primary,
                focusRequester = requester,
                onFocusChange = onFocusChange,
                onLongClick = onLongClick,
                onClick = onClick
            ) {
                if (bannerImage != null) {
                    Image(
                        bitmap = bannerImage,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Image(
                        bitmap = app.icon,
                        contentDescription = null,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxHeight(0.52f)
                            .aspectRatio(1f)
                            .clip(iconShape.still)
                    )
                }
                if (isFavorite) {
                    Icon(
                        imageVector = Icons.Rounded.Star,
                        contentDescription = "Favorite",
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(16.dp)
                    )
                }
            }
        } else {
            FocusSurface(
                modifier = Modifier.size(width),
                shapeAt = iconShape.shapeAt,
                containerColor = base,
                ringColor = MaterialTheme.colorScheme.primary,
                focusedScale = 1.1f,
                focusRequester = requester,
                onFocusChange = onFocusChange,
                onLongClick = onLongClick,
                onClick = onClick
            ) {
                Image(
                    bitmap = app.icon,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        if (showLabel) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = app.label,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = if (banner) TextAlign.Start else TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
                    .graphicsLayer { alpha = labelAlpha.value }
            )
        }
    }
}
