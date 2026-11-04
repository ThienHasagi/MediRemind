package com.example.employeemanagement.ui.salary

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.employeemanagement.data.local.model.SalaryDetails
import java.text.NumberFormat
import java.util.Locale

@Composable
fun AdminSalaryScreen(viewModel: SalaryViewModel, onBack: (() -> Unit)? = null) {
    SalaryScreen(viewModel, isAdmin = true, onBack)
}

@Composable
fun EmployeeSalaryScreen(viewModel: SalaryViewModel, onBack: (() -> Unit)? = null) {
    SalaryScreen(viewModel, isAdmin = false, onBack)
}

@Composable
private fun SalaryScreen(viewModel: SalaryViewModel, isAdmin: Boolean, onBack: (() -> Unit)?) {
    val period by viewModel.period.collectAsStateWithLifecycle()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val salaries by viewModel.salaries.collectAsStateWithLifecycle()
    var monthInput by rememberSaveable(period) { mutableStateOf(period.month.toString()) }
    var yearInput by rememberSaveable(period) { mutableStateOf(period.year.toString()) }
    var confirmCalculation by rememberSaveable { mutableStateOf(false) }
    var payingId by rememberSaveable { mutableStateOf<Long?>(null) }
    val snackbarHost = remember { SnackbarHostState() }
    val numberFormat = remember {
        NumberFormat.getNumberInstance(Locale.forLanguageTag("vi-VN")).apply { maximumFractionDigits = 2 }
    }
    val periodApplied = monthInput.trim().toIntOrNull() == period.month && yearInput.trim().toIntOrNull() == period.year

    LaunchedEffect(state.message) {
        state.message?.let { snackbarHost.showSnackbar(it); viewModel.clearMessage() }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).imePadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                onBack?.let { action -> TextButton(onClick = action) { Text("← Quay lại") } }
                Text(if (isAdmin) "Quản lý bảng lương" else "Bảng lương của tôi", style = MaterialTheme.typography.headlineSmall)
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = monthInput, onValueChange = { monthInput = it },
                        modifier = Modifier.weight(1f), label = { Text("Tháng") }, singleLine = true,
                        enabled = !state.isWorking, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    OutlinedTextField(
                        value = yearInput, onValueChange = { yearInput = it },
                        modifier = Modifier.weight(1f), label = { Text("Năm") }, singleLine = true,
                        enabled = !state.isWorking, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
                OutlinedButton(
                    onClick = { viewModel.selectPeriod(monthInput, yearInput) },
                    enabled = !state.isWorking, modifier = Modifier.fillMaxWidth(),
                ) { Text("Xem tháng đã chọn") }
                Text("Đang xem: ${period.month}/${period.year}", style = MaterialTheme.typography.titleMedium)
                if (!periodApplied) Text("Bấm “Xem tháng đã chọn” để áp dụng tháng/năm mới.")
            }
            if (isAdmin) {
                item {
                    Button(
                        onClick = { confirmCalculation = true },
                        enabled = !state.isWorking && periodApplied,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (state.isWorking) "Đang xử lý…" else "Tính toán lương tháng") }
                }
            }
            if (state.isWorking) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            state.error?.let { error -> item { Text(error, color = MaterialTheme.colorScheme.error) } }
            if (salaries.isEmpty()) {
                item { Text("Chưa có bảng lương cho tháng này. Admin cần thực hiện tính lương trước.") }
            }
            items(salaries, key = { it.salary.id }) { details ->
                SalaryCard(details, numberFormat, isAdmin, !state.isWorking && periodApplied) { payingId = details.salary.id }
            }
        }
    }

    if (confirmCalculation) {
        AlertDialog(
            onDismissRequest = { confirmCalculation = false },
            title = { Text("Tính lương ${period.month}/${period.year}?") },
            text = { Text("Tính lương cho toàn bộ nhân viên từ các ngày làm COMPLETED. Bảng lương cũ của tháng này sẽ được cập nhật và chuyển về PENDING, kể cả bảng đã PAID.") },
            confirmButton = {
                TextButton(onClick = { confirmCalculation = false; viewModel.calculateMonthlySalaries() }) { Text("Tính lương") }
            },
            dismissButton = { TextButton(onClick = { confirmCalculation = false }) { Text("Hủy") } },
        )
    }
    payingId?.let { id ->
        AlertDialog(
            onDismissRequest = { payingId = null },
            title = { Text("Xác nhận đã thanh toán?") },
            text = { Text("Chuyển bảng lương này từ PENDING sang PAID.") },
            confirmButton = { TextButton(onClick = { payingId = null; viewModel.markAsPaid(id) }) { Text("Đã thanh toán") } },
            dismissButton = { TextButton(onClick = { payingId = null }) { Text("Hủy") } },
        )
    }
}

@Composable
private fun SalaryCard(details: SalaryDetails, format: NumberFormat, isAdmin: Boolean, enabled: Boolean, onPay: () -> Unit) {
    val salary = details.salary
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${details.employeeCode} — ${details.employeeName}", style = MaterialTheme.typography.titleMedium)
            Text("Tháng ${salary.month}/${salary.year}")
            Text("Tổng giờ làm: ${format.format(salary.totalHours)} giờ")
            Text("Giờ OT: ${format.format(salary.overtimeHours)} giờ")
            Text("Giờ thường: ${format.format(salary.totalHours - salary.overtimeHours)} giờ")
            Text("${if (salary.totalHours > 0) "Mức lương áp dụng" else "Mức lương hiện tại"}: ${format.format(details.calculatedHourlyRate)} VNĐ/giờ")
            Text("Hệ số OT: 1,5")
            Text("Tổng lương thực nhận: ${format.format(salary.totalSalary)} VNĐ", style = MaterialTheme.typography.titleMedium)
            Text(if (salary.status == "PAID") "PAID — Đã thanh toán" else "${salary.status} — Chờ thanh toán")
            if (isAdmin && salary.status == "PENDING") {
                Button(onClick = onPay, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text("Chuyển sang PAID") }
            }
        }
    }
}
