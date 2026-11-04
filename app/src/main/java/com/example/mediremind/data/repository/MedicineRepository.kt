package com.example.mediremind.data.repository

import com.example.mediremind.alarm.AlarmScheduler
import com.example.mediremind.data.local.DoseLogDao
import com.example.mediremind.data.local.MedicineDao
import com.example.mediremind.data.local.MedicineEntity
import com.example.mediremind.data.remote.FirestoreMedicineModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import com.example.mediremind.data.remote.RemoteDoseLog
import java.time.LocalDate

class MedicineRepository(
    private val medicineDao: MedicineDao,
    private val doseLogDao: DoseLogDao,
    private val alarmScheduler: AlarmScheduler? = null
) {
    /** Replays saved confirmations, including doses recorded from notifications on earlier days. */
    suspend fun savedHistory(pairCode: String): List<RemoteDoseLog> {
        val schedules = medicineDao.getActiveSchedules().associateBy { it.scheduleId }
        return doseLogDao.getTakenHistory().mapNotNull { log ->
            val schedule = schedules[log.scheduleId] ?: return@mapNotNull null
            RemoteDoseLog(pairCode = pairCode, scheduleId = log.scheduleId,
                medicineId = log.medicineId, medicineName = schedule.medicineName,
                dateEpochDay = log.dateEpochDay, isTaken = true, takenTime = "",
                timestamp = log.takenTimestamp ?: 0L, takenTimestamp = log.takenTimestamp,
                dosage = schedule.dosage, instruction = schedule.instruction,
                reminderTime = schedule.reminderTime)
        }
    }

    fun observeTodayDoses(
        dateEpochDay: Long = LocalDate.now().toEpochDay()
    ): Flow<List<TodayDose>> {
        return combine(
            medicineDao.observeMedicinesWithSchedules(),
            doseLogDao.observeDoseLogsByDay(dateEpochDay)
        ) { medicinesWithSchedules, logs ->
            val logsByScheduleId = logs.associateBy { it.scheduleId }

            medicinesWithSchedules
                .filter { isScheduleActiveOnDate(it.medicine.repeatType, it.medicine.startDateEpochDay, dateEpochDay) }
                .flatMap { medicineWithSchedules ->
                    medicineWithSchedules.schedules
                        .filter { it.isActive }
                        .map { schedule ->
                            val log = logsByScheduleId[schedule.id]
                            TodayDose(
                                doseId = schedule.id,
                                medicineId = medicineWithSchedules.medicine.id,
                                scheduleId = schedule.id,
                                medicineName = medicineWithSchedules.medicine.name,
                                dosage = medicineWithSchedules.medicine.dosage,
                                instruction = medicineWithSchedules.medicine.instruction,
                                reminderTime = schedule.reminderTime,
                                isTaken = log?.isTaken == true,
                                takenTimestamp = log?.takenTimestamp,
                                repeatType = medicineWithSchedules.medicine.repeatType,
                                notes = medicineWithSchedules.medicine.notes
                            )
                        }
                }
                .sortedBy { it.reminderTime }
        }
    }

    suspend fun addMedicineWithSchedules(
        name: String,
        dosage: String,
        instruction: String,
        reminderTimes: List<String>,
        repeatType: String = "DAILY",
        startDateEpochDay: Long = LocalDate.now().toEpochDay(),
        notes: String = ""
    ): Long {
        val effectiveTimes = if (repeatType == "AS_NEEDED" && reminderTimes.isEmpty()) {
            listOf("Khi cần")
        } else {
            reminderTimes
        }
        val medicineId = medicineDao.insertMedicineWithSchedules(
            medicine = MedicineEntity(
                name = name,
                dosage = dosage,
                instruction = instruction,
                repeatType = repeatType,
                startDateEpochDay = startDateEpochDay,
                notes = notes
            ),
            reminderTimes = effectiveTimes
        )
        alarmScheduler?.rescheduleAll()
        return medicineId
    }

    suspend fun markDoseTaken(
        medicineId: Long,
        scheduleId: Long,
        dateEpochDay: Long = LocalDate.now().toEpochDay(),
        takenTimestamp: Long = System.currentTimeMillis()
    ) {
        doseLogDao.markDoseTaken(
            medicineId = medicineId,
            scheduleId = scheduleId,
            dateEpochDay = dateEpochDay,
            takenTimestamp = takenTimestamp
        )
    }

    suspend fun markDoseNotTaken(
        medicineId: Long,
        scheduleId: Long,
        dateEpochDay: Long = LocalDate.now().toEpochDay()
    ) {
        doseLogDao.markDoseNotTaken(
            medicineId = medicineId,
            scheduleId = scheduleId,
            dateEpochDay = dateEpochDay
        )
    }

    suspend fun seedSampleDataIfNeeded() {
        if (medicineDao.countMedicines() > 0) return

        addMedicineWithSchedules(
            name = "Paracetamol",
            dosage = "2 viên",
            instruction = "Uống sau ăn, uống với nhiều nước.",
            reminderTimes = listOf("08:00", "20:00")
        )
        addMedicineWithSchedules(
            name = "Amlodipine",
            dosage = "1 viên",
            instruction = "Uống sau bữa sáng, đo huyết áp nếu thấy chóng mặt.",
            reminderTimes = listOf("07:30")
        )
        addMedicineWithSchedules(
            name = "Vitamin D3",
            dosage = "1 viên",
            instruction = "Uống sau bữa trưa.",
            reminderTimes = listOf("12:30")
        )
        alarmScheduler?.rescheduleAll()
    }

    suspend fun syncRemoteMedicines(remoteMedicines: List<FirestoreMedicineModel>) {
        val medicinesWithTimes = remoteMedicines
            .filter { it.name.isNotBlank() && (it.scheduleTimes.isNotEmpty() || it.repeatType == "AS_NEEDED") }
            .map { remoteMedicine ->
                val times = if (remoteMedicine.repeatType == "AS_NEEDED" && remoteMedicine.scheduleTimes.isEmpty()) {
                    listOf("Khi cần")
                } else {
                    remoteMedicine.scheduleTimes
                }
                MedicineEntity(
                    remoteId = remoteMedicine.id,
                    name = remoteMedicine.name,
                    dosage = remoteMedicine.dosage,
                    instruction = remoteMedicine.instruction,
                    repeatType = remoteMedicine.repeatType,
                    startDateEpochDay = remoteMedicine.startDateEpochDay,
                    notes = remoteMedicine.notes,
                    createdAt = remoteMedicine.createdAt
                ) to times
            }

        // Cancel removed/changed alarms before their IDs disappear from the database.
        medicineDao.getActiveSchedules().forEach { alarmScheduler?.cancel(it.scheduleId) }
        medicineDao.replaceRemoteMedicines(medicinesWithTimes)
        alarmScheduler?.rescheduleAll()
    }

    private fun isScheduleActiveOnDate(
        repeatType: String,
        startDateEpochDay: Long,
        targetDateEpochDay: Long
    ): Boolean {
        if (startDateEpochDay <= 0L) return true
        if (targetDateEpochDay < startDateEpochDay) return false

        val targetDate = LocalDate.ofEpochDay(targetDateEpochDay)
        val startDate = LocalDate.ofEpochDay(startDateEpochDay)

        return Recurrence.isDue(repeatType, startDate, targetDate)
    }
}
