package com.example.mediremind.ui.screens

import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mediremind.alarm.AlarmSoundController
import com.example.mediremind.alarm.AndroidAlarmScheduler
import com.example.mediremind.data.local.AppDatabase
import com.example.mediremind.receiver.AlarmPayload
import com.example.mediremind.receiver.AlarmReceiver
import com.example.mediremind.ui.theme.MediRemindTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class AlarmAlertActivity : ComponentActivity() {
    private var payload by mutableStateOf<AlarmPayload?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureLockScreenBehavior()
        payload = AlarmPayload.from(intent)

        setContent {
            MediRemindTheme {
                payload?.let { alarmPayload ->
                    AlarmAlertScreen(
                        payload = alarmPayload,
                        onTakenClick = ::markTakenAndClose,
                        onSnoozeClick = ::snoozeAndClose
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        payload = AlarmPayload.from(intent)
    }

    private fun configureLockScreenBehavior() {
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            getSystemService(KeyguardManager::class.java)
                .requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
    }

    private fun markTakenAndClose() {
        val currentPayload = payload ?: return
        AlarmSoundController.stop()
        cancelNotification(currentPayload.scheduleId)

        if (currentPayload.medicineId <= 0L || currentPayload.scheduleId <= 0L) {
            finish()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            AppDatabase.getInstance(this@AlarmAlertActivity).doseLogDao().markDoseTaken(
                medicineId = currentPayload.medicineId,
                scheduleId = currentPayload.scheduleId,
                dateEpochDay = LocalDate.now().toEpochDay()
            )
            finish()
        }
    }

    private fun snoozeAndClose() {
        val currentPayload = payload ?: return
        AlarmSoundController.stop()
        cancelNotification(currentPayload.scheduleId)

        AndroidAlarmScheduler(this).scheduleAfterDelay(
            scheduleId = currentPayload.scheduleId,
            medicineId = currentPayload.medicineId,
            medicineName = currentPayload.medicineName,
            dosage = currentPayload.dosage,
            instruction = currentPayload.instruction,
            delayMillis = 10 * 60 * 1000L,
            isTest = currentPayload.isTest
        )
        finish()
    }

    private fun cancelNotification(scheduleId: Long) {
        getSystemService(NotificationManager::class.java).cancel(scheduleId.toInt())
    }

    companion object {
        fun createIntent(context: Context, payload: AlarmPayload): Intent {
            return Intent(context, AlarmAlertActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(AlarmReceiver.EXTRA_SCHEDULE_ID, payload.scheduleId)
                putExtra(AlarmReceiver.EXTRA_MEDICINE_ID, payload.medicineId)
                putExtra(AlarmReceiver.EXTRA_MEDICINE_NAME, payload.medicineName)
                putExtra(AlarmReceiver.EXTRA_DOSAGE, payload.dosage)
                putExtra(AlarmReceiver.EXTRA_INSTRUCTION, payload.instruction)
                putExtra(AlarmReceiver.EXTRA_REMINDER_TIME, payload.reminderTime)
                putExtra(AlarmReceiver.EXTRA_IS_TEST, payload.isTest)
            }
        }
    }
}

@Composable
private fun AlarmAlertScreen(
    payload: AlarmPayload,
    onTakenClick: () -> Unit,
    onSnoozeClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFB71C1C))
            .padding(horizontal = 24.dp, vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Alarm,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(112.dp)
        )

        Spacer(modifier = Modifier.height(22.dp))

        Text(
            text = "ĐẾN GIỜ UỐNG THUỐC",
            color = Color.White,
            fontSize = 32.sp,
            lineHeight = 38.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = payload.reminderTime ?: currentTimeText(),
            color = Color(0xFFFFF176),
            fontSize = 32.sp,
            lineHeight = 38.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = payload.medicineName,
            color = Color.White,
            fontSize = 28.sp,
            lineHeight = 34.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = buildDoseInstruction(payload),
            color = Color.White,
            fontSize = 22.sp,
            lineHeight = 30.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(34.dp))

        Button(
            onClick = onTakenClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF2E7D32),
                contentColor = Color.White
            )
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(32.dp)
            )
            Text(
                text = "  TÔI ĐÃ UỐNG",
                fontSize = 22.sp,
                lineHeight = 26.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        Button(
            onClick = onSnoozeClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFFFA000),
                contentColor = Color(0xFF151515)
            )
        ) {
            Icon(
                imageVector = Icons.Default.Snooze,
                contentDescription = null,
                modifier = Modifier.size(32.dp)
            )
            Text(
                text = "  10 PHÚT NỮA NHẮC LẠI",
                fontSize = 20.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun buildDoseInstruction(payload: AlarmPayload): String {
    return listOf(payload.dosage, payload.instruction)
        .filter { it.isNotBlank() }
        .joinToString(separator = " - ")
}

private fun currentTimeText(): String {
    val formatter = DateTimeFormatter
        .ofPattern("HH:mm", Locale("vi", "VN"))
        .withZone(ZoneId.systemDefault())
    return formatter.format(Instant.ofEpochMilli(System.currentTimeMillis()))
}
