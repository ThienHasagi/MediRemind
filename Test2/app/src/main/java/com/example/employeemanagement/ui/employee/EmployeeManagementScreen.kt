package com.example.employeemanagement.ui.employee

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.employeemanagement.data.local.entity.Employee
import java.text.NumberFormat
import java.util.Locale

@Composable
fun EmployeeManagementScreen(viewModel: EmployeeViewModel, onBack: (() -> Unit)? = null) {
    val employees by viewModel.employees.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    val moneyFormat = remember { NumberFormat.getNumberInstance(Locale.forLanguageTag("vi-VN")) }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHost.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                onBack?.let { action -> TextButton(onClick = action) { Text("← Quay lại") } }
                Text("Quản lý nhân viên", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = viewModel::search,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Tìm theo họ tên hoặc mã nhân viên") },
                    singleLine = true,
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = viewModel::openAddDialog) { Text("+ Thêm nhân viên") }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!state.editorOpen && state.pendingDelete == null) {
                state.error?.let { error -> item { Text(error, color = MaterialTheme.colorScheme.error) } }
            }
            if (employees.isEmpty()) {
                item { Text(if (query.isBlank()) "Chưa có nhân viên." else "Không tìm thấy nhân viên phù hợp.") }
            }
            items(employees, key = { it.id }) { employee ->
                EmployeeCard(
                    employee = employee,
                    hourlyRate = moneyFormat.format(employee.hourlyRate),
                    enabled = !state.isSaving,
                    onEdit = { viewModel.openEditDialog(employee) },
                    onDelete = { viewModel.requestDelete(employee) },
                )
            }
        }
    }

    if (state.editorOpen) {
        EmployeeEditorDialog(
            state = state,
            onChange = viewModel::updateForm,
            onDismiss = viewModel::dismissDialogs,
            onSave = { if (state.editingEmployee == null) viewModel.addEmployee() else viewModel.updateEmployee() },
        )
    }
    state.pendingDelete?.let { employee ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDialogs,
            title = { Text("Xóa nhân viên?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Xóa ${employee.code} — ${employee.fullname}? Tài khoản đăng nhập, chấm công và bảng lương liên quan cũng sẽ bị xóa.")
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::deleteEmployee, enabled = !state.isSaving) {
                    Text(if (state.isSaving) "Đang xóa…" else "Xóa", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDialogs, enabled = !state.isSaving) { Text("Hủy") }
            },
        )
    }
}

@Composable
private fun EmployeeCard(employee: Employee, hourlyRate: String, enabled: Boolean, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("${employee.code} — ${employee.fullname}", style = MaterialTheme.typography.titleMedium)
            Text("Email: ${employee.email}")
            Text("Điện thoại: ${employee.phone}")
            Text("Lương theo giờ: $hourlyRate VNĐ/giờ", style = MaterialTheme.typography.titleSmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onEdit, enabled = enabled) { Text("Sửa") }
                TextButton(onClick = onDelete, enabled = enabled) { Text("Xóa", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

@Composable
private fun EmployeeEditorDialog(
    state: EmployeeUiState,
    onChange: (EmployeeForm) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    val form = state.form
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (state.editingEmployee == null) "Thêm nhân viên" else "Sửa nhân viên") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                EmployeeField("Mã nhân viên", form.code, state.fieldErrors["code"], !state.isSaving) { onChange(form.copy(code = it)) }
                EmployeeField("Họ tên", form.fullname, state.fieldErrors["fullname"], !state.isSaving) { onChange(form.copy(fullname = it)) }
                EmployeeField("Email", form.email, state.fieldErrors["email"], !state.isSaving, KeyboardType.Email) { onChange(form.copy(email = it)) }
                EmployeeField("Số điện thoại", form.phone, state.fieldErrors["phone"], !state.isSaving, KeyboardType.Phone) { onChange(form.copy(phone = it)) }
                EmployeeField("Lương theo giờ (VNĐ)", form.hourlyRate, state.fieldErrors["hourlyRate"], !state.isSaving, KeyboardType.Decimal) { onChange(form.copy(hourlyRate = it)) }
                Text(
                    if (state.editingEmployee == null) "Tên đăng nhập là mã nhân viên. Mật khẩu ban đầu: 123456."
                    else "Nếu đổi mã nhân viên, tên đăng nhập cũng đổi theo mã mới. Mật khẩu được giữ nguyên.",
                    style = MaterialTheme.typography.bodySmall,
                )
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(onClick = onSave, enabled = !state.isSaving) { Text(if (state.isSaving) "Đang lưu…" else "Lưu") } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !state.isSaving) { Text("Hủy") } },
    )
}

@Composable
private fun EmployeeField(
    label: String,
    value: String,
    error: String?,
    enabled: Boolean,
    keyboardType: KeyboardType = KeyboardType.Text,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        enabled = enabled,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
    )
}
