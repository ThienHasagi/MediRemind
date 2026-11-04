package com.example.mediremind.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mediremind.data.remote.FirestoreMedicineModel
import com.example.mediremind.data.remote.RemoteDoseLog
import com.example.mediremind.ui.viewmodel.CaregiverDashboardViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun CaregiverDashboardScreen(
    viewModel: CaregiverDashboardViewModel,
    pairCode: String,
    onQuickCallClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onAiPrescriptionClick: () -> Unit,
    onManualMedicineClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var showAddChoiceDialog by remember { mutableStateOf(false) }
    var medicineToDelete by remember { mutableStateOf<FirestoreMedicineModel?>(null) }

    LaunchedEffect(pairCode) {
        viewModel.setPairCode(pairCode)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color(0xFFF4F6F8),
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddChoiceDialog = true },
                containerColor = Color(0xFF0D47A1),
                contentColor = Color.White
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Thêm đơn thuốc từ xa"
                )
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (uiState.isLoading || uiState.isSaving) {
                item { androidx.compose.material3.LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
            }
            uiState.errorMessage?.let { message ->
                item { Column { Text(message, color = Color(0xFFB71C1C))
                    TextButton(onClick = viewModel::retry) { Text("Thử tải lại") }
                } }
            }
            item {
                DashboardHeader(
                    pairCode = pairCode,
                    onQuickCallClick = onQuickCallClick,
                    onSettingsClick = onSettingsClick
                )
            }

            item {
                TabRow(selectedTabIndex = selectedTabIndex) {
                    Tab(
                        selected = selectedTabIndex == 0,
                        onClick = { selectedTabIndex = 0 },
                        text = { Text("Lịch sử nhật ký", fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = selectedTabIndex == 1,
                        onClick = { selectedTabIndex = 1 },
                        text = { Text("Tủ thuốc", fontWeight = FontWeight.Bold) }
                    )
                }
            }

            if (selectedTabIndex == 0) {
                item {
                    TextButton(onClick = {
                        val date = LocalDate.ofEpochDay(uiState.selectedDateEpochDay)
                        android.app.DatePickerDialog(context, { _, year, month, day ->
                            viewModel.setSelectedDate(LocalDate.of(year, month + 1, day).toEpochDay())
                        }, date.year, date.monthValue - 1, date.dayOfMonth).apply {
                            datePicker.maxDate = System.currentTimeMillis()
                        }.show()
                    }) { Text("Chọn ngày xem lịch sử") }
                    DateNavigationHeader(
                        selectedEpochDay = uiState.selectedDateEpochDay,
                        onPrevDay = { viewModel.setSelectedDate(uiState.selectedDateEpochDay - 1) },
                        onNextDay = { viewModel.setSelectedDate(uiState.selectedDateEpochDay + 1) },
                        onResetToday = { viewModel.setSelectedDate(LocalDate.now().toEpochDay()) }
                    )
                }

                if (!uiState.isLoading && uiState.errorMessage == null && uiState.doseLogs.isEmpty()) {
                    item {
                        EmptyDashboardMessage(epochDay = uiState.selectedDateEpochDay)
                    }
                }

                items(
                    items = uiState.doseLogs,
                    key = { "${it.dateEpochDay}_${it.scheduleId}" }
                ) { doseLog ->
                    CaregiverDoseCard(doseLog = doseLog)
                }
            } else {
                if (!uiState.isLoading && uiState.errorMessage == null && uiState.medicines.isEmpty()) {
                    item {
                        EmptyMedicineCabinetMessage()
                    }
                }

                items(
                    items = uiState.medicines,
                    key = { it.id }
                ) { medicine ->
                    MedicineCabinetCard(
                        medicine = medicine,
                        onDeleteClick = { medicineToDelete = medicine }
                    )
                }
            }
        }
    }

    if (showAddChoiceDialog) {
        AddMedicineChoiceDialog(
            onDismiss = { showAddChoiceDialog = false },
            onAiClick = {
                showAddChoiceDialog = false
                onAiPrescriptionClick()
            },
            onManualClick = {
                showAddChoiceDialog = false
                onManualMedicineClick()
            }
        )
    }

    medicineToDelete?.let { medicine ->
        AlertDialog(
            onDismissRequest = { medicineToDelete = null },
            title = {
                Text(
                    text = "Xác nhận xóa thuốc?",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Bạn có chắc chắn muốn xóa thuốc \"${medicine.name}\" khỏi tủ thuốc của Bố/Mẹ không? Thao tác này không thể hoàn tác.",
                    fontSize = 16.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteMedicine(medicine.id)
                        medicineToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFB71C1C)
                    )
                ) {
                    Text("Xóa thuốc", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { medicineToDelete = null }) {
                    Text("Hủy")
                }
            }
        )
    }
}

