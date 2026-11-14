package dev.expressive.launcher.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.BatteryManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

data class StatusInfo(val network: Int, val battery: Int, val charging: Boolean)

private fun readBattery(intent: Intent, old: StatusInfo): StatusInfo {
    val present = intent.getBooleanExtra(BatteryManager.EXTRA_PRESENT, false)
    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
    val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
    val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
    val percent = if (present && level >= 0 && scale > 0) level * 100 / scale else -1
    return old.copy(battery = percent, charging = charging)
}

@Composable
fun rememberStatus(): State<StatusInfo> {
    val context = LocalContext.current
    val state = remember { mutableStateOf(StatusInfo(0, -1, false)) }
    DisposableEffect(context) {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                val kind = when {
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> 1
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> 2
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> 3
                    else -> 4
                }
                state.value = state.value.copy(network = kind)
            }

            override fun onLost(network: Network) {
                state.value = state.value.copy(network = 0)
            }
        }
        runCatching { manager.registerDefaultNetworkCallback(callback) }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                if (intent != null) state.value = readBattery(intent, state.value)
            }
        }
        val sticky = ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        if (sticky != null) state.value = readBattery(sticky, state.value)

        onDispose {
            runCatching { manager.unregisterNetworkCallback(callback) }
            runCatching { context.unregisterReceiver(receiver) }
        }
    }
    return state
}

@Composable
fun StatusChips(status: State<StatusInfo>) {
    val info = status.value
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val networkIcon = when (info.network) {
            0 -> Icons.Rounded.WifiOff
            1 -> Icons.Rounded.Wifi
            else -> Icons.Rounded.Language
        }
        Icon(
            imageVector = networkIcon,
            contentDescription = "Network",
            tint = muted,
            modifier = Modifier.size(22.dp)
        )
        if (info.battery >= 0) {
            Icon(
                imageVector = if (info.charging) Icons.Rounded.BatteryChargingFull else Icons.Rounded.BatteryFull,
                contentDescription = "Battery",
                tint = muted,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = "${info.battery}%",
                style = MaterialTheme.typography.labelLarge,
                color = muted
            )
        }
    }
}
