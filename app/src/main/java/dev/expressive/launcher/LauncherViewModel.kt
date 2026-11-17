package dev.expressive.launcher

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.media.tv.TvInputManager
import android.media.ExifInterface
import android.net.Uri
import android.media.AudioManager
import android.view.KeyEvent
import android.os.SystemClock
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.roundToInt
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
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

    private val _names = MutableStateFlow(readNames())
    val names: StateFlow<Map<String, String>> = _names.asStateFlow()

    private val _categories = MutableStateFlow(readCategories())
    val categories: StateFlow<Map<String, Int>> = _categories.asStateFlow()

    private val _quickSignal = MutableStateFlow(0)
    val quickSignal: StateFlow<Int> = _quickSignal.asStateFlow()

    private val _collections = MutableStateFlow(readCollections())
    val collections: StateFlow<Map<String, List<String>>> = _collections.asStateFlow()

    private val _inputs = MutableStateFlow<List<AppEntry>>(emptyList())
    val inputs: StateFlow<List<AppEntry>> = _inputs.asStateFlow()

    private val _slots = MutableStateFlow(readSlots())
    val slots: StateFlow<Map<String, List<Int>>> = _slots.asStateFlow()

    private val _appOrder = MutableStateFlow(readList("appOrder"))
    val appOrder: StateFlow<List<String>> = _appOrder.asStateFlow()

    private val _scenes = MutableStateFlow(readScenes())
    val scenes: StateFlow<List<Scene>> = _scenes.asStateFlow()

    private val _slides = MutableStateFlow<List<ImageBitmap>>(emptyList())
    val slides: StateFlow<List<ImageBitmap>> = _slides.asStateFlow()

    private val _sleepEnd = MutableStateFlow(0L)
    val sleepEnd: StateFlow<Long> = _sleepEnd.asStateFlow()

    private val _colorKeys = MutableSharedFlow<Int>(extraBufferCapacity = 4)
    val colorKeys: SharedFlow<Int> = _colorKeys.asSharedFlow()

    private var sleepJob: Job? = null

    private val _weather = MutableStateFlow<WeatherInfo?>(null)
    val weather: StateFlow<WeatherInfo?> = _weather.asStateFlow()

    private val _ambient = MutableStateFlow(false)
    val ambient: StateFlow<Boolean> = _ambient.asStateFlow()

    private var lastInput = SystemClock.elapsedRealtime()
    private var ambientSince = 0L
    private var foreground = false

    private var loadJob: Job? = null

    init {
        if (_settings.value.wallpaper == "photo") loadPhoto()
        viewModelScope.launch { _slides.value = withContext(Dispatchers.IO) { decodeSlides() } }
        viewModelScope.launch {
            while (true) {
                refreshWeather()
                delay(30 * 60_000L)
            }
        }
        viewModelScope.launch {
            while (true) {
                delay(4000)
                val minutes = when (_settings.value.ambient) {
                    1 -> 2
                    2 -> 5
                    3 -> 10
                    else -> 0
                }
                val idle = SystemClock.elapsedRealtime() - lastInput
                if (minutes > 0 && foreground && !_ambient.value && idle > minutes * 60_000L) startAmbient()
            }
        }
    }

    private var okHeld = false
    private var swallowOk = false

    fun handleOk(down: Boolean, up: Boolean): Boolean {
        if (down) okHeld = true
        val swallow = swallowOk
        if (up) {
            okHeld = false
            swallowOk = false
        }
        return swallow
    }

    fun armOkGuard() {
        if (okHeld) swallowOk = true
    }

    private class Place(val name: String, val lat: Float, val lon: Float)

    private fun httpGet(address: String): String {
        val connection = URL(address).openConnection() as HttpURLConnection
        connection.connectTimeout = 8000
        connection.readTimeout = 8000
        return try {
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun geocode(query: String): Place? {
        val body = httpGet(
            "https://geocoding-api.open-meteo.com/v1/search?name=" +
                URLEncoder.encode(query, "UTF-8") + "&count=1&language=en&format=json"
        )
        val results = JSONObject(body).optJSONArray("results") ?: return null
        if (results.length() == 0) return null
        val first = results.getJSONObject(0)
        val label = listOf(first.optString("name"), first.optString("country"))
            .filter { it.isNotBlank() }
            .joinToString(", ")
        return Place(label, first.getDouble("latitude").toFloat(), first.getDouble("longitude").toFloat())
    }

    private fun fetchWeather(p: LauncherPrefs): WeatherInfo {
        val unit = if (p.fahrenheit) "fahrenheit" else "celsius"
        val body = httpGet(
            "https://api.open-meteo.com/v1/forecast?latitude=${p.weatherLat}&longitude=${p.weatherLon}" +
                "&current=temperature_2m,weather_code&temperature_unit=$unit"
        )
        val current = JSONObject(body).getJSONObject("current")
        return WeatherInfo(current.getDouble("temperature_2m").roundToInt(), current.getInt("weather_code"))
    }

    fun refreshWeather() {
        val p = _settings.value
        if (!p.showWeather || (p.weatherLat == 0f && p.weatherLon == 0f)) {
            _weather.value = null
            return
        }
        viewModelScope.launch {
            val fetched = withContext(Dispatchers.IO) { runCatching { fetchWeather(p) }.getOrNull() }
            if (fetched != null) _weather.value = fetched
        }
    }

    fun lookupCity(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch {
            val found = withContext(Dispatchers.IO) { runCatching { geocode(query.trim()) }.getOrNull() }
            val app = getApplication<Application>()
            if (found == null) {
                app.toast("City not found")
            } else {
                edit { it.copy(showWeather = true, weatherName = found.name, weatherLat = found.lat, weatherLon = found.lon) }
                app.toast("Weather set to " + found.name)
            }
        }
    }

    private fun readCollections(): Map<String, List<String>> = runCatching {
        val json = JSONObject(prefs.getString("collections", "{}").orEmpty())
        val result = LinkedHashMap<String, List<String>>()
        for (key in json.keys()) {
            val array = json.getJSONArray(key)
            result[key] = (0 until array.length()).map { array.getString(it) }
        }
        result as Map<String, List<String>>
    }.getOrDefault(emptyMap())

    private fun writeCollections(map: Map<String, List<String>>) {
        val json = JSONObject()
        for ((name, list) in map) json.put(name, JSONArray(list))
        prefs.edit().putString("collections", json.toString()).apply()
    }

    fun toggleInCollection(name: String, packageName: String) {
        val next = LinkedHashMap(_collections.value)
        val list = next[name].orEmpty()
        next[name] = if (packageName in list) list - packageName else list + packageName
        _collections.value = next
        writeCollections(next)
    }

    fun createCollection(name: String, firstPackage: String?) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        val next = LinkedHashMap(_collections.value)
        var list = next[clean].orEmpty()
        if (firstPackage != null && firstPackage !in list) list = list + firstPackage
        next[clean] = list
        _collections.value = next
        writeCollections(next)
    }

    fun deleteCollection(name: String) {
        val next = LinkedHashMap(_collections.value)
        next.remove(name)
        _collections.value = next
        writeCollections(next)
    }

    private fun queryInputs(): List<AppEntry> {
        val context = getApplication<Application>()
        val manager = context.getSystemService(Context.TV_INPUT_SERVICE) as? TvInputManager ?: return emptyList()
        return runCatching {
            manager.tvInputList.filter { it.isPassthroughInput }.mapNotNull { input ->
                runCatching {
                    val label = input.loadCustomLabel(context)?.toString()?.takeIf { it.isNotBlank() }
                        ?: input.loadLabel(context).toString()
                    val drawable = input.loadIcon(context) ?: ColorDrawable(0xFF3B4252.toInt())
                    AppEntry(
                        packageName = "input:" + input.id,
                        activityName = input.id,
                        label = label,
                        systemLabel = label,
                        icon = renderIcon(drawable).asImageBitmap(),
                        banner = null,
                        tint = Color(0xFF5E81AC),
                        category = -1,
                        autoCategory = -1,
                        isTv = true,
                        isSystem = false,
                        installedAt = 0L
                    )
                }.getOrNull()
            }
        }.getOrDefault(emptyList())
    }

    private fun readSlots(): Map<String, List<Int>> = runCatching {
        val json = JSONObject(prefs.getString("slots", "{}").orEmpty())
        val result = HashMap<String, List<Int>>()
        for (key in json.keys()) {
            val array = json.getJSONArray(key)
            val counts = (0 until array.length()).map { array.getInt(it) }
            result[key] = (counts + List(8) { 0 }).take(8)
        }
        result as Map<String, List<Int>>
    }.getOrDefault(emptyMap())

    private fun readScenes(): List<Scene> = runCatching {
        val array = JSONArray(prefs.getString("scenes", "[]").orEmpty())
        (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            Scene(item.getString("name"), item.getString("pkg"), item.getInt("vol"), item.getInt("dim"))
        }
    }.getOrDefault(emptyList())

    private fun writeScenes(list: List<Scene>) {
        val array = JSONArray()
        list.forEach {
            array.put(JSONObject().put("name", it.name).put("pkg", it.packageName).put("vol", it.volume).put("dim", it.dim))
        }
        prefs.edit().putString("scenes", array.toString()).apply()
    }

    fun saveScene(scene: Scene) {
        val clean = scene.copy(name = scene.name.trim())
        if (clean.name.isEmpty()) return
        val next = _scenes.value.filter { it.name != clean.name } + clean
        _scenes.value = next
        writeScenes(next)
    }

    fun deleteScene(name: String) {
        val next = _scenes.value.filter { it.name != name }
        _scenes.value = next
        writeScenes(next)
    }

    fun runScene(name: String) {
        val scene = _scenes.value.firstOrNull { it.name == name } ?: return
        val context = getApplication<Application>()
        if (scene.volume >= 0) {
            val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            runCatching { audio.setStreamVolume(AudioManager.STREAM_MUSIC, max * scene.volume / 100, 0) }
        }
        if (scene.dim >= 0) edit { it.copy(dim = scene.dim) }
        val app = _apps.value.firstOrNull { it.packageName == scene.packageName }
        if (app != null) {
            markLaunched(app.packageName)
            context.launchApp(app)
        }
    }

    fun runFirstScene() {
        _scenes.value.firstOrNull()?.let { runScene(it.name) }
    }

    fun colorKey(index: Int) {
        _colorKeys.tryEmit(index)
    }

    fun setSleep(minutes: Int) {
        sleepJob?.cancel()
        if (minutes <= 0) {
            _sleepEnd.value = 0L
            return
        }
        _sleepEnd.value = System.currentTimeMillis() + minutes * 60_000L
        sleepJob = viewModelScope.launch {
            delay(minutes * 60_000L)
            fadeAndSleep()
        }
    }

    fun cycleSleep() {
        val remaining = ((_sleepEnd.value - System.currentTimeMillis()) / 60_000L).toInt()
        val next = when {
            _sleepEnd.value == 0L || remaining <= 0 -> 15
            remaining <= 15 -> 30
            remaining <= 30 -> 60
            remaining <= 60 -> 90
            else -> 0
        }
        setSleep(next)
    }

    private suspend fun fadeAndSleep() {
        val context = getApplication<Application>()
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val original = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        for (step in 9 downTo 0) {
            runCatching { audio.setStreamVolume(AudioManager.STREAM_MUSIC, original * step / 10, 0) }
            delay(1200)
        }
        runCatching {
            audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE))
            audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PAUSE))
            audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_STOP))
            audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_STOP))
        }
        delay(600)
        runCatching { audio.setStreamVolume(AudioManager.STREAM_MUSIC, original, 0) }
        runCatching {
            context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        delay(800)
        _sleepEnd.value = 0L
        startAmbient()
    }

    fun moveInCollection(name: String, packageName: String, delta: Int) {
        val next = LinkedHashMap(_collections.value)
        val list = next[name].orEmpty().toMutableList()
        val index = list.indexOf(packageName)
        val target = index + delta
        if (index < 0 || target !in list.indices) return
        list.removeAt(index)
        list.add(target, packageName)
        next[name] = list
        _collections.value = next
        writeCollections(next)
    }

    fun moveInOrder(packageName: String, neighbor: String?, base: List<String>) {
        if (neighbor == null) return
        val known = _appOrder.value
        val merged = (known.filter { it in base } + base.filter { it !in known }).toMutableList()
        val first = merged.indexOf(packageName)
        val second = merged.indexOf(neighbor)
        if (first < 0 || second < 0) return
        merged[first] = neighbor
        merged[second] = packageName
        _appOrder.value = merged
        writeList("appOrder", merged)
        if (_settings.value.sort != 3) edit { it.copy(sort = 3) }
    }

    private fun slideDir(): File = File(getApplication<Application>().filesDir, "slides")

    private fun decodeUriRotated(uri: Uri, maxSide: Int): Bitmap? {
        val resolver = getApplication<Application>().contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > maxSide * 2) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) } ?: return null
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
        return if (degrees != 0f) {
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, Matrix().apply { postRotate(degrees) }, true)
        } else {
            decoded
        }
    }

    private fun storeSlides(uris: List<Uri>): Int {
        val dir = slideDir()
        dir.mkdirs()
        dir.listFiles()?.forEach { it.delete() }
        var saved = 0
        for (uri in uris.take(10)) {
            val bitmap = runCatching { decodeUriRotated(uri, 1280) }.getOrNull() ?: continue
            File(dir, "slide_$saved.jpg").outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 86, it) }
            saved++
        }
        return saved
    }

    private fun decodeSlides(): List<ImageBitmap> {
        val files = slideDir().listFiles()?.sortedBy { it.name } ?: return emptyList()
        return files.mapNotNull { file ->
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.path, bounds)
                var sample = 1
                while (max(bounds.outWidth, bounds.outHeight) / sample > 1400) sample *= 2
                BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
            }.getOrNull()
        }
    }

    fun importSlides(uris: List<Uri>, onResult: (Int) -> Unit) {
        viewModelScope.launch {
            val count = withContext(Dispatchers.IO) { runCatching { storeSlides(uris) }.getOrDefault(0) }
            if (count > 0) _slides.value = withContext(Dispatchers.IO) { decodeSlides() }
            onResult(count)
        }
    }

    fun toggleQuick() {
        _quickSignal.update { it + 1 }
    }

    fun setCategory(packageName: String, id: Int?) {
        val next = _categories.value.toMutableMap()
        if (id == null) next.remove(packageName) else next[packageName] = id
        _categories.value = next
        prefs.edit().putString("categories", JSONObject(next).toString()).apply()
    }

    fun touch() {
        lastInput = SystemClock.elapsedRealtime()
    }

    fun setForeground(value: Boolean) {
        foreground = value
        okHeld = false
        swallowOk = false
        touch()
        if (!value) _ambient.value = false
    }

    fun startAmbient() {
        touch()
        ambientSince = SystemClock.elapsedRealtime()
        _ambient.value = true
    }

    fun wakeIfAmbient(): Boolean {
        if (!_ambient.value) return false
        if (SystemClock.elapsedRealtime() - ambientSince < 700) return true
        _ambient.value = false
        touch()
        return true
    }

    fun rename(packageName: String, name: String) {
        val next = _names.value.toMutableMap()
        if (name.isBlank()) next.remove(packageName) else next[packageName] = name.trim()
        _names.value = next
        prefs.edit().putString("names", JSONObject(next).toString()).apply()
    }

    fun moveFavorite(packageName: String, delta: Int) {
        val list = _favorites.value.toMutableList()
        val index = list.indexOf(packageName)
        val target = index + delta
        if (index < 0 || target !in list.indices) return
        list.removeAt(index)
        list.add(target, packageName)
        _favorites.value = list
        writeList("favorites", list)
    }

    fun refresh() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _apps.value = withContext(Dispatchers.Default) { queryApps() }
            _inputs.value = withContext(Dispatchers.Default) { queryInputs() }
        }
    }

    fun signalHome() {
        _homeSignal.update { it + 1 }
    }

    fun edit(transform: (LauncherPrefs) -> LauncherPrefs) {
        val old = _settings.value
        val next = transform(old)
        _settings.value = next
        savePrefs(next)
        if (next.showWeather != old.showWeather || next.weatherLat != old.weatherLat ||
            next.weatherLon != old.weatherLon || next.fahrenheit != old.fahrenheit
        ) {
            refreshWeather()
        }
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
        val slot = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) / 3
        val slotMap = _slots.value.toMutableMap()
        val slotCounts = (slotMap[packageName] ?: List(8) { 0 }).toMutableList()
        slotCounts[slot] = slotCounts[slot] + 1
        slotMap[packageName] = slotCounts
        _slots.value = slotMap
        prefs.edit().putString("slots", JSONObject(slotMap.mapValues { JSONArray(it.value) }).toString()).apply()
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
        motion = prefs.getInt("motion", 0),
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
        showStatus = prefs.getBoolean("showStatus", true),
        focusStyle = prefs.getInt("focusStyle", 0),
        focusLabels = prefs.getBoolean("focusLabels", false),
        navSounds = prefs.getBoolean("navSounds", false),
        rowDepth = prefs.getBoolean("rowDepth", true),
        ripple = prefs.getBoolean("ripple", true),
        lean = prefs.getBoolean("lean", true),
        showQuickRow = prefs.getBoolean("showQuickRow", true),
        showSuggested = prefs.getBoolean("showSuggested", true),
        showScenes = prefs.getBoolean("showScenes", true),
        folderMode = prefs.getBoolean("folderMode", false),
        autoTheme = prefs.getBoolean("autoTheme", false),
        ambientStyle = prefs.getInt("ambientStyle", 0),
        keyRed = prefs.getInt("keyRed", 1),
        keyGreen = prefs.getInt("keyGreen", 2),
        keyYellow = prefs.getInt("keyYellow", 5),
        keyBlue = prefs.getInt("keyBlue", 4),
        kidsMode = prefs.getBoolean("kidsMode", false),
        kidsPin = prefs.getString("kidsPin", "").orEmpty(),
        kidsApps = readList("kidsApps").toSet(),
        showCollections = prefs.getBoolean("showCollections", true),
        showInputs = prefs.getBoolean("showInputs", true),
        showWeather = prefs.getBoolean("showWeather", false),
        weatherName = prefs.getString("weatherName", "").orEmpty(),
        weatherLat = prefs.getFloat("weatherLat", 0f),
        weatherLon = prefs.getFloat("weatherLon", 0f),
        fahrenheit = prefs.getBoolean("fahrenheit", false),
        userName = prefs.getString("userName", "").orEmpty(),
        ambient = prefs.getInt("ambient", 0),
        showAllRow = prefs.getBoolean("showAllRow", true),
        groupCategories = prefs.getBoolean("groupCategories", false),
        hidden = readList("hidden").toSet()
    )

    private fun savePrefs(p: LauncherPrefs) {
        prefs.edit()
            .putString("wallpaper", p.wallpaper)
            .putInt("dim", p.dim)
            .putInt("accent", p.accent)
            .putInt("iconShape", p.iconShape)
            .putInt("motion", p.motion)
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
            .putBoolean("showStatus", p.showStatus)
            .putInt("focusStyle", p.focusStyle)
            .putBoolean("focusLabels", p.focusLabels)
            .putBoolean("navSounds", p.navSounds)
            .putBoolean("rowDepth", p.rowDepth)
            .putBoolean("ripple", p.ripple)
            .putBoolean("lean", p.lean)
            .putBoolean("showQuickRow", p.showQuickRow)
            .putBoolean("showSuggested", p.showSuggested)
            .putBoolean("showScenes", p.showScenes)
            .putBoolean("folderMode", p.folderMode)
            .putBoolean("autoTheme", p.autoTheme)
            .putInt("ambientStyle", p.ambientStyle)
            .putInt("keyRed", p.keyRed)
            .putInt("keyGreen", p.keyGreen)
            .putInt("keyYellow", p.keyYellow)
            .putInt("keyBlue", p.keyBlue)
            .putBoolean("kidsMode", p.kidsMode)
            .putString("kidsPin", p.kidsPin)
            .putString("kidsApps", p.kidsApps.joinToString(","))
            .putBoolean("showCollections", p.showCollections)
            .putBoolean("showInputs", p.showInputs)
            .putBoolean("showWeather", p.showWeather)
            .putString("weatherName", p.weatherName)
            .putFloat("weatherLat", p.weatherLat)
            .putFloat("weatherLon", p.weatherLon)
            .putBoolean("fahrenheit", p.fahrenheit)
            .putString("userName", p.userName)
            .putInt("ambient", p.ambient)
            .putBoolean("showAllRow", p.showAllRow)
            .putBoolean("groupCategories", p.groupCategories)
            .putString("hidden", p.hidden.joinToString(","))
            .apply()
    }

    private fun readUsage(): Map<String, Int> =
        prefs.getString("usage", "").orEmpty().split(",").mapNotNull { entry ->
            val parts = entry.split("=")
            val count = parts.getOrNull(1)?.toIntOrNull()
            if (parts.size == 2 && count != null && parts[0].isNotBlank()) parts[0] to count else null
        }.toMap()

    private fun readNames(): Map<String, String> = runCatching {
        val json = JSONObject(prefs.getString("names", "{}").orEmpty())
        json.keys().asSequence().associateWith { json.getString(it) }
    }.getOrDefault(emptyMap())

    private fun readCategories(): Map<String, Int> = runCatching {
        val json = JSONObject(prefs.getString("categories", "{}").orEmpty())
        json.keys().asSequence().associateWith { json.getInt(it) }
    }.getOrDefault(emptyMap())

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
                    val appLabel = info.loadLabel(pm).toString()
                    val autoCategory = AppCategories.auto(activity.applicationInfo.category, appLabel, activity.packageName)
                    AppEntry(
                        packageName = activity.packageName,
                        activityName = activity.name,
                        label = appLabel,
                        systemLabel = appLabel,
                        icon = iconBitmap.asImageBitmap(),
                        banner = if (isTv) {
                            runCatching { activity.loadBanner(pm)?.toBitmap(320, 180)?.asImageBitmap() }.getOrNull()
                        } else {
                            null
                        },
                        tint = tintOf(iconBitmap),
                        category = autoCategory,
                        autoCategory = autoCategory,
                        isTv = isTv,
                        isSystem = (flags and ApplicationInfo.FLAG_SYSTEM) != 0 &&
                            (flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0 &&
                            activity.packageName !in ImportantApps.packages,
                        installedAt = runCatching { pm.getPackageInfo(activity.packageName, 0).firstInstallTime }
                            .getOrDefault(0L)
                    )
                }.getOrNull()
            }
            .sortedBy { it.label.lowercase() }
    }
}
