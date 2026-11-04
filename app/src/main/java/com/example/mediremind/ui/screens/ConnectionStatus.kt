package com.example.mediremind.ui.screens

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.content.Intent
import android.provider.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ConnectionStatus() {
    val context = LocalContext.current
    val manager = remember { context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager }
    fun connected() = manager.getNetworkCapabilities(manager.activeNetwork)
        ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
    var online by remember { mutableStateOf(connected()) }
    DisposableEffect(manager) {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) { online = connected() }
            override fun onLost(network: Network) { online = connected() }
        }
        manager.registerDefaultNetworkCallback(callback)
        onDispose { manager.unregisterNetworkCallback(callback) }
    }
    if (!online) {
        Surface(color = MaterialTheme.colorScheme.errorContainer) {
            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                Text("Mất kết nối mạng. Dữ liệu đang xem có thể chưa cập nhật; lịch thuốc đã lưu vẫn dùng được.")
                TextButton(onClick = { runCatching { context.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS)) } }) {
                    Text("Mở cài đặt mạng")
                }
            }
        }
    }
}

@Composable
fun ReminderPermissionStatus() {
    val context = LocalContext.current
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    var revision by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) revision++
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val notifications = remember(revision) {
        androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
    val alarms = remember(revision) {
        android.os.Build.VERSION.SDK_INT < 31 ||
            context.getSystemService(android.app.AlarmManager::class.java).canScheduleExactAlarms()
    }
    if (!notifications || !alarms) {
        Column {
            Text("Nhắc thuốc chưa có đủ quyền: " +
                listOfNotNull(if (!notifications) "thông báo" else null, if (!alarms) "báo thức chính xác" else null).joinToString(", "))
            if (!notifications) TextButton(onClick = {
                runCatching { context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)) }
            }) { Text("Bật thông báo") }
            if (!alarms) TextButton(onClick = {
                if (android.os.Build.VERSION.SDK_INT >= 31) runCatching { context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    android.net.Uri.parse("package:${context.packageName}"))) }
            }) { Text("Cấp quyền báo thức") }
        }
    }
}
