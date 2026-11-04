package com.example.mediremind.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "medicines")
data class MedicineEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val remoteId: String? = null,
    val name: String,
    val dosage: String,
    val instruction: String,
    val repeatType: String = "DAILY",
    val startDateEpochDay: Long = 0L,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
