package dev.expressive.launcher.ui

import android.content.pm.PackageManager
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.expressive.launcher.AppEntry
import dev.expressive.launcher.LauncherViewModel
import dev.expressive.launcher.launchApp
import dev.expressive.launcher.openAppInfo
import dev.expressive.launcher.openSystemSettings
import dev.expressive.launcher.uninstallApp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun LauncherScreen(viewModel: LauncherViewModel) {
    val context = LocalContext.current
    val apps by viewModel.apps.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val recents by viewModel.recents.collectAsStateWithLifecycle()
    val homeSignal by viewModel.homeSignal.collectAsStateWithLifecycle()

    val isTv = remember { context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) }
    val byPackage = remember(apps) { apps.associateBy { it.packageName } }
    val favoriteApps = remember(byPackage, favorites) { favorites.mapNotNull { byPackage[it] } }
    val recentApps = remember(byPackage, recents) { recents.mapNotNull { byPackage[it] } }
    val favoriteSet = remember(favorites) { favorites.toSet() }

    var focused by remember { mutableStateOf<AppEntry?>(null) }
    var menuFor by remember { mutableStateOf<AppEntry?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val initialFocus = remember { FocusRequester() }

    val now by produceState(initialValue = System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(60_000L - value % 60_000L)
        }
    }
    val timeText = remember(now) { DateFormat.getTimeFormat(context).format(Date(now)) }
    val dateText = remember(now) { SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(Date(now)) }
    val greeting = remember(now) {
        val hour = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY)
        when {
            hour < 5 -> "Late night"
            hour < 12 -> "Good morning"
            hour < 18 -> "Good afternoon"
            else -> "Good evening"
        }
    }

    val open: (AppEntry) -> Unit = { app ->
        viewModel.markLaunched(app.packageName)
        context.launchApp(app)
    }

    LaunchedEffect(homeSignal) {
        if (homeSignal > 0) listState.animateScrollToItem(0)
    }

    LaunchedEffect(apps.isNotEmpty()) {
        if (isTv && apps.isNotEmpty()) {
            repeat(10) {
                delay(80)
                if (runCatching { initialFocus.requestFocus() }.isSuccess) return@LaunchedEffect
            }
        }
    }

    BackHandler {
        scope.launch { listState.animateScrollToItem(0) }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val compact = maxWidth < 600.dp
        val pad = if (compact) 20.dp else 56.dp
        val cardWidth = if (compact) 156.dp else maxWidth * 0.17f
        val glow = MaterialTheme.colorScheme.primaryContainer
        val accent = MaterialTheme.colorScheme.tertiaryContainer

        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    drawRect(
                        Brush.radialGradient(
                            colors = listOf(glow.copy(alpha = 0.6f), Color.Transparent),
                            center = Offset(size.width * 0.9f, 0f),
                            radius = size.maxDimension * 0.7f
                        )
                    )
                    drawRect(
                        Brush.radialGradient(
                            colors = listOf(accent.copy(alpha = 0.35f), Color.Transparent),
                            center = Offset(0f, size.height),
                            radius = size.maxDimension * 0.6f
                        )
                    )
                }
        )

        Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            TopBar(
                time = timeText,
                date = dateText,
                pad = pad,
                onFocused = { focused = null },
                onSettings = { context.openSystemSettings() }
            )
            Hero(
                focused = focused,
                greeting = greeting,
                hint = if (isTv) "Hold OK for options" else "Long press for options",
                compact = compact,
                pad = pad
            )

            if (apps.isEmpty()) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                val firstSection = when {
                    favoriteApps.isNotEmpty() -> "favorites"
                    recentApps.isNotEmpty() -> "recent"
                    else -> "all"
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 32.dp)
                ) {
                    if (favoriteApps.isNotEmpty()) {
                        item(key = "favorites") {
                            AppRow(
                                title = "Favorites",
                                apps = favoriteApps,
                                cardWidth = cardWidth,
                                pad = pad,
                                favorites = favoriteSet,
                                firstRequester = initialFocus.takeIf { firstSection == "favorites" },
                                onFocused = { focused = it },
                                onOpen = open,
                                onMenu = { menuFor = it }
                            )
                        }
                    }
                    if (recentApps.isNotEmpty()) {
                        item(key = "recent") {
                            AppRow(
                                title = "Recent",
                                apps = recentApps,
                                cardWidth = cardWidth,
                                pad = pad,
                                favorites = favoriteSet,
                                firstRequester = initialFocus.takeIf { firstSection == "recent" },
                                onFocused = { focused = it },
                                onOpen = open,
                                onMenu = { menuFor = it }
                            )
                        }
                    }
                    item(key = "all") {
                        AppRow(
                            title = "All apps",
                            apps = apps,
                            cardWidth = cardWidth,
                            pad = pad,
                            favorites = favoriteSet,
                            firstRequester = initialFocus.takeIf { firstSection == "all" },
                            onFocused = { focused = it },
                            onOpen = open,
                            onMenu = { menuFor = it }
                        )
                    }
                }
            }
        }
    }

    menuFor?.let { app ->
        val firstAction = remember { FocusRequester() }
        LaunchedEffect(app) {
            delay(120)
            runCatching { firstAction.requestFocus() }
        }
        AlertDialog(
            onDismissRequest = { menuFor = null },
            icon = {
                Image(
                    bitmap = app.icon,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp)
                )
            },
            title = { Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            menuFor = null
                            open(app)
                        },
                        modifier = Modifier.fillMaxWidth().focusRequester(firstAction)
                    ) { Text("Open") }
                    FilledTonalButton(
                        onClick = {
                            viewModel.toggleFavorite(app.packageName)
                            menuFor = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (app.packageName in favoriteSet) "Remove from favorites" else "Add to favorites")
                    }
                    FilledTonalButton(
                        onClick = {
                            menuFor = null
                            context.openAppInfo(app.packageName)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("App info") }
                    if (!app.isSystem) {
                        FilledTonalButton(
                            onClick = {
                                menuFor = null
                                context.uninstallApp(app.packageName)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            )
                        ) { Text("Uninstall") }
                    }
                }
            },
            confirmButton = {}
        )
    }
}

