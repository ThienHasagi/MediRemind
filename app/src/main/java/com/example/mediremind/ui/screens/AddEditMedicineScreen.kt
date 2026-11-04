package com.example.mediremind.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mediremind.data.ai.ParsedMedicineUiModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddEditMedicineScreen(
    initialMedicine: ParsedMedicineUiModel?,
    onBackClick: () -> Unit,
    onSaveClick: (
        name: String,
        dosage: String,
        instruction: String,
        scheduleTimes: List<String>,
        repeatType: String,
        startDateEpochDay: Long
    ) -> Unit,
    modifier: Modifier = Modifier,
    isSaving: Boolean = false,
    errorMessage: String? = null
) {
    var name by rememberSaveable(initialMedicine) {
        mutableStateOf(initialMedicine?.medicineName.orEmpty())
    }
    var dosage by rememberSaveable(initialMedicine) {
        mutableStateOf(initialMedicine?.dosage.orEmpty())
    }
    var instruction by rememberSaveable(initialMedicine) {
        mutableStateOf(initialMedicine?.instruction.orEmpty())
    }
    var selectedRepeatType by rememberSaveable { mutableStateOf("DAILY") }
    var startDate by rememberSaveable { mutableStateOf(java.time.LocalDate.now().toString()) }
    val scheduleTimes = remember(initialMedicine) {
        mutableStateListOf<String>().apply {
            addAll(initialMedicine?.scheduleTimes?.ifEmpty { listOf("08:00") } ?: listOf("08:00"))
        }
    }
    var showTimePicker by rememberSaveable { mutableStateOf(false) }
    var validationError by rememberSaveable { mutableStateOf<String?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Lưu lịch thuốc",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick, enabled = !isSaving) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Quay lại"
                        )
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Tên thuốc") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                OutlinedTextField(
                    value = dosage,
                    onValueChange = { dosage = it },
                    label = { Text("Liều lượng, ví dụ: 1 viên") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                OutlinedTextField(
                    value = instruction,
                    onValueChange = { instruction = it },
                    label = { Text("Hướng dẫn, ví dụ: Uống sau ăn no, hoặc khi sốt > 38.5°C") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Tần suất / Loại thuốc",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            "DAILY" to "Hằng ngày",
                            "WEEKLY" to "1 tuần / lần",
                            "MONTHLY" to "1 tháng / lần",
                            "YEARLY" to "1 năm / lần",
                            "AS_NEEDED" to "Uống khi cần (Không cố định giờ)"
                        ).forEach { (type, label) ->
                            FilterChip(
                                selected = selectedRepeatType == type,
                                onClick = { selectedRepeatType = type },
                                label = { Text(label, fontWeight = FontWeight.Bold) }
                            )
                        }
                    }
                }
            }

            if (selectedRepeatType != "AS_NEEDED") {
                item {
                    OutlinedTextField(value = startDate, onValueChange = { startDate = it },
                        label = { Text("Ngày bắt đầu (yyyy-MM-dd)") }, singleLine = true,
                        supportingText = { Text("Tháng thiếu ngày sẽ nhắc vào ngày cuối tháng; 29/2 chuyển thành 28/2 khi cần.") },
                        modifier = Modifier.fillMaxWidth())
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Giờ uống trong ngày",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("08:00", "12:30", "20:00").forEach { time ->
                                AssistChip(
                                    onClick = {
                                        if (time !in scheduleTimes) {
                                            scheduleTimes.add(time)
                                            scheduleTimes.sort()
                                        }
                                    },
                                    label = { Text(time, fontWeight = FontWeight.Bold) }
                                )
                            }
                        }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            scheduleTimes.forEach { time ->
                                AssistChip(
                                    onClick = { scheduleTimes.remove(time) },
                                    label = { Text(time, fontWeight = FontWeight.Bold) },
                                    trailingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Xóa giờ"
                                        )
                                    }
                                )
                            }
                        }
                        Button(
                            onClick = { showTimePicker = true },
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null
                            )
                            Text("  Thêm mốc giờ")
                        }
                    }
                }
            } else {
                item {
                    Text(
                        text = "Thuốc uống khi cần sẽ hiển thị trong mục 'Thuốc uống khi cần' để Bố/Mẹ bấm đã uống bất kỳ lúc nào có triệu chứng.",
                        color = Color(0xFF0D47A1),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            (validationError ?: errorMessage)?.let { message ->
                item {
                    Text(
                        text = message,
                        color = Color(0xFFB71C1C),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    enabled = !isSaving,
                    onClick = {
                        validationError = validateMedicineInput(
                            name,
                            dosage,
                            instruction,
                            scheduleTimes,
                            selectedRepeatType
                        )
                        val parsedDate = runCatching { java.time.LocalDate.parse(startDate) }.getOrNull()
                        if (selectedRepeatType != "AS_NEEDED" && parsedDate == null) {
                            validationError = "Ngày không hợp lệ. Nhập theo yyyy-MM-dd."
                        }
                        if (validationError == null) {
                            onSaveClick(
                                name.trim(),
                                dosage.trim(),
                                instruction.trim(),
                                if (selectedRepeatType == "AS_NEEDED") listOf("Khi cần") else scheduleTimes.sorted(),
                                selectedRepeatType,
                                (if (selectedRepeatType == "AS_NEEDED") java.time.LocalDate.now() else parsedDate!!).toEpochDay()
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(62.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2E7D32),
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        text = if (isSaving) "ĐANG LƯU…" else "LƯU LỊCH UỐNG CHO BỐ/MẸ",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }
    }

    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(is24Hour = true)
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Chọn giờ uống") },
            text = {
                Column {
                    TimePicker(state = timePickerState)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val time = "%02d:%02d".format(timePickerState.hour, timePickerState.minute)
                        if (time !in scheduleTimes) {
                            scheduleTimes.add(time)
                            scheduleTimes.sort()
                        }
                        showTimePicker = false
                    }
                ) {
                    Text("Thêm")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text("Đóng")
                }
            }
        )
    }
}

private fun validateMedicineInput(
    name: String,
    dosage: String,
    instruction: String,
    scheduleTimes: List<String>,
    repeatType: String
): String? {
    return when {
        name.isBlank() -> "Vui lòng nhập tên thuốc."
        dosage.isBlank() -> "Vui lòng nhập liều lượng."
        instruction.isBlank() -> "Vui lòng nhập hướng dẫn uống thuốc."
        repeatType != "AS_NEEDED" && scheduleTimes.isEmpty() -> "Vui lòng chọn ít nhất một giờ uống."
        else -> null
    }
}
