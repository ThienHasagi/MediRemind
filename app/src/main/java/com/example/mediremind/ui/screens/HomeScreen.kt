package com.example.mediremind.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mediremind.ui.viewmodel.DoseUiItem
import com.example.mediremind.ui.viewmodel.HomeViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val AppBackground = Color(0xFFF4F6F8)
private val TakenCardBackground = Color(0xFFE8F5E9)
private val InstructionBackground = Color(0xFFFFF3CD)
private val PrimaryGreen = Color(0xFF2E7D32)
private val HighContrastText = Color(0xFF101010)

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onCameraClick: () -> Unit,
    onTestAlarmClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = AppBackground,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCameraClick,
                icon = {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = null,
                        modifier = Modifier.size(30.dp)
                    )
                },
                text = {
                    Text(
                        text = "Chụp Đơn Thuốc",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                containerColor = Color(0xFF0D47A1),
                contentColor = Color.White,
                modifier = Modifier
                    .height(64.dp)
                    .navigationBarsPadding()
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(AppBackground)
                .padding(padding)
                .statusBarsPadding(),
            contentPadding = PaddingValues(
                start = 18.dp,
                top = 20.dp,
                end = 18.dp,
                bottom = 112.dp
            ),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item {
                Text(
                    text = "LỊCH UỐNG THUỐC HÔM NAY",
                    color = HighContrastText,
                    fontSize = 28.sp,
                    lineHeight = 34.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                Button(
                    onClick = onTestAlarmClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF455A64),
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.NotificationsActive,
                        contentDescription = null,
                        modifier = Modifier.size(26.dp)
                    )
                    Text(
                        text = "  Thử chuông sau 10 giây",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (!uiState.isLoading && !uiState.hasAnyDose) {
                item {
                    EmptyTodayMessage()
                }
            }

            doseSection(
                title = "Sáng",
                doses = uiState.morningDoses,
                onTakeMedicine = viewModel::onTakeMedicine
            )
            doseSection(
                title = "Trưa",
                doses = uiState.afternoonDoses,
                onTakeMedicine = viewModel::onTakeMedicine
            )
            doseSection(
                title = "Tối",
                doses = uiState.eveningDoses,
                onTakeMedicine = viewModel::onTakeMedicine
            )
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.doseSection(
    title: String,
    doses: List<DoseUiItem>,
    onTakeMedicine: (Long) -> Unit
) {
    if (doses.isEmpty()) return

    item {
        SectionHeader(title = title)
    }
    items(
        items = doses,
        key = { dose -> dose.doseId }
    ) { dose ->
        SeniorMedicineCard(
            dose = dose,
            onTakeMedicine = { onTakeMedicine(dose.doseId) }
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        color = Color(0xFF0D47A1),
        fontSize = 24.sp,
        fontWeight = FontWeight.ExtraBold,
        modifier = Modifier.padding(top = 6.dp)
    )
}

@Composable
fun SeniorMedicineCard(
    dose: DoseUiItem,
    onTakeMedicine: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardColor = if (dose.isTaken) TakenCardBackground else Color.White

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AccessTime,
                    contentDescription = null,
                    tint = Color(0xFF0D47A1),
                    modifier = Modifier.size(34.dp)
                )
                Text(
                    text = dose.reminderTime,
                    color = HighContrastText,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = dose.medicineName,
                color = HighContrastText,
                fontSize = 24.sp,
                lineHeight = 30.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = dose.dosage,
                color = Color(0xFF202020),
                fontSize = 20.sp,
                lineHeight = 26.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(14.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = InstructionBackground,
                        shape = RoundedCornerShape(14.dp)
                    )
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Text(
                    text = dose.instruction,
                    color = Color(0xFF3D2C00),
                    fontSize = 18.sp,
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            if (dose.isTaken) {
                TakenStatus(timestamp = dose.takenTimestamp)
            } else {
                Button(
                    onClick = onTakeMedicine,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryGreen,
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        text = "TÔI ĐÃ UỐNG THUỐC NÀY",
                        fontSize = 18.sp,
                        lineHeight = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun TakenStatus(timestamp: Long?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = Color(0xFFD0EFD2),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = PrimaryGreen,
            modifier = Modifier.size(32.dp)
        )
        Text(
            text = "✓ Đã uống lúc ${timestamp.formatTakenTime()}",
            color = Color(0xFF1B5E20),
            fontSize = 20.sp,
            lineHeight = 26.sp,
            fontWeight = FontWeight.ExtraBold
        )
    }
}

@Composable
private fun EmptyTodayMessage() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Text(
            text = "Hôm nay chưa có lịch uống thuốc.",
            color = HighContrastText,
            fontSize = 22.sp,
            lineHeight = 30.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(24.dp)
        )
    }
}

private fun Long?.formatTakenTime(): String {
    if (this == null) return "--:--"
    val formatter = DateTimeFormatter
        .ofPattern("HH:mm", Locale("vi", "VN"))
        .withZone(ZoneId.systemDefault())
    return formatter.format(Instant.ofEpochMilli(this))
}
