package dev.expressive.launcher.ui

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Path as AndroidPath
import android.text.format.DateFormat
import android.view.SoundEffectConstants
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyEvent
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
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
import dev.expressive.launcher.Scene
import dev.expressive.launcher.WeatherInfo
import dev.expressive.launcher.launchApp
import dev.expressive.launcher.launchAssistant
import dev.expressive.launcher.openAppInfo
import dev.expressive.launcher.openSystemSettings
import dev.expressive.launcher.syntheticEntry
import dev.expressive.launcher.toast
import dev.expressive.launcher.uninstallApp
import dev.expressive.launcher.weatherLabel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class FocusRegistry {
    val map = HashMap<String, FocusRequester>()
    var last: String? = null
    var lastCard: String? = null
    var firstKey: String? = null
    var lastDir: Int = 0
    var active by mutableStateOf<String?>(null)
}

class FocusTracker {
    var rect by mutableStateOf<Rect?>(null)
    var visible by mutableStateOf(false)
    var tint by mutableStateOf(Color.Unspecified)
    var radius by mutableStateOf(0f)
}

val LocalFocusTracker = compositionLocalOf { FocusTracker() }

@Immutable
class CardOptions(
    val banner: Boolean,
    val labels: Boolean,
    val focusLabels: Boolean,
    val expand: Boolean,
    val sounds: Boolean,
    val rowDepth: Boolean,
    val ripple: Boolean,
    val lean: Boolean
)

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

private fun folderBitmap(apps: List<AppEntry>): ImageBitmap {
    val size = 160
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)
    canvas.drawColor(0xFF2B2E35.toInt())
    val cell = size / 2
    val inset = 12
    apps.take(4).forEachIndexed { index, app ->
        val column = index % 2
        val row = index / 2
        val target = android.graphics.Rect(
            column * cell + inset,
            row * cell + inset,
            (column + 1) * cell - inset,
            (row + 1) * cell - inset
        )
        canvas.drawBitmap(app.icon.asAndroidBitmap(), null, target, null)
    }
    return bitmap.asImageBitmap()
}

private fun backBitmap(): ImageBitmap {
    val size = 160
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)
    canvas.drawColor(0xFF2B2E35.toInt())
    val paint = Paint().apply {
        color = 0xFFE4E2E6.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 12f
        isAntiAlias = true
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    val path = AndroidPath()
    path.moveTo(95f, 40f)
    path.lineTo(55f, 80f)
    path.lineTo(95f, 120f)
    canvas.drawPath(path, paint)
    return bitmap.asImageBitmap()
}

