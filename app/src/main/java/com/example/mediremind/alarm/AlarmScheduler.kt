package com.example.mediremind.alarm

interface AlarmScheduler {
    fun schedule(
        scheduleId: Long,
        medicineId: Long,
        medicineName: String,
        dosage: String,
        instruction: String,
        time: String
    )

    fun scheduleAfterDelay(
        scheduleId: Long,
        medicineId: Long,
        medicineName: String,
        dosage: String,
        instruction: String,
        delayMillis: Long,
        isTest: Boolean = false
    )

    fun cancel(scheduleId: Long)

    suspend fun rescheduleAll()
}
