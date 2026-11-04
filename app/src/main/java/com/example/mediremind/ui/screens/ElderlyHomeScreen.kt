package com.example.mediremind.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brightness5
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mediremind.ui.viewmodel.DoseUiItem
import com.example.mediremind.ui.viewmodel.HomeViewModel
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ElderlyBackground = Color(0xFFF4F6F8)
private val MorningBackground = Color(0xFFFFF8E7)
private val MorningBorder = Color(0xFFFFB300)
private val MorningText = Color(0xFF3E2723)
private val NightBackground = Color(0xFF233249)
private val NightBorder = Color(0xFFB0BEC5)
private val MoonYellow = Color(0xFFFFD54F)
private val TakenBackground = Color(0xFFE8F5E9)
private val TakenBorder = Color(0xFF2E7D32)
private val PrimaryGreen = Color(0xFF2E7D32)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ElderlyHomeScreen(
    viewModel: HomeViewModel,
    pairCode: String,
    emergencyPhone: String,
    onCallChildClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val allDoses = uiState.morningDoses + uiState.afternoonDoses + uiState.eveningDoses
    val morningDoses = allDoses.filter { isMorning(it.reminderTime) }
    val nightDoses = allDoses.filterNot { isMorning(it.reminderTime) }

    LaunchedEffect(pairCode, allDoses, uiState.asNeededDoses) {
        viewModel.syncVisibleDosesToFirestore(pairCode)
    }

    LaunchedEffect(pairCode) {
        viewModel.startRemoteMedicineSync(pairCode)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = ElderlyBackground,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "LỊCH UỐNG THUỐC",
                        color = Color(0xFF101010),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center
                    )
                },
                actions = {
                    EmergencyCallButton(
                        enabled = emergencyPhone.isNotBlank(),
                        onClick = { onCallChildClick(emergencyPhone) }
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.White,
                    titleContentColor = Color(0xFF101010)
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(
                start = 18.dp,
                top = 18.dp,
                end = 18.dp,
                bottom = 28.dp
            ),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            if (uiState.isLoading) {
                item { androidx.compose.material3.CircularProgressIndicator() }
            }
            item { ReminderPermissionStatus() }
            uiState.errorMessage?.let { message ->
                item {
                    Column {
                        Text(message, color = Color(0xFFB71C1C), fontSize = 18.sp)
                        Button(onClick = { viewModel.retry(pairCode) }) { Text("Thử lại") }
                    }
                }
            }
            if (pairCode.isNotBlank()) {
                item {
                    PairCodeCard(pairCode = pairCode)
                }
            }

            if (!uiState.isLoading && !uiState.hasAnyDose) {
                item {
                    EmptyMedicineMessage(pairCode = pairCode)
                }
            }

            if (uiState.asNeededDoses.isNotEmpty()) {
                item { ElderlySectionHeader("THUỐC UỐNG KHI CẦN") }
                items(
                    items = uiState.asNeededDoses,
                    key = { it.doseId }
                ) { dose ->
                    ElderlyMedicineCard(
                        dose = dose,
                        isMorningDose = true,
                        onTakeMedicine = {
                            viewModel.onTakeMedicine(dose.doseId, pairCode)
                        }
                    )
                }
            }

            if (morningDoses.isNotEmpty()) {
                item { ElderlySectionHeader("CỮ SÁNG") }
                items(
                    items = morningDoses,
                    key = { it.doseId }
                ) { dose ->
                    ElderlyMedicineCard(
                        dose = dose,
                        isMorningDose = true,
                        onTakeMedicine = {
                            viewModel.onTakeMedicine(dose.doseId, pairCode)
                        }
                    )
                }
            }

            if (nightDoses.isNotEmpty()) {
                item { ElderlySectionHeader("CỮ TỐI") }
                items(
                    items = nightDoses,
                    key = { it.doseId }
                ) { dose ->
                    ElderlyMedicineCard(
                        dose = dose,
                        isMorningDose = false,
                        onTakeMedicine = {
                            viewModel.onTakeMedicine(dose.doseId, pairCode)
                        }
                    )
                }
            }
        }
    }
}