@Composable
private fun DashboardHeader(
    pairCode: String,
    onQuickCallClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "THEO DÕI UỐNG THUỐC",
                    color = Color(0xFF101010),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "Mã ghép đôi: $pairCode",
                    color = Color(0xFF0D47A1),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            IconButton(onClick = onSettingsClick) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Cài đặt máy Con cái",
                    tint = Color(0xFF0D47A1),
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        ExtendedFloatingActionButton(
            onClick = onQuickCallClick,
            icon = {
                Icon(
                    imageVector = Icons.Default.Call,
                    contentDescription = null
                )
            },
            text = {
                Text(
                    text = "Gọi nhanh cho Bố/Mẹ",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            containerColor = Color(0xFF2E7D32),
            contentColor = Color.White,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun DateNavigationHeader(
    selectedEpochDay: Long,
    onPrevDay: () -> Unit,
    onNextDay: () -> Unit,
    onResetToday: () -> Unit
) {
    val todayEpochDay = LocalDate.now().toEpochDay()
    val isToday = selectedEpochDay == todayEpochDay
    val dateText = when (selectedEpochDay) {
        todayEpochDay -> "Hôm nay"
        todayEpochDay - 1 -> "Hôm qua"
        todayEpochDay + 1 -> "Ngày mai"
        else -> LocalDate.ofEpochDay(selectedEpochDay).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onPrevDay) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Ngày trước"
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = dateText,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF0D47A1)
                )
                if (!isToday) {
                    TextButton(
                        onClick = onResetToday,
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("Về Hôm nay", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            IconButton(onClick = onNextDay, enabled = selectedEpochDay < todayEpochDay) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Ngày sau"
                )
            }
        }
    }
}

@Composable
private fun CaregiverDoseCard(doseLog: RemoteDoseLog) {
    val isTaken = doseLog.isTaken
    val background = if (isTaken) Color(0xFFE8F5E9) else Color(0xFFFFF8E1)
    val accent = if (isTaken) Color(0xFF2E7D32) else Color(0xFFF9A825)
    val statusText = if (isTaken) {
        "Đã uống lúc ${doseLog.takenTimestamp.formatTime()}"
    } else {
        "Chưa ghi nhận uống thuốc"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = background),
        elevation = CardDefaults.cardElevation(defaultElevation = 5.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = if (isTaken) Icons.Default.CheckCircle else Icons.Default.Schedule,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(32.dp)
                )
                Text(
                    text = doseLog.reminderTime,
                    color = Color(0xFF101010),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = doseLog.medicineName,
                color = Color(0xFF101010),
                fontSize = 22.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "${doseLog.dosage} - ${doseLog.instruction}",
                color = Color(0xFF263238),
                fontSize = 17.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = statusText,
                color = accent,
                fontSize = 18.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

@Composable
private fun MedicineCabinetCard(
    medicine: FirestoreMedicineModel,
    onDeleteClick: () -> Unit
) {
    val frequencyLabel = when (medicine.repeatType) {
        "WEEKLY" -> "Lặp lại: 1 tuần / 1 lần"
        "MONTHLY" -> "Lặp lại: 1 tháng / 1 lần"
        "YEARLY" -> "Lặp lại: 1 năm / 1 lần"
        "AS_NEEDED" -> "Lặp lại: Uống khi cần (Phụ thuộc tình trạng)"
        else -> "Lặp lại: Hằng ngày"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 5.dp)
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = medicine.name,
                    color = Color(0xFF101010),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = medicine.dosage,
                    color = Color(0xFF263238),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = medicine.instruction,
                    color = Color(0xFF263238),
                    fontSize = 16.sp,
                    lineHeight = 22.sp
                )
                Text(
                    text = frequencyLabel,
                    color = Color(0xFF2E7D32),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                if (medicine.scheduleTimes.isNotEmpty()) {
                    Text(
                        text = "Giờ uống: ${medicine.scheduleTimes.joinToString(", ")}",
                        color = Color(0xFF0D47A1),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            IconButton(onClick = onDeleteClick) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Xóa thuốc",
                    tint = Color(0xFFB71C1C)
                )
            }
        }
    }
}

@Composable
private fun EmptyDashboardMessage(epochDay: Long) {
    val isToday = epochDay == LocalDate.now().toEpochDay()
    val text = if (isToday) {
        "Chưa có dữ liệu cữ thuốc hôm nay. Dữ liệu sẽ cập nhật khi máy của Bố/Mẹ ghi nhận uống thuốc."
    } else {
        "Không có ghi nhận uống thuốc nào trong ngày này."
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Text(
            text = text,
            color = Color(0xFF101010),
            fontSize = 18.sp,
            lineHeight = 26.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(22.dp)
        )
    }
}

@Composable
private fun EmptyMedicineCabinetMessage() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Text(
            text = "Tủ thuốc chưa có thuốc. Bấm nút + để nhập thuốc thủ công hoặc chụp đơn thuốc bằng AI.",
            color = Color(0xFF101010),
            fontSize = 18.sp,
            lineHeight = 26.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(22.dp)
        )
    }
}

@Composable
private fun AddMedicineChoiceDialog(
    onDismiss: () -> Unit,
    onAiClick: () -> Unit,
    onManualClick: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Thêm thuốc cho Bố/Mẹ",
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onAiClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF0D47A1),
                        contentColor = Color.White
                    )
                ) {
                    Icon(imageVector = Icons.Default.CameraAlt, contentDescription = null)
                    Text("  Chụp đơn thuốc bằng AI", fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = onManualClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2E7D32),
                        contentColor = Color.White
                    )
                ) {
                    Icon(imageVector = Icons.Default.Edit, contentDescription = null)
                    Text("  Nhập thuốc thủ công", fontWeight = FontWeight.Bold)
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Đóng")
            }
        }
    )
}

private fun Long?.formatTime(): String {
    if (this == null) return "--:--"
    val formatter = DateTimeFormatter
        .ofPattern("HH:mm", Locale("vi", "VN"))
        .withZone(ZoneId.systemDefault())
    return formatter.format(Instant.ofEpochMilli(this))
}
