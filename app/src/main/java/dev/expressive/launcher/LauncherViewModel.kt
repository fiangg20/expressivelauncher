package dev.expressive.launcher

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.AdaptiveIconDrawable
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.drawable.Drawable
import android.media.ExifInterface
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.graphics.Color as AndroidColor

class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("launcher", Context.MODE_PRIVATE)

    private val _apps = MutableStateFlow<List<AppEntry>>(emptyList())
    val apps: StateFlow<List<AppEntry>> = _apps.asStateFlow()

    private val _favorites = MutableStateFlow(readList("favorites"))
    val favorites: StateFlow<List<String>> = _favorites.asStateFlow()

    private val _recents = MutableStateFlow(readList("recents"))
    val recents: StateFlow<List<String>> = _recents.asStateFlow()

    private val _usage = MutableStateFlow(readUsage())
    val usage: StateFlow<Map<String, Int>> = _usage.asStateFlow()

    private val _homeSignal = MutableStateFlow(0)
    val homeSignal: StateFlow<Int> = _homeSignal.asStateFlow()

    private val _settings = MutableStateFlow(loadPrefs())
    val settings: StateFlow<LauncherPrefs> = _settings.asStateFlow()

    private val _photo = MutableStateFlow<ImageBitmap?>(null)
    val photo: StateFlow<ImageBitmap?> = _photo.asStateFlow()

    private var loadJob: Job? = null

    init {
        if (_settings.value.wallpaper == "photo") loadPhoto()
    }

    fun refresh() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _apps.value = withContext(Dispatchers.Default) { queryApps() }
        }
    }

    fun signalHome() {
        _homeSignal.update { it + 1 }
    }

    fun edit(transform: (LauncherPrefs) -> LauncherPrefs) {
        val next = transform(_settings.value)
        _settings.value = next
        savePrefs(next)
    }

    fun selectWallpaper(id: String) {
        edit { it.copy(wallpaper = id) }
        if (id == "photo" && _photo.value == null) loadPhoto()
    }

    fun resetCustomization() {
        edit { LauncherPrefs() }
    }

    fun toggleFavorite(packageName: String) {
        val current = _favorites.value
        val next = if (packageName in current) current - packageName else current + packageName
        _favorites.value = next
        writeList("favorites", next)
    }

    fun markLaunched(packageName: String) {
        val recents = (listOf(packageName) + _recents.value.filter { it != packageName }).take(12)
        _recents.value = recents
        writeList("recents", recents)
        val counts = _usage.value.toMutableMap()
        counts[packageName] = (counts[packageName] ?: 0) + 1
        _usage.value = counts
        prefs.edit().putString("usage", counts.entries.joinToString(",") { "${it.key}=${it.value}" }).apply()
    }

    fun importPhoto(uri: Uri, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val stored = withContext(Dispatchers.IO) { runCatching { storePhoto(uri) }.getOrDefault(false) }
            if (stored) {
                _photo.value = withContext(Dispatchers.IO) { decodePhoto() }
                selectWallpaper("photo")
            }
            onResult(stored)
        }
    }

    private fun loadPrefs(): LauncherPrefs = LauncherPrefs(
        wallpaper = prefs.getString("wallpaper", "ink").orEmpty().ifEmpty { "ink" },
        dim = prefs.getInt("dim", 1),
        accent = prefs.getInt("accent", 0),
        iconShape = prefs.getInt("iconShape", 0),
        calmMotion = prefs.getBoolean("calm", false),
        clockMode = prefs.getInt("clockMode", 0),
        timeFormat = prefs.getInt("timeFormat", 0),
        showDate = prefs.getBoolean("showDate", true),
        showGreeting = prefs.getBoolean("showGreeting", false),
        cardStyle = prefs.getInt("cardStyle", 0),
        cardSize = prefs.getInt("cardSize", 1),
        showLabels = prefs.getBoolean("showLabels", true),
        showFavorites = prefs.getBoolean("showFavorites", true),
        showRecent = prefs.getBoolean("showRecent", true),
        showMostUsed = prefs.getBoolean("showMostUsed", false),
        sort = prefs.getInt("sort", 0),
        showSystem = prefs.getBoolean("showSystem", false),
        hidden = readList("hidden").toSet()
    )

    private fun savePrefs(p: LauncherPrefs) {
        prefs.edit()
            .putString("wallpaper", p.wallpaper)
            .putInt("dim", p.dim)
            .putInt("accent", p.accent)
            .putInt("iconShape", p.iconShape)
            .putBoolean("calm", p.calmMotion)
            .putInt("clockMode", p.clockMode)
            .putInt("timeFormat", p.timeFormat)
            .putBoolean("showDate", p.showDate)
            .putBoolean("showGreeting", p.showGreeting)
            .putInt("cardStyle", p.cardStyle)
            .putInt("cardSize", p.cardSize)
            .putBoolean("showLabels", p.showLabels)
            .putBoolean("showFavorites", p.showFavorites)
            .putBoolean("showRecent", p.showRecent)
            .putBoolean("showMostUsed", p.showMostUsed)
            .putInt("sort", p.sort)
            .putBoolean("showSystem", p.showSystem)
            .putString("hidden", p.hidden.joinToString(","))
            .apply()
    }

    private fun readUsage(): Map<String, Int> =
        prefs.getString("usage", "").orEmpty().split(",").mapNotNull { entry ->
            val parts = entry.split("=")
            val count = parts.getOrNull(1)?.toIntOrNull()
            if (parts.size == 2 && count != null && parts[0].isNotBlank()) parts[0] to count else null
        }.toMap()

    private fun photoFile(): File = File(getApplication<Application>().filesDir, "wallpaper.jpg")

    private fun loadPhoto() {
        viewModelScope.launch {
            _photo.value = withContext(Dispatchers.IO) { decodePhoto() }
        }
    }

    private fun decodePhoto(): ImageBitmap? {
        val file = photoFile()
        if (!file.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > 2048) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeFile(file.path, options)?.asImageBitmap()
    }

    private fun storePhoto(uri: Uri): Boolean {
        val resolver = getApplication<Application>().contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > 2400) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: return false
        val degrees = runCatching {
            resolver.openInputStream(uri)?.use { stream ->
                when (ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            }
        }.getOrNull() ?: 0f
        val bitmap = if (degrees != 0f) {
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, Matrix().apply { postRotate(degrees) }, true)
        } else {
            decoded
        }
        photoFile().outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        return true
    }

    private fun readList(key: String): List<String> =
        prefs.getString(key, "").orEmpty().split(",").filter { it.isNotBlank() }

    private fun writeList(key: String, value: List<String>) {
        prefs.edit().putString(key, value.joinToString(",")).apply()
    }

    @Suppress("DEPRECATION")
    private fun resolve(pm: PackageManager, category: String): List<ResolveInfo> =
        pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(category), 0)

    private fun renderIcon(drawable: Drawable): Bitmap {
        val size = 160
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        if (drawable is AdaptiveIconDrawable) {
            val layer = (size * 108f / 72f).toInt()
            val offset = -(layer - size) / 2
            drawable.background?.let {
                it.setBounds(offset, offset, offset + layer, offset + layer)
                it.draw(canvas)
            }
            drawable.foreground?.let {
                it.setBounds(offset, offset, offset + layer, offset + layer)
                it.draw(canvas)
            }
        } else {
            canvas.drawColor(0xFF2B2E35.toInt())
            val inset = (size * 0.16f).toInt()
            drawable.setBounds(inset, inset, size - inset, size - inset)
            drawable.draw(canvas)
        }
        return bitmap
    }

    private fun tintOf(bitmap: Bitmap): Color {
        val pixel = Bitmap.createScaledBitmap(bitmap, 1, 1, true).getPixel(0, 0)
        if (AndroidColor.alpha(pixel) < 40) return Color(0xFF6B7280)
        val hsv = FloatArray(3)
        AndroidColor.colorToHSV(pixel, hsv)
        hsv[1] = (hsv[1] * 1.2f).coerceAtMost(1f)
        hsv[2] = hsv[2].coerceIn(0.45f, 0.9f)
        return Color(AndroidColor.HSVToColor(hsv))
    }

    @Suppress("DEPRECATION")
    private fun queryApps(): List<AppEntry> {
        val context = getApplication<Application>()
        val pm = context.packageManager
        val own = context.packageName

        val tvInfos = resolve(pm, Intent.CATEGORY_LEANBACK_LAUNCHER)
        val tvPackages = tvInfos.map { it.activityInfo.packageName }.toSet()
        val phoneInfos = resolve(pm, Intent.CATEGORY_LAUNCHER)
            .filter { it.activityInfo.packageName !in tvPackages }

        return (tvInfos.map { it to true } + phoneInfos.map { it to false })
            .filter { it.first.activityInfo.packageName != own }
            .distinctBy { it.first.activityInfo.packageName }
            .mapNotNull { (info, isTv) ->
                runCatching {
                    val activity = info.activityInfo
                    val flags = activity.applicationInfo.flags
                    val iconBitmap = renderIcon(info.loadIcon(pm))
                    AppEntry(
                        packageName = activity.packageName,
                        activityName = activity.name,
                        label = info.loadLabel(pm).toString(),
                        icon = iconBitmap.asImageBitmap(),
                        banner = if (isTv) {
                            runCatching { activity.loadBanner(pm)?.toBitmap(320, 180)?.asImageBitmap() }.getOrNull()
                        } else {
                            null
                        },
                        tint = tintOf(iconBitmap),
                        isTv = isTv,
                        isSystem = (flags and ApplicationInfo.FLAG_SYSTEM) != 0 &&
                            (flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0,
                        installedAt = runCatching { pm.getPackageInfo(activity.packageName, 0).firstInstallTime }
                            .getOrDefault(0L)
                    )
                }.getOrNull()
            }
            .sortedBy { it.label.lowercase() }
    }
}
