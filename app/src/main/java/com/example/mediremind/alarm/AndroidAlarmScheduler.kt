package com.example.mediremind.alarm

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.mediremind.data.local.AppDatabase
import com.example.mediremind.receiver.AlarmReceiver
import com.example.mediremind.data.repository.Recurrence
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class AndroidAlarmScheduler(
    private val context: Context,
    private val database: AppDatabase = AppDatabase.getInstance(context)
) : AlarmScheduler {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    override fun schedule(
        scheduleId: Long,
        medicineId: Long,
        medicineName: String,
        dosage: String,
        instruction: String,
        time: String
    ) {
        val triggerAtMillis = nextTriggerMillis(time)
        scheduleAt(
            scheduleId = scheduleId,
            medicineId = medicineId,
            medicineName = medicineName,
            dosage = dosage,
            instruction = instruction,
            triggerAtMillis = triggerAtMillis,
            reminderTime = time,
            isTest = false
        )
    }

    override fun scheduleAfterDelay(
        scheduleId: Long,
        medicineId: Long,
        medicineName: String,
        dosage: String,
        instruction: String,
        delayMillis: Long,
        isTest: Boolean
    ) {
        scheduleAt(
            scheduleId = scheduleId,
            medicineId = medicineId,
            medicineName = medicineName,
            dosage = dosage,
            instruction = instruction,
            triggerAtMillis = System.currentTimeMillis() + delayMillis,
            reminderTime = null,
            isTest = isTest
        )
    }

    override fun cancel(scheduleId: Long) {
        alarmManager.cancel(pendingIntent(scheduleId, emptyAlarmIntent(scheduleId)))
    }

    override suspend fun rescheduleAll() {
        database.medicineDao().getActiveSchedules().forEach { schedule ->
            cancel(schedule.scheduleId)
            val time = runCatching { LocalTime.parse(schedule.reminderTime) }.getOrNull()
                ?: return@forEach
            val start = if (schedule.startDateEpochDay > 0) LocalDate.ofEpochDay(schedule.startDateEpochDay)
                else LocalDate.now()
            val next = Recurrence.next(schedule.repeatType, start, time, LocalDateTime.now())
                ?: return@forEach
            scheduleAt(
                scheduleId = schedule.scheduleId,
                medicineId = schedule.medicineId,
                medicineName = schedule.medicineName,
                dosage = schedule.dosage,
                instruction = schedule.instruction,
                triggerAtMillis = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                reminderTime = schedule.reminderTime,
                isTest = false
            )
        }
    }

    private fun scheduleAt(
        scheduleId: Long,
        medicineId: Long,
        medicineName: String,
        dosage: String,
        instruction: String,
        triggerAtMillis: Long,
        reminderTime: String?,
        isTest: Boolean
    ) {
        if (!canScheduleExactAlarms()) return

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_ALARM_TRIGGER
            putExtra(AlarmReceiver.EXTRA_SCHEDULE_ID, scheduleId)
            putExtra(AlarmReceiver.EXTRA_MEDICINE_ID, medicineId)
            putExtra(AlarmReceiver.EXTRA_MEDICINE_NAME, medicineName)
            putExtra(AlarmReceiver.EXTRA_DOSAGE, dosage)
            putExtra(AlarmReceiver.EXTRA_INSTRUCTION, instruction)
            putExtra(AlarmReceiver.EXTRA_REMINDER_TIME, reminderTime)
            putExtra(AlarmReceiver.EXTRA_IS_TEST, isTest)
        }

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            pendingIntent(scheduleId, intent)
        )
    }

    private fun pendingIntent(scheduleId: Long, intent: Intent): PendingIntent {
        return PendingIntent.getBroadcast(
            context,
            scheduleId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun emptyAlarmIntent(scheduleId: Long): Intent {
        return Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_ALARM_TRIGGER
            putExtra(AlarmReceiver.EXTRA_SCHEDULE_ID, scheduleId)
        }
    }

    private fun canScheduleExactAlarms(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.SCHEDULE_EXACT_ALARM
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            return true
        }
        return alarmManager.canScheduleExactAlarms()
    }

    private fun nextTriggerMillis(time: String): Long {
        val reminderTime = LocalTime.parse(time)
        var triggerDateTime = LocalDateTime.of(LocalDate.now(), reminderTime)
        if (!triggerDateTime.isAfter(LocalDateTime.now())) {
            triggerDateTime = triggerDateTime.plusDays(1)
        }
        return triggerDateTime
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }
}
