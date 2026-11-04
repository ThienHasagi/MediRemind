package com.example.mediremind.data.local

data class ActiveSchedule(
    val scheduleId: Long,
    val medicineId: Long,
    val medicineName: String,
    val dosage: String,
    val instruction: String,
    val reminderTime: String,
    val repeatType: String,
    val startDateEpochDay: Long
)
