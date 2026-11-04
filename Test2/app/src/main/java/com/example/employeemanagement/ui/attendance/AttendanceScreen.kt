package com.example.employeemanagement.ui.attendance

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.employeemanagement.data.local.entity.WorkLog
import java.util.Locale

@Composable
fun AttendanceScreen(viewModel: AttendanceViewModel, onBack: (() -> Unit)? = null) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let { snackbarHost.showSnackbar(it); viewModel.clearMessage() }
    }
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                onBack?.let { action -> TextButton(onClick = action) { Text("← Quay lại") } }
                Text("Chấm công", style = MaterialTheme.typography.headlineMedium)
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Hôm nay: ${state.date}", style = MaterialTheme.typography.titleLarge)
                        Text("Giờ hiện tại: ${state.currentTime}")
                        Text("Check In: ${state.today?.checkInTime ?: "—"}")
                        Text("Check Out: ${state.today?.checkOutTime ?: "—"}")
                        Text("Số giờ làm: ${hours(state.today?.totalHours ?: 0.0)} giờ")
                        Text(attendanceStatus(state.today))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(onClick = viewModel::checkIn, enabled = state.canCheckIn, modifier = Modifier.weight(1f)) { Text("Check In") }
                            Button(onClick = viewModel::checkOut, enabled = state.canCheckOut, modifier = Modifier.weight(1f)) { Text("Check Out") }
                        }
                        Text("Giờ làm được chốt khi Check Out. Mỗi ngày chỉ Check In một lần.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (state.isLoading || state.isSaving) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            state.error?.let { error -> item { Text(error, color = MaterialTheme.colorScheme.error) } }
            item {
                Text("Lịch sử tháng ${state.selectedMonth}", style = MaterialTheme.typography.titleLarge)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { viewModel.moveMonth(-1) }) { Text("‹ Trước") }
                    TextButton(onClick = viewModel::showCurrentMonth) { Text("Tháng này") }
                    TextButton(onClick = { viewModel.moveMonth(1) }) { Text("Sau ›") }
                }
                Text("Tổng giờ lũy kế: ${hours(state.monthlyHours)} giờ", style = MaterialTheme.typography.titleMedium)
                Text("Chỉ cộng các ngày đã COMPLETED.", style = MaterialTheme.typography.bodySmall)
            }
            if (!state.isLoading && state.history.isEmpty()) item { Text("Chưa có chấm công trong tháng này.") }
            items(state.history, key = { it.id }) { log ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(log.workDate, style = MaterialTheme.typography.titleMedium)
                        Text("Giờ vào: ${log.checkInTime}")
                        Text("Giờ ra: ${log.checkOutTime ?: "Chưa Check Out"}")
                        Text("Tổng giờ: ${hours(log.totalHours)} giờ")
                        Text(attendanceStatus(log))
                    }
                }
            }
        }
    }
}

private fun hours(value: Double): String = String.format(Locale.forLanguageTag("vi-VN"), "%.2f", value)

private fun attendanceStatus(log: WorkLog?): String = when {
    log == null -> "Chưa Check In"
    log.status == "COMPLETED" -> "COMPLETED — Đã hoàn tất"
    log.status == "CHECKED_IN" && log.checkOutTime == null -> "CHECKED_IN — Đang làm việc"
    else -> "Trạng thái: ${log.status}"
}