private fun letterOf(label: String): String {
    val first = label.trim().firstOrNull()?.uppercaseChar()
    return if (first != null && first in 'A'..'Z') first.toString() else "#"
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
    val weather by viewModel.weather.collectAsStateWithLifecycle()
    val collections by viewModel.collections.collectAsStateWithLifecycle()
    val inputs by viewModel.inputs.collectAsStateWithLifecycle()
    val slots by viewModel.slots.collectAsStateWithLifecycle()
    val appOrder by viewModel.appOrder.collectAsStateWithLifecycle()
    val scenes by viewModel.scenes.collectAsStateWithLifecycle()
    val slides by viewModel.slides.collectAsStateWithLifecycle()
    val sleepEnd by viewModel.sleepEnd.collectAsStateWithLifecycle()
    val homeSignal by viewModel.homeSignal.collectAsStateWithLifecycle()
    val prefs by viewModel.settings.collectAsStateWithLifecycle()
    val photo by viewModel.photo.collectAsStateWithLifecycle()
    val status = rememberStatus()
    val tracker = remember { FocusTracker() }
    val registry = remember { FocusRegistry() }
    val latestPrefs = rememberUpdatedState(prefs)

    val isTv = remember { context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) }
    val iconShape = IconShapes.getOrElse(prefs.iconShape) { IconShapes.first() }
    val kids = prefs.kidsMode

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
    val visible = remember(named, managerApps, prefs.hidden, kids, prefs.kidsApps) {
        if (kids) {
            named.filter { it.packageName in prefs.kidsApps }
        } else {
            managerApps.filter { it.packageName !in prefs.hidden }
        }
    }
    val byPackage = remember(visible) { visible.associateBy { it.packageName } }
    val allApps = remember(visible, prefs.sort, usage, appOrder) {
        when (prefs.sort) {
            1 -> visible.sortedByDescending { it.installedAt }
            2 -> visible.sortedWith(
                compareByDescending<AppEntry> { usage[it.packageName] ?: 0 }.thenBy { it.label.lowercase() }
            )
            3 -> {
                val position = appOrder.withIndex().associate { it.value to it.index }
                visible.sortedWith(
                    compareBy<AppEntry> { position[it.packageName] ?: Int.MAX_VALUE }.thenBy { it.label.lowercase() }
                )
            }
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
    val collectionSections = remember(byPackage, collections) {
        collections.mapNotNull { (name, packages) ->
            packages.mapNotNull { byPackage[it] }
                .takeIf { it.isNotEmpty() }
                ?.let { Section("col:" + name, name, it) }
        }
    }
    val suggestedApps = remember(byPackage, slots) {
        val slot = Calendar.getInstance().get(Calendar.HOUR_OF_DAY) / 3
        slots.entries
            .mapNotNull { (packageName, counts) -> byPackage[packageName]?.let { app -> app to counts[slot] } }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(8)
            .map { it.first }
    }
    val newPackages = remember(visible) {
        val limit = System.currentTimeMillis() - 7L * 24L * 60L * 60L * 1000L
        visible.filter { it.installedAt > limit }.map { it.packageName }.toSet()
    }
    val blankIcon = remember { ImageBitmap(160, 160) }
    val sceneEntries = remember(scenes, byPackage) {
        scenes.map { scene ->
            val target = byPackage[scene.packageName]
            syntheticEntry("scene:" + scene.name, scene.name, target?.icon ?: blankIcon, target?.tint ?: Color(0xFF5E81AC))
        }
    }
    var openFolder by remember { mutableStateOf<String?>(null) }
    val folderEntries = remember(collectionSections) {
        collectionSections.map { section ->
            syntheticEntry("folder:" + section.title, section.title, folderBitmap(section.apps), Color(0xFF6B7280))
        }
    }
    val backEntry = remember { syntheticEntry("folder:__back", "Back", backBitmap(), Color(0xFF6B7280)) }
    val sections = remember(
        prefs.showFavorites, prefs.showSuggested, prefs.showScenes, prefs.showCollections, prefs.folderMode,
        prefs.showInputs, prefs.showRecent, prefs.showMostUsed, prefs.showAllRow, prefs.groupCategories, kids,
        favoriteApps, suggestedApps, sceneEntries, collectionSections, folderEntries, openFolder, inputs,
        recentApps, mostUsedApps, allApps
    ) {
        if (kids) {
            if (allApps.isEmpty()) emptyList<Section>() else listOf(Section("kids", "Kids apps", allApps))
        } else {
            buildList {
                if (prefs.showFavorites && favoriteApps.isNotEmpty()) add(Section("favorites", "Favorites", favoriteApps))
                if (prefs.showSuggested && suggestedApps.isNotEmpty()) add(Section("suggested", "Suggested now", suggestedApps))
                if (prefs.showScenes && sceneEntries.isNotEmpty()) add(Section("scenes", "Scenes", sceneEntries))
                if (prefs.showCollections) {
                    if (prefs.folderMode) {
                        val opened = openFolder
                        if (opened == null) {
                            if (folderEntries.isNotEmpty()) add(Section("folders", "Folders", folderEntries))
                        } else {
                            collectionSections.firstOrNull { it.title == opened }
                                ?.let { add(Section("folder", opened, listOf(backEntry) + it.apps)) }
                        }
                    } else {
                        addAll(collectionSections)
                    }
                }
                if (prefs.showInputs && inputs.isNotEmpty()) add(Section("inputs", "Inputs", inputs))
                if (prefs.showRecent && recentApps.isNotEmpty()) add(Section("recent", "Recent", recentApps))
                if (prefs.showMostUsed && mostUsedApps.isNotEmpty()) add(Section("most", "Most used", mostUsedApps))
                if (prefs.showAllRow && allApps.isNotEmpty()) {
                    if (prefs.groupCategories) addAll(categorySections(allApps)) else add(Section("all", "All apps", allApps))
                }
            }
        }
    }
    val firstKey = sections.firstOrNull()?.let { section ->
        section.apps.firstOrNull()?.let { app -> section.id + ":" + app.packageName }
    }
    registry.firstKey = firstKey

    val focused = remember { mutableStateOf<AppEntry?>(null) }
    var sidebar by remember { mutableStateOf<Sidebar>(Sidebar.Closed) }
    val memory = remember { SidebarMemory(Sidebar.Settings()) }
    if (sidebar !is Sidebar.Closed) memory.value = sidebar
    val sidebarOpen = sidebar !is Sidebar.Closed
    var reorder by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val initialDone = remember { booleanArrayOf(false) }
    val lastFolder = remember { arrayOfNulls<String>(1) }
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
    val slidesPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importSlides(uris) { count ->
                context.toast(if (count > 0) "Slideshow ready with $count photos" else "Could not use those photos")
            }
        }
    }

    val now by produceState(initialValue = System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(60_000L - value % 60_000L)
        }
    }
    val hourNow = remember(now) { Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY) }
    val dimEffective = if (prefs.autoTheme) {
        when (hourNow) {
            in 6..17 -> 0
            in 18..21 -> 1
            else -> 2
        }
    } else {
        prefs.dim
    }
    val sleepMinutes = if (sleepEnd > now) ((sleepEnd - now) / 60_000L + 1).toInt() else 0
    val systemIs24 = remember { DateFormat.is24HourFormat(context) }
    val use24 = when (prefs.timeFormat) {
        1 -> false
        2 -> true
        else -> systemIs24
    }
    val clock = remember(now, use24) {
        val date = Date(now)
        ClockText(
            time = SimpleDateFormat(if (use24) "HH:mm" else "h:mm", Locale.getDefault()).format(date),
            period = if (use24) "" else SimpleDateFormat("a", Locale.getDefault()).format(date),
            date = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(date),
            greeting = when {
                hourNow < 5 -> "Late night"
                hourNow < 12 -> "Good morning"
                hourNow < 18 -> "Good afternoon"
                else -> "Good evening"
            }
        )
    }

    val open: (AppEntry) -> Unit = { app ->
        val packageName = app.packageName
        when {
            packageName == "folder:__back" -> openFolder = null
            packageName.startsWith("folder:") -> openFolder = packageName.removePrefix("folder:")
            packageName.startsWith("scene:") -> viewModel.runScene(packageName.removePrefix("scene:"))
            else -> {
                if (!packageName.startsWith("input:")) viewModel.markLaunched(packageName)
                context.launchApp(app)
            }
        }
        Unit
    }

    val reorderableKey: (String?) -> Boolean = { key ->
        if (key == null || !key.contains(":")) {
            false
        } else {
            val id = key.substringBeforeLast(":")
            id == "favorites" || id.startsWith("col:") || id == "all" || id.startsWith("cat:")
        }
    }
    val moveActive: (Int) -> Unit = { delta ->
        val key = reorder
        if (key != null) {
            val sectionId = key.substringBeforeLast(":")
            val packageName = key.substringAfterLast(":")
            val list = sections.firstOrNull { it.id == sectionId }?.apps.orEmpty()
            val index = list.indexOfFirst { it.packageName == packageName }
            val neighbor = list.getOrNull(index + delta)?.packageName
            when {
                sectionId == "favorites" -> viewModel.moveFavorite(packageName, delta)
                sectionId.startsWith("col:") -> viewModel.moveInCollection(sectionId.removePrefix("col:"), packageName, delta)
                sectionId == "all" || sectionId.startsWith("cat:") ->
                    viewModel.moveInOrder(packageName, neighbor, allApps.map { it.packageName })
            }
        }
        Unit
    }

    val toggleQuickPanel: () -> Unit = {
        val current = sidebar
        sidebar = if (current is Sidebar.Settings && current.page == SettingsPage.Quick) {
            Sidebar.Closed
        } else {
            Sidebar.Settings(SettingsPage.Quick)
        }
    }
    val perform: (Int) -> Unit = { action ->
        when (action) {
            1 -> sidebar = Sidebar.Search
            2 -> toggleQuickPanel()
            3 -> sidebar = Sidebar.Settings()
            4 -> context.launchAssistant()
            5 -> viewModel.startAmbient()
            6 -> viewModel.cycleSleep()
            7 -> viewModel.runFirstScene()
            8 -> scope.launch { listState.animateScrollToItem(0) }
            else -> Unit
        }
        Unit
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
        setCategory = { packageName, id -> viewModel.setCategory(packageName, id) },
        lookupCity = { viewModel.lookupCity(it) },
        toggleCollection = { name, packageName -> viewModel.toggleInCollection(name, packageName) },
        createCollection = { name, packageName -> viewModel.createCollection(name, packageName) },
        deleteCollection = { viewModel.deleteCollection(it) },
        reorder = {
            sidebar = Sidebar.Closed
            reorder = registry.lastCard
        },
        canReorder = { reorderableKey(registry.lastCard) },
        saveScene = { viewModel.saveScene(it) },
        deleteScene = { viewModel.deleteScene(it) },
        runScene = { viewModel.runScene(it) },
        setSleep = { viewModel.setSleep(it) },
        pickSlides = {
            runCatching { slidesPicker.launch("image/*") }
                .onFailure { context.toast("No photo picker on this device") }
        }
    )

    LaunchedEffect(homeSignal) {
        if (homeSignal > 0) {
            sidebar = Sidebar.Closed
            reorder = null
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

    LaunchedEffect(openFolder) {
        if (!isTv) return@LaunchedEffect
        val current = openFolder
        if (current != null) lastFolder[0] = current
        val key = if (current != null) "folder:folder:__back" else lastFolder[0]?.let { "folders:folder:" + it }
        if (key != null) {
            repeat(10) {
                delay(70)
                if (registry.map[key]?.tryFocus() == true) return@LaunchedEffect
            }
        }
    }

    LaunchedEffect(quickSignal) {
        if (quickSignal > 0) {
            if (latestPrefs.value.kidsMode) sidebar = Sidebar.Pin else toggleQuickPanel()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.colorKeys.collect { index ->
            val current = latestPrefs.value
            if (!current.kidsMode) {
                val action = when (index) {
                    0 -> current.keyRed
                    1 -> current.keyGreen
                    2 -> current.keyYellow
                    else -> current.keyBlue
                }
                perform(action)
            }
        }
    }

    BackHandler(enabled = !sidebarOpen && reorder == null) {
        scope.launch { listState.animateScrollToItem(0) }
    }
    BackHandler(enabled = sidebarOpen) {
        sidebar = sidebar.parent()
    }
    BackHandler(enabled = reorder != null) {
        reorder = null
    }

    val cardOptions = remember(
        prefs.cardStyle, prefs.showLabels, prefs.focusLabels, prefs.focusStyle,
        prefs.navSounds, prefs.rowDepth, prefs.ripple, prefs.lean
    ) {
        CardOptions(
            banner = prefs.cardStyle == 0,
            labels = prefs.showLabels,
            focusLabels = prefs.focusLabels,
            expand = prefs.focusStyle == 1,
            sounds = prefs.navSounds,
            rowDepth = prefs.rowDepth,
            ripple = prefs.ripple,
            lean = prefs.lean
        )
    }

    CompositionLocalProvider(LocalFocusTracker provides tracker) {
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
            val itemWidth = if (cardOptions.banner) baseWidth * scale else baseWidth * 0.52f * scale
            val sidebarWidth = if (compact) maxWidth * 0.88f else 440.dp

            WallpaperLayer(
                wallpaperId = prefs.wallpaper,
                photo = photo,
                dim = dimEffective,
                accentContainer = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxSize()
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .focusProperties { canFocus = !sidebarOpen }
                    .onPreviewKeyEvent { event ->
                        val pressed = event.key
                        if (event.type == KeyEventType.KeyDown) {
                            registry.lastDir = when (pressed) {
                                Key.DirectionLeft -> 1
                                Key.DirectionRight -> 2
                                Key.DirectionUp -> 3
                                Key.DirectionDown -> 4
                                else -> registry.lastDir
                            }
                        }
                        if (reorder != null) {
                            if (event.type == KeyEventType.KeyDown &&
                                (pressed == Key.DirectionLeft || pressed == Key.DirectionRight)
                            ) {
                                moveActive(if (pressed == Key.DirectionLeft) -1 else 1)
                                true
                            } else if (pressed == Key.DirectionCenter || pressed == Key.Enter || pressed == Key.NumPadEnter) {
                                if (event.type == KeyEventType.KeyUp) reorder = null
                                true
                            } else {
                                pressed == Key.DirectionUp || pressed == Key.DirectionDown
                            }
                        } else {
                            false
                        }
                    }
                    .onKeyEvent { event ->
                        if (event.type == KeyEventType.KeyUp && !kids) {
                            val index = when (event.key) {
                                Key.One, Key.NumPad1 -> 0
                                Key.Two, Key.NumPad2 -> 1
                                Key.Three, Key.NumPad3 -> 2
                                Key.Four, Key.NumPad4 -> 3
                                Key.Five, Key.NumPad5 -> 4
                                Key.Six, Key.NumPad6 -> 5
                                Key.Seven, Key.NumPad7 -> 6
                                Key.Eight, Key.NumPad8 -> 7
                                Key.Nine, Key.NumPad9 -> 8
                                else -> -1
                            }
                            val target = if (index >= 0) favoriteApps.getOrNull(index) else null
                            if (target != null) {
                                open(target)
                                true
                            } else {
                                false
                            }
                        } else {
                            false
                        }
                    }
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
                        weather = weather,
                        kids = kids,
                        onHeaderFocused = { focused.value = null },
                        onSearch = { sidebar = Sidebar.Search },
                        onAssistant = { context.launchAssistant() },
                        onSettings = { sidebar = Sidebar.Settings() },
                        onUnlock = { sidebar = Sidebar.Pin }
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
                                text = if (kids) {
                                    "No apps are allowed in kids mode yet."
                                } else {
                                    "Nothing to show here. Open Settings, then Home screen or Apps, to change what appears."
                                },
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
                            if (prefs.showQuickRow && !kids) {
                                item(key = "quick") {
                                    QuickRow(
                                        pad = pad,
                                        registry = registry,
                                        tracker = tracker,
                                        sleepMinutes = sleepMinutes,
                                        onWifi = { context.openSystemSettings(android.provider.Settings.ACTION_WIFI_SETTINGS) },
                                        onBluetooth = { context.openSystemSettings(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS) },
                                        onSleep = { viewModel.cycleSleep() },
                                        onAmbient = { viewModel.startAmbient() },
                                        onAssistant = { context.launchAssistant() }
                                    )
                                }
                            }
                            items(sections, key = { it.id }) { section ->
                                AppRow(
                                    section = section,
                                    opts = cardOptions,
                                    itemWidth = itemWidth,
                                    pad = pad,
                                    favorites = favoriteSet,
                                    newPackages = newPackages,
                                    iconShape = iconShape,
                                    registry = registry,
                                    showLetters = section.id == "all" && prefs.sort == 0 && section.apps.size > 14,
                                    onFocused = { focused.value = it },
                                    onOpen = open,
                                    onMenu = {
                                        if (!kids && !it.packageName.contains(':')) {
                                            sidebar = Sidebar.Options(it)
                                            viewModel.armOkGuard()
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            GlidingRing(tracker = tracker, enabled = !sidebarOpen)

            if (reorder != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 28.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 22.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = "Reorder: move with left and right, press OK when done",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
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
                    collections = collections,
                    scenes = scenes,
                    sleepEnd = sleepEnd,
                    slots = slots,
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
                AmbientClock(
                    clock = clock,
                    now = now,
                    status = status,
                    showStatus = prefs.showStatus,
                    slides = slides,
                    style = prefs.ambientStyle
                )
            }
        }
    }
}

@Composable
private fun GlidingRing(tracker: FocusTracker, enabled: Boolean) {
    val motion = LocalMotion.current
    val primary = MaterialTheme.colorScheme.primary
    val anim = remember { Animatable(Rect(0f, 0f, 0f, 0f), Rect.VectorConverter) }
    val shown = enabled && tracker.visible
    val alpha = animateFloatAsState(targetValue = if (shown) 1f else 0f, animationSpec = tween(140), label = "ringAlpha")
    val ringColor = animateColorAsState(
        targetValue = if (tracker.tint == Color.Unspecified) primary else lerp(primary, tracker.tint, 0.55f),
        animationSpec = tween(250),
        label = "ringColor"
    )
    LaunchedEffect(tracker, motion) {
        var first = true
        snapshotFlow { tracker.rect }.collectLatest { target ->
            if (target != null) {
                if (first || alpha.value < 0.05f || motion == 2) {
                    anim.snapTo(target)
                } else {
                    anim.animateTo(
                        target,
                        spring<Rect>(dampingRatio = if (motion == 1) 1f else 0.72f, stiffness = 800f)
                    )
                }
                first = false
            }
        }
    }
    Canvas(modifier = Modifier.fillMaxSize()) {
        val rect = anim.value
        val currentAlpha = alpha.value
        if (currentAlpha > 0.01f && rect.width > 1f) {
            val inflate = 4.dp.toPx()
            drawRoundRect(
                color = ringColor.value.copy(alpha = currentAlpha),
                topLeft = Offset(rect.left - inflate, rect.top - inflate),
                size = Size(rect.width + 2f * inflate, rect.height + 2f * inflate),
                cornerRadius = CornerRadius(tracker.radius + inflate),
                style = Stroke(width = 3.dp.toPx())
            )
        }
    }
}

@Composable
private fun AmbientClock(
    clock: ClockText,
    now: Long,
    status: State<StatusInfo>,
    showStatus: Boolean,
    slides: List<ImageBitmap>,
    style: Int
) {
    if (style == 1 && slides.isNotEmpty()) {
        AmbientSlides(clock, slides)
        return
    }
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
private fun AmbientSlides(clock: ClockText, slides: List<ImageBitmap>) {
    var index by remember { mutableStateOf(0) }
    LaunchedEffect(slides.size) {
        while (true) {
            delay(12_000L)
            index = (index + 1) % slides.size
        }
    }
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Crossfade(targetState = index, animationSpec = tween(1600), label = "slides") { current ->
            val zoom = remember { Animatable(1f) }
            LaunchedEffect(current) {
                zoom.snapTo(1f)
                zoom.animateTo(1.12f, tween(14_000, easing = LinearEasing))
            }
            Image(
                bitmap = slides[current % slides.size],
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    scaleX = zoom.value
                    scaleY = zoom.value
                }
            )
        }
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.6f to Color.Transparent,
                    1f to Color(0xB3000000)
                )
            )
        )
        Column(modifier = Modifier.align(Alignment.BottomStart).padding(40.dp)) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = clock.time,
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontSize = 64.sp,
                        lineHeight = 68.sp,
                        fontWeight = FontWeight.Normal,
                        fontFeatureSettings = "tnum"
                    ),
                    color = Color.White.copy(alpha = 0.92f)
                )
                if (clock.period.isNotEmpty()) {
                    Text(
                        text = clock.period,
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
            }
            Text(
                text = clock.date,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.7f)
            )
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
    weather: WeatherInfo?,
    kids: Boolean,
    onHeaderFocused: () -> Unit,
    onSearch: () -> Unit,
    onAssistant: () -> Unit,
    onSettings: () -> Unit,
    onUnlock: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = pad, end = pad, top = if (compact) 12.dp else 24.dp, bottom = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            if (prefs.clockMode != 2) Clock(clock, prefs, compact, weather)
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
            if (kids) {
                HeaderButton(registry, "lock", Icons.Rounded.Lock, "Unlock", compact, onHeaderFocused, onUnlock)
            } else {
                HeaderButton(registry, "search", Icons.Rounded.Search, "Search", compact, onHeaderFocused, onSearch)
                HeaderButton(registry, "assistant", Icons.Rounded.Mic, "Assistant", compact, onHeaderFocused, onAssistant)
                HeaderButton(registry, "settings", Icons.Rounded.Settings, "Settings", compact, onHeaderFocused, onSettings)
            }
        }
    }
}