fun isMorning(timeStr: String): Boolean {
    val time = runCatching { LocalTime.parse(timeStr) }.getOrNull() ?: return false
    return !time.isBefore(LocalTime.of(5, 0)) && time.isBefore(LocalTime.of(15, 0))
}

@Composable
private fun EmergencyCallButton(
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .padding(end = 12.dp)
            .size(56.dp)
            .shadow(elevation = 6.dp, shape = CircleShape),
        shape = CircleShape,
        color = if (enabled) Color(0xFFE53935) else Color(0xFFBDBDBD)
    ) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.size(56.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Phone,
                contentDescription = "Gọi cho con",
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

@Composable
private fun ElderlySectionHeader(title: String) {
    Text(
        text = title,
        color = Color(0xFF0D47A1),
        fontSize = 24.sp,
        lineHeight = 30.sp,
        fontWeight = FontWeight.ExtraBold,
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
private fun ElderlyMedicineCard(
    dose: DoseUiItem,
    isMorningDose: Boolean,
    onTakeMedicine: () -> Unit
) {
    val style = when {
        dose.isTaken -> CardVisualStyle(
            background = TakenBackground,
            border = TakenBorder,
            text = Color(0xFF1B5E20),
            icon = Icons.Default.CheckCircle,
            iconTint = TakenBorder
        )
        isMorningDose -> CardVisualStyle(
            background = MorningBackground,
            border = MorningBorder,
            text = MorningText,
            icon = Icons.Default.Brightness5,
            iconTint = Color(0xFFF57C00)
        )
        else -> CardVisualStyle(
            background = NightBackground,
            border = NightBorder,
            text = Color(0xFFFFFDE7),
            icon = Icons.Default.NightsStay,
            iconTint = MoonYellow
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                border = BorderStroke(width = 2.dp, color = style.border),
                shape = RoundedCornerShape(22.dp)
            ),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = style.background),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = style.icon,
                    contentDescription = null,
                    tint = style.iconTint,
                    modifier = Modifier.size(38.dp)
                )
                Text(
                    text = if (dose.isTaken) "✓ ĐÃ UỐNG" else dose.reminderTime,
                    color = style.text,
                    fontSize = 28.sp,
                    lineHeight = 34.sp,
                    fontWeight = FontWeight.Black
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = dose.medicineName,
                color = style.text,
                fontSize = 26.sp,
                lineHeight = 32.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = dose.dosage,
                color = style.text,
                fontSize = 20.sp,
                lineHeight = 26.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = if (isMorningDose || dose.isTaken) Color(0xFFFFECB3) else Color(0xFF31435F),
                        shape = RoundedCornerShape(14.dp)
                    )
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Text(
                    text = dose.instruction,
                    color = if (isMorningDose || dose.isTaken) MorningText else Color.White,
                    fontSize = 18.sp,
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            if (dose.isTaken) {
                Text(
                    text = "Đã uống lúc ${dose.takenTimestamp.formatTakenTime()}",
                    color = Color(0xFF1B5E20),
                    fontSize = 20.sp,
                    lineHeight = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            } else {
                Button(
                    onClick = onTakeMedicine,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
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
private fun PairCodeCard(pairCode: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Text(
            text = "Mã ghép đôi: $pairCode",
            color = Color(0xFF0D47A1),
            fontSize = 20.sp,
            lineHeight = 26.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
private fun EmptyMedicineMessage(pairCode: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Hôm nay chưa có lịch uống thuốc.",
                color = Color(0xFF101010),
                fontSize = 22.sp,
                lineHeight = 30.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            if (pairCode.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Mã ghép đôi: $pairCode",
                    color = Color(0xFF0D47A1),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

private data class CardVisualStyle(
    val background: Color,
    val border: Color,
    val text: Color,
    val icon: ImageVector,
    val iconTint: Color
)

private fun Long?.formatTakenTime(): String {
    if (this == null) return "--:--"
    val formatter = DateTimeFormatter
        .ofPattern("HH:mm", Locale("vi", "VN"))
        .withZone(ZoneId.systemDefault())
    return formatter.format(Instant.ofEpochMilli(this))
}
