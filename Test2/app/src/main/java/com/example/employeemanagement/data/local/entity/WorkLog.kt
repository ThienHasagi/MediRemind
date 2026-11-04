package com.example.employeemanagement.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "work_logs",
    foreignKeys = [ForeignKey(
        entity = Employee::class,
        parentColumns = ["id"],
        childColumns = ["employeeId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index(value = ["employeeId", "workDate"])],
)
data class WorkLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val employeeId: Long,
    val workDate: String,
    val checkInTime: String,
    val checkOutTime: String?,
    val totalHours: Double,
    val status: String,
)
