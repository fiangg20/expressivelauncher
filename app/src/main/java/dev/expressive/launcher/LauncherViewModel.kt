package dev.expressive.launcher

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("launcher", Context.MODE_PRIVATE)

    private val _apps = MutableStateFlow<List<AppEntry>>(emptyList())
    val apps: StateFlow<List<AppEntry>> = _apps.asStateFlow()

    private val _favorites = MutableStateFlow(readList("favorites"))
    val favorites: StateFlow<List<String>> = _favorites.asStateFlow()

    private val _recents = MutableStateFlow(readList("recents"))
    val recents: StateFlow<List<String>> = _recents.asStateFlow()

    private val _homeSignal = MutableStateFlow(0)
    val homeSignal: StateFlow<Int> = _homeSignal.asStateFlow()

    private var loadJob: Job? = null

    fun refresh() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _apps.value = withContext(Dispatchers.Default) { queryApps() }
        }
    }

    fun signalHome() {
        _homeSignal.update { it + 1 }
    }

    fun toggleFavorite(packageName: String) {
        val current = _favorites.value
        val next = if (packageName in current) current - packageName else current + packageName
        _favorites.value = next
        writeList("favorites", next)
    }

    fun markLaunched(packageName: String) {
        val next = (listOf(packageName) + _recents.value.filter { it != packageName }).take(12)
        _recents.value = next
        writeList("recents", next)
    }

    private fun readList(key: String): List<String> =
        prefs.getString(key, "").orEmpty().split(",").filter { it.isNotBlank() }

    private fun writeList(key: String, value: List<String>) {
        prefs.edit().putString(key, value.joinToString(",")).apply()
    }

    @Suppress("DEPRECATION")
    private fun resolve(pm: PackageManager, category: String): List<ResolveInfo> =
        pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(category), 0)

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
                    AppEntry(
                        packageName = activity.packageName,
                        activityName = activity.name,
                        label = info.loadLabel(pm).toString(),
                        icon = info.loadIcon(pm).toBitmap(128, 128).asImageBitmap(),
                        banner = if (isTv) {
                            runCatching { activity.loadBanner(pm)?.toBitmap(320, 180)?.asImageBitmap() }.getOrNull()
                        } else {
                            null
                        },
                        isTv = isTv,
                        isSystem = (activity.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    )
                }.getOrNull()
            }
            .sortedBy { it.label.lowercase() }
    }
}