@Composable
private fun TopBar(
    time: String,
    date: String,
    pad: Dp,
    onFocused: () -> Unit,
    onSettings: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = pad, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Row(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = time,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = date,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.weight(1f))
        FocusSurface(
            modifier = Modifier.size(56.dp),
            idleRadius = 28.dp,
            focusedRadius = 18.dp,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedScale = 1.14f,
            onFocusChange = { if (it) onFocused() },
            onClick = onSettings
        ) {
            Icon(
                imageVector = Icons.Rounded.Settings,
                contentDescription = "Settings",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.align(Alignment.Center).size(26.dp)
            )
        }
    }
}

@Composable
private fun Hero(
    focused: AppEntry?,
    greeting: String,
    hint: String,
    compact: Boolean,
    pad: Dp
) {
    val spatial = spring<IntOffset>(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow)
    val iconSize = if (compact) 64.dp else 104.dp

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (compact) 104.dp else 148.dp)
            .padding(horizontal = pad),
        contentAlignment = Alignment.CenterStart
    ) {
        AnimatedContent(
            targetState = focused,
            transitionSpec = {
                (fadeIn(tween(220)) + slideInVertically(spatial) { it / 3 }) togetherWith fadeOut(tween(120))
            },
            label = "hero"
        ) { app ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                if (app != null) {
                    Box(
                        modifier = Modifier
                            .size(iconSize)
                            .clip(RoundedCornerShape(28.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = app.icon,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(0.72f)
                        )
                    }
                }
                Column {
                    Text(
                        text = app?.label ?: greeting,
                        style = if (compact) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (app != null) hint else "Pick something to start",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun AppRow(
    title: String,
    apps: List<AppEntry>,
    cardWidth: Dp,
    pad: Dp,
    favorites: Set<String>,
    firstRequester: FocusRequester?,
    onFocused: (AppEntry) -> Unit,
    onOpen: (AppEntry) -> Unit,
    onMenu: (AppEntry) -> Unit
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = pad, top = 8.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = pad, vertical = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            itemsIndexed(apps, key = { _, app -> app.packageName }) { index, app ->
                AppCard(
                    app = app,
                    width = cardWidth,
                    isFavorite = app.packageName in favorites,
                    focusRequester = if (index == 0) firstRequester else null,
                    onFocused = { onFocused(app) },
                    onClick = { onOpen(app) },
                    onLongClick = { onMenu(app) }
                )
            }
        }
    }
}

@Composable
private fun AppCard(
    app: AppEntry,
    width: Dp,
    isFavorite: Boolean,
    focusRequester: FocusRequester?,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val labelColor by animateColorAsState(
        targetValue = if (focused) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(160),
        label = "label"
    )
    val banner = app.banner

    Column(modifier = Modifier.width(width)) {
        FocusSurface(
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
            idleRadius = 22.dp,
            focusedRadius = 34.dp,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusRequester = focusRequester,
            onFocusChange = {
                focused = it
                if (it) onFocused()
            },
            onLongClick = onLongClick,
            onClick = onClick
        ) {
            if (banner != null) {
                Image(
                    bitmap = banner,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.surfaceContainerHighest,
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = app.icon,
                        contentDescription = null,
                        modifier = Modifier.fillMaxHeight(0.56f).aspectRatio(1f)
                    )
                }
            }
            if (isFavorite) {
                Icon(
                    imageVector = Icons.Rounded.Star,
                    contentDescription = "Favorite",
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.align(Alignment.TopEnd).padding(10.dp).size(20.dp)
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = app.label,
            style = MaterialTheme.typography.labelLarge,
            color = labelColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 6.dp)
        )
    }
}
