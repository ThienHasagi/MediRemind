package com.example.mediremind.data.local

import androidx.room.Embedded
import androidx.room.Relation

data class MedicineWithSchedules(
    @Embedded
    val medicine: MedicineEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "medicineId"
    )
    val schedules: List<ScheduleEntity>
)