@Composable
private fun Clock(clock: ClockText, prefs: LauncherPrefs, compact: Boolean, weather: WeatherInfo?) {
    val large = prefs.clockMode == 0
    val weatherText = weather?.let { it.temp.toString() + "° " + weatherLabel(it.code) }
    val infoLine = listOfNotNull(clock.date.takeIf { prefs.showDate }, weatherText).joinToString("  ·  ")
    val size = if (large) {
        if (compact) 52.sp else 72.sp
    } else {
        if (compact) 28.sp else 34.sp
    }
    Column {
        if (prefs.showGreeting) {
            Text(
                text = if (prefs.userName.isBlank()) clock.greeting else clock.greeting + ", " + prefs.userName.trim(),
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
            if (!large && infoLine.isNotEmpty()) {
                Text(
                    text = infoLine,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 6.dp, bottom = 6.dp)
                )
            }
        }
        if (large && infoLine.isNotEmpty()) {
            Text(
                text = infoLine,
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
    val tracker = LocalFocusTracker.current
    val requester = remember { FocusRequester() }
    DisposableEffect(key) {
        registry.map[key] = requester
        onDispose { registry.map.remove(key) }
    }
    val shapeAt = remember { cookieMorph(8, 0f, 0.1f, 1.4f) }
    FocusSurface(
        modifier = Modifier
            .size(if (compact) 44.dp else 56.dp)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                    val firstSection = registry.firstKey?.substringBeforeLast(":")
                    val target = registry.lastCard
                        ?.takeIf { it.substringBeforeLast(":") == firstSection && registry.map.containsKey(it) }
                        ?: registry.firstKey
                    target != null && registry.map[target]?.tryFocus() == true
                } else {
                    false
                }
            },
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
                registry.active = null
                tracker.visible = false
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
        Crossfade(
            targetState = app,
            animationSpec = if (LocalMotion.current == 2) snap<Float>() else tween(120),
            label = "spotlight"
        ) { target ->
            if (target == null) {
                Text(
                    text = "Press down to browse your apps",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val meta = listOfNotNull(
                    if (target.category != -1) AppCategories.name(target.category) else null,
                    if (target.packageName.startsWith("input:")) "TV input" else if (target.isTv) "Made for TV" else null,
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

private class QuickChip(val key: String, val icon: ImageVector, val label: String, val action: () -> Unit)

@Composable
private fun QuickRow(
    pad: Dp,
    registry: FocusRegistry,
    tracker: FocusTracker,
    sleepMinutes: Int,
    onWifi: () -> Unit,
    onBluetooth: () -> Unit,
    onSleep: () -> Unit,
    onAmbient: () -> Unit,
    onAssistant: () -> Unit
) {
    val chips = listOf(
        QuickChip("wifi", Icons.Rounded.Wifi, "Wi-Fi", onWifi),
        QuickChip("bluetooth", Icons.Rounded.Bluetooth, "Bluetooth", onBluetooth),
        QuickChip("sleep", Icons.Rounded.Bedtime, if (sleepMinutes > 0) "Sleep in $sleepMinutes min" else "Sleep timer", onSleep),
        QuickChip("ambient", Icons.Rounded.Schedule, "Ambient clock", onAmbient),
        QuickChip("assistant", Icons.Rounded.Mic, "Assistant", onAssistant)
    )
    LazyRow(
        contentPadding = PaddingValues(horizontal = pad, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(chips, key = { it.key }) { chip -> QuickChipView(chip, registry, tracker) }
    }
}

@Composable
private fun QuickChipView(chip: QuickChip, registry: FocusRegistry, tracker: FocusTracker) {
    val requester = remember { FocusRequester() }
    val registryKey = "quick:" + chip.key
    DisposableEffect(registryKey) {
        registry.map[registryKey] = requester
        onDispose { registry.map.remove(registryKey) }
    }
    val shapeAt = remember { percentMorph(50, 34) }
    FocusSurface(
        modifier = Modifier.height(44.dp),
        shapeAt = shapeAt,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        focusedContainerColor = MaterialTheme.colorScheme.primaryContainer,
        ringColor = MaterialTheme.colorScheme.primary,
        ringWidth = 2.dp,
        focusedScale = 1.06f,
        focusRequester = requester,
        onFocusChange = {
            if (it) {
                tracker.visible = false
                registry.active = null
                registry.last = registryKey
            }
        },
        onClick = chip.action
    ) {
        Row(
            modifier = Modifier.fillMaxHeight().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = chip.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = chip.label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun AppRow(
    section: Section,
    opts: CardOptions,
    itemWidth: Dp,
    pad: Dp,
    favorites: Set<String>,
    newPackages: Set<String>,
    iconShape: IconShape,
    registry: FocusRegistry,
    showLetters: Boolean,
    onFocused: (AppEntry) -> Unit,
    onOpen: (AppEntry) -> Unit,
    onMenu: (AppEntry) -> Unit
) {
    val rowState = rememberLazyListState()
    val focusedIndex = remember { mutableStateOf(-1) }
    val active = registry.active
    val dimmed = opts.rowDepth && active != null && active != section.id
    val rowAlpha = animateFloatAsState(
        targetValue = if (dimmed) 0.62f else 1f,
        animationSpec = tween(220),
        label = "rowAlpha"
    )
    Column(
        modifier = Modifier.graphicsLayer {
            alpha = rowAlpha.value
            compositingStrategy = CompositingStrategy.ModulateAlpha
        }
    ) {
        Text(
            text = section.title,
            style = MaterialTheme.typography.titleLarge,
            color = if (active == section.id) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = pad, top = 8.dp)
        )
        if (showLetters) {
            LetterStrip(section.apps, pad, registry, rowState, section.id)
        }
        LazyRow(
            state = rowState,
            modifier = Modifier.onFocusChanged { if (!it.hasFocus) focusedIndex.value = -1 },
            contentPadding = PaddingValues(horizontal = pad, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(if (opts.banner) 16.dp else 22.dp)
        ) {
            itemsIndexed(section.apps, key = { _, app -> app.packageName }) { index, app ->
                AppItem(
                    app = app,
                    keyId = section.id + ":" + app.packageName,
                    sectionId = section.id,
                    index = index,
                    isLast = index == section.apps.lastIndex,
                    opts = opts,
                    width = itemWidth,
                    isFavorite = app.packageName in favorites,
                    isNew = app.packageName in newPackages,
                    iconShape = iconShape,
                    registry = registry,
                    focusedIndex = focusedIndex,
                    onFocused = { onFocused(app) },
                    onClick = { onOpen(app) },
                    onLongClick = { onMenu(app) }
                )
            }
        }
    }
}

@Composable
private fun LetterStrip(
    apps: List<AppEntry>,
    pad: Dp,
    registry: FocusRegistry,
    rowState: LazyListState,
    sectionId: String
) {
    val scope = rememberCoroutineScope()
    val tracker = LocalFocusTracker.current
    val letters = remember(apps) { apps.map { letterOf(it.label) }.distinct() }
    val shapeAt = remember { percentMorph(50, 30) }
    LazyRow(
        contentPadding = PaddingValues(horizontal = pad, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(letters, key = { it }) { letter ->
            FocusSurface(
                modifier = Modifier.size(34.dp),
                shapeAt = shapeAt,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                focusedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                ringColor = MaterialTheme.colorScheme.primary,
                ringWidth = 0.dp,
                focusedScale = 1.12f,
                onFocusChange = { if (it) tracker.visible = false },
                onClick = {
                    val target = apps.indexOfFirst { letterOf(it.label) == letter }
                    if (target >= 0) {
                        scope.launch {
                            rowState.scrollToItem(target)
                            val key = sectionId + ":" + apps[target].packageName
                            repeat(10) {
                                delay(60)
                                if (registry.map[key]?.tryFocus() == true) return@launch
                            }
                        }
                    }
                }
            ) {
                Text(
                    text = letter,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
}

@Composable
private fun AppItem(
    app: AppEntry,
    keyId: String,
    sectionId: String,
    index: Int,
    isLast: Boolean,
    opts: CardOptions,
    width: Dp,
    isFavorite: Boolean,
    isNew: Boolean,
    iconShape: IconShape,
    registry: FocusRegistry,
    focusedIndex: MutableState<Int>,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val motion = LocalMotion.current
    val tracker = LocalFocusTracker.current
    val density = LocalDensity.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val requester = remember { FocusRequester() }
    DisposableEffect(keyId) {
        registry.map[keyId] = requester
        onDispose { if (registry.map[keyId] === requester) registry.map.remove(keyId) }
    }
    var focused by remember { mutableStateOf(false) }
    val bounds = remember { arrayOfNulls<Rect>(1) }

    val pop = animateFloatAsState(
        targetValue = if (focused && motion != 2) 1f else 0f,
        animationSpec = if (motion == 1) {
            tween<Float>(300)
        } else {
            spring<Float>(dampingRatio = 0.55f, stiffness = 500f)
        },
        label = "pop"
    )
    val zoom = animateFloatAsState(
        targetValue = if (focused && motion != 2) 1f else 0f,
        animationSpec = tween(900),
        label = "zoom"
    )
    val expand = animateFloatAsState(
        targetValue = if (opts.expand && opts.banner && focused) 1f else 0f,
        animationSpec = when (motion) {
            2 -> snap<Float>()
            1 -> spring<Float>(dampingRatio = 1f, stiffness = 700f)
            else -> spring<Float>(dampingRatio = 0.75f, stiffness = 600f)
        },
        label = "expand"
    )
    val labelAlpha = animateFloatAsState(
        targetValue = if (focused) 1f else if (opts.focusLabels) 0f else 0.7f,
        animationSpec = tween(160),
        label = "label"
    )
    val influence = remember { Animatable(0f) }
    val lean = remember { Animatable(0f) }
    val bump = remember { Animatable(0f) }

    if (opts.ripple && motion != 2) {
        LaunchedEffect(index) {
            snapshotFlow {
                val current = focusedIndex.value
                if (current < 0) -1 else minOf(abs(index - current), 3)
            }.collectLatest { distance ->
                val target = when (distance) {
                    1 -> 1f
                    2 -> 0.5f
                    3 -> 0.25f
                    else -> 0f
                }
                influence.animateTo(target, spring<Float>(dampingRatio = 0.85f, stiffness = 450f))
            }
        }
    }

    val onFocusChange: (Boolean) -> Unit = { has ->
        focused = has
        if (has) {
            registry.last = keyId
            registry.lastCard = keyId
            registry.active = sectionId
            tracker.tint = app.tint
            tracker.radius = with(density) { (if (opts.banner) 34.dp else width * 0.4f).toPx() }
            val current = bounds[0]
            if (current != null) tracker.rect = current
            tracker.visible = true
            if (opts.lean && motion != 2 && (registry.lastDir == 1 || registry.lastDir == 2)) {
                scope.launch {
                    lean.snapTo(if (registry.lastDir == 1) -3f else 3f)
                    lean.animateTo(0f, spring<Float>(dampingRatio = 0.35f, stiffness = 260f))
                }
            }
            if (opts.sounds) {
                view.playSoundEffect(
                    when (registry.lastDir) {
                        1 -> SoundEffectConstants.NAVIGATION_LEFT
                        2 -> SoundEffectConstants.NAVIGATION_RIGHT
                        3 -> SoundEffectConstants.NAVIGATION_UP
                        else -> SoundEffectConstants.NAVIGATION_DOWN
                    }
                )
            }
            onFocused()
        }
    }

    val surfaceModifier = Modifier
        .onGloballyPositioned { coordinates ->
            val rect = coordinates.boundsInRoot()
            bounds[0] = rect
            if (focused) tracker.rect = rect
        }
        .onPreviewKeyEvent { event ->
            if (opts.lean && motion != 2 && event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) {
                val atEnd = (event.key == Key.DirectionRight && isLast) || (event.key == Key.DirectionLeft && index == 0)
                if (atEnd) {
                    val direction = if (event.key == Key.DirectionRight) 14f else -14f
                    scope.launch {
                        bump.snapTo(direction)
                        bump.animateTo(0f, spring<Float>(dampingRatio = 0.3f, stiffness = 350f))
                    }
                }
            }
            false
        }

    val baseWidthPx = with(density) { width.roundToPx() }
    val columnModifier = if (opts.banner && opts.expand) {
        Modifier.layout { measurable, constraints ->
            val target = baseWidthPx + (baseWidthPx * 0.38f * expand.value).roundToInt()
            val placeable = measurable.measure(constraints.copy(minWidth = target, maxWidth = target))
            layout(target, placeable.height) { placeable.place(0, 0) }
        }
    } else {
        Modifier.width(width)
    }

    Column(
        modifier = columnModifier,
        horizontalAlignment = if (opts.banner) Alignment.Start else Alignment.CenterHorizontally
    ) {
        if (opts.banner) {
            val shapeAt = remember { roundedMorph(20.dp, 32.dp) }
            val base = MaterialTheme.colorScheme.surfaceContainerHigh
            val high = MaterialTheme.colorScheme.surfaceContainerHighest
            val bannerImage = app.banner
            FocusSurface(
                modifier = surfaceModifier.fillMaxWidth().height(width * 9f / 16f),
                shapeAt = shapeAt,
                containerColor = lerp(base, app.tint, 0.18f),
                focusedContainerColor = lerp(high, app.tint, 0.3f),
                ringColor = MaterialTheme.colorScheme.primary,
                ringWidth = 0.dp,
                focusedScale = if (opts.expand) 1f else 1.06f,
                focusRequester = requester,
                onFocusChange = onFocusChange,
                onLongClick = onLongClick,
                extraScale = { 1f - 0.05f * influence.value },
                extraAlpha = { 1f - 0.18f * influence.value },
                extraRotation = { lean.value },
                extraTranslationX = { bump.value * density.density },
                onClick = onClick
            ) {
                if (bannerImage != null) {
                    Image(
                        bitmap = bannerImage,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().graphicsLayer {
                            val s = 1f + 0.07f * zoom.value
                            scaleX = s
                            scaleY = s
                        }
                    )
                } else {
                    Image(
                        bitmap = app.icon,
                        contentDescription = null,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxHeight(0.52f)
                            .aspectRatio(1f)
                            .graphicsLayer {
                                val s = 1f + 0.16f * pop.value
                                scaleX = s
                                scaleY = s
                                translationY = -6f * density.density * pop.value
                            }
                            .clip(iconShape.still)
                    )
                }
                if (opts.expand) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .graphicsLayer { alpha = expand.value }
                            .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))))
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Column {
                            Text(
                                text = app.label,
                                style = MaterialTheme.typography.titleSmall,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = AppCategories.name(app.category),
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(alpha = 0.75f),
                                maxLines = 1
                            )
                        }
                    }
                }
                if (isNew) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "New",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
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
                modifier = surfaceModifier.size(width),
                shapeAt = iconShape.shapeAt,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ringColor = MaterialTheme.colorScheme.primary,
                ringWidth = 0.dp,
                focusedScale = 1.1f,
                focusRequester = requester,
                onFocusChange = onFocusChange,
                onLongClick = onLongClick,
                extraScale = { 1f - 0.05f * influence.value },
                extraAlpha = { 1f - 0.18f * influence.value },
                extraRotation = { lean.value },
                extraTranslationX = { bump.value * density.density },
                onClick = onClick
            ) {
                Image(
                    bitmap = app.icon,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize()
                )
                if (isNew) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .padding(horizontal = 7.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "New",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }
        if (opts.labels) {
            Spacer(Modifier.height(10.dp))
            if (opts.focusLabels) {
                Box(
                    modifier = Modifier
                        .widthIn(max = width)
                        .graphicsLayer { alpha = labelAlpha.value }
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = app.label,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            } else {
                Text(
                    text = app.label,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = if (opts.banner) TextAlign.Start else TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp)
                        .graphicsLayer { alpha = labelAlpha.value }
                )
            }
        }
    }
}
