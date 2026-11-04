package com.example.mediremind.data.repository

data class TodayDose(
    val doseId: Long,
    val medicineId: Long,
    val scheduleId: Long,
    val medicineName: String,
    val dosage: String,
    val instruction: String,
    val reminderTime: String,
    val isTaken: Boolean,
    val takenTimestamp: Long?,
    val repeatType: String = "DAILY",
    val notes: String = ""
) {
    val isAsNeeded: Boolean
        get() = repeatType == "AS_NEEDED" || reminderTime == "Khi cần"
}
