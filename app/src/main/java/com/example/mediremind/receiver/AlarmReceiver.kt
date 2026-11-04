package com.example.mediremind.receiver

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.mediremind.R
import com.example.mediremind.alarm.AlarmSoundController
import com.example.mediremind.alarm.AndroidAlarmScheduler
import com.example.mediremind.data.local.AppDatabase
import com.example.mediremind.ui.screens.AlarmAlertActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_ALARM_TRIGGER -> handleAlarmTrigger(context, intent)
            ACTION_MARK_TAKEN -> handleMarkTaken(context, intent)
            ACTION_SNOOZE -> handleSnooze(context, intent)
        }
    }

    private fun handleAlarmTrigger(context: Context, intent: Intent) {
        val payload = AlarmPayload.from(intent)
        AlarmSoundController.start(context)
        createNotificationChannel(context)
        showAlarmNotification(context, payload)

        if (!payload.isTest && payload.reminderTime != null) {
            val pending = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try { AndroidAlarmScheduler(context).rescheduleAll() }
                finally { pending.finish() }
            }
        }
    }

    private fun handleMarkTaken(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val payload = AlarmPayload.from(intent)
        AlarmSoundController.stop()
        NotificationManagerCompat.from(context).cancel(payload.scheduleId.toInt())

        CoroutineScope(Dispatchers.IO).launch {
            AppDatabase.getInstance(context).doseLogDao().markDoseTaken(
                medicineId = payload.medicineId,
                scheduleId = payload.scheduleId,
                dateEpochDay = LocalDate.now().toEpochDay()
            )
            pendingResult.finish()
        }
    }

    private fun handleSnooze(context: Context, intent: Intent) {
        val payload = AlarmPayload.from(intent)
        AlarmSoundController.stop()
        NotificationManagerCompat.from(context).cancel(payload.scheduleId.toInt())
        AndroidAlarmScheduler(context).scheduleAfterDelay(
            scheduleId = payload.scheduleId,
            medicineId = payload.medicineId,
            medicineName = payload.medicineName,
            dosage = payload.dosage,
            instruction = payload.instruction,
            delayMillis = TEN_MINUTES_MILLIS,
            isTest = payload.isTest
        )
    }

    private fun showAlarmNotification(context: Context, payload: AlarmPayload) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val fullScreenIntent = PendingIntent.getActivity(
            context,
            payload.scheduleId.toInt(),
            AlarmAlertActivity.createIntent(context, payload),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val takenIntent = PendingIntent.getBroadcast(
            context,
            payload.scheduleId.toInt() + TAKEN_REQUEST_OFFSET,
            payload.toReceiverIntent(context, ACTION_MARK_TAKEN),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val snoozeIntent = PendingIntent.getBroadcast(
            context,
            payload.scheduleId.toInt() + SNOOZE_REQUEST_OFFSET,
            payload.toReceiverIntent(context, ACTION_SNOOZE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("ĐẾN GIỜ UỐNG THUỐC")
            .setContentText("${payload.medicineName} - ${payload.dosage}")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("${payload.medicineName} - ${payload.dosage}\n${payload.instruction}")
            )
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(fullScreenIntent, true)
            .setContentIntent(fullScreenIntent)
            .addAction(0, "Đã uống", takenIntent)
            .addAction(0, "Nhắc lại sau 10 phút", snoozeIntent)
            .build()

        NotificationManagerCompat.from(context).notify(payload.scheduleId.toInt(), notification)
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val channel = NotificationChannel(
            CHANNEL_ID,
            "Chuông nhắc uống thuốc",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Thông báo reo chuông toàn màn hình khi đến giờ uống thuốc."
            setSound(alarmUri, audioAttributes)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 900, 350, 900, 350)
            lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
        }

        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    companion object {
        const val ACTION_ALARM_TRIGGER = "com.example.mediremind.action.ALARM_TRIGGER"
        const val ACTION_MARK_TAKEN = "com.example.mediremind.action.MARK_TAKEN"
        const val ACTION_SNOOZE = "com.example.mediremind.action.SNOOZE"

        const val EXTRA_SCHEDULE_ID = "extra_schedule_id"
        const val EXTRA_MEDICINE_ID = "extra_medicine_id"
        const val EXTRA_MEDICINE_NAME = "extra_medicine_name"
        const val EXTRA_DOSAGE = "extra_dosage"
        const val EXTRA_INSTRUCTION = "extra_instruction"
        const val EXTRA_REMINDER_TIME = "extra_reminder_time"
        const val EXTRA_IS_TEST = "extra_is_test"

        const val CHANNEL_ID = "medicine_alarm_channel"
        private const val TAKEN_REQUEST_OFFSET = 10_000
        private const val SNOOZE_REQUEST_OFFSET = 20_000
        private const val TEN_MINUTES_MILLIS = 10 * 60 * 1000L
    }
}

data class AlarmPayload(
    val scheduleId: Long,
    val medicineId: Long,
    val medicineName: String,
    val dosage: String,
    val instruction: String,
    val reminderTime: String?,
    val isTest: Boolean
) {
    fun toReceiverIntent(context: Context, actionName: String): Intent {
        return Intent(context, AlarmReceiver::class.java).apply {
            action = actionName
            putExtra(AlarmReceiver.EXTRA_SCHEDULE_ID, scheduleId)
            putExtra(AlarmReceiver.EXTRA_MEDICINE_ID, medicineId)
            putExtra(AlarmReceiver.EXTRA_MEDICINE_NAME, medicineName)
            putExtra(AlarmReceiver.EXTRA_DOSAGE, dosage)
            putExtra(AlarmReceiver.EXTRA_INSTRUCTION, instruction)
            putExtra(AlarmReceiver.EXTRA_REMINDER_TIME, reminderTime)
            putExtra(AlarmReceiver.EXTRA_IS_TEST, isTest)
        }
    }

    companion object {
        fun from(intent: Intent): AlarmPayload {
            return AlarmPayload(
                scheduleId = intent.getLongExtra(AlarmReceiver.EXTRA_SCHEDULE_ID, -1L),
                medicineId = intent.getLongExtra(AlarmReceiver.EXTRA_MEDICINE_ID, -1L),
                medicineName = intent.getStringExtra(AlarmReceiver.EXTRA_MEDICINE_NAME)
                    ?: "Thuốc cần uống",
                dosage = intent.getStringExtra(AlarmReceiver.EXTRA_DOSAGE) ?: "",
                instruction = intent.getStringExtra(AlarmReceiver.EXTRA_INSTRUCTION) ?: "",
                reminderTime = intent.getStringExtra(AlarmReceiver.EXTRA_REMINDER_TIME),
                isTest = intent.getBooleanExtra(AlarmReceiver.EXTRA_IS_TEST, false)
            )
        }
    }
}
