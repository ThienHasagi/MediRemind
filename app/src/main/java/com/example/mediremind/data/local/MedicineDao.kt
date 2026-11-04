package com.example.mediremind.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicineDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedicine(medicine: MedicineEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSchedule(schedule: ScheduleEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSchedules(schedules: List<ScheduleEntity>)

    @Update
    suspend fun updateMedicine(medicine: MedicineEntity)

    @Update
    suspend fun updateSchedule(schedule: ScheduleEntity)

    @Delete
    suspend fun deleteMedicine(medicine: MedicineEntity)

    @Query("DELETE FROM medicines WHERE id = :medicineId")
    suspend fun deleteMedicineById(medicineId: Long)

    @Query("DELETE FROM medicines WHERE remoteId IS NOT NULL")
    suspend fun deleteRemoteMedicines()

    @Query("SELECT * FROM medicines WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getMedicineByRemoteId(remoteId: String): MedicineEntity?

    @Query("SELECT * FROM schedules WHERE medicineId = :medicineId ORDER BY reminderTime ASC")
    suspend fun getSchedulesForMedicine(medicineId: Long): List<ScheduleEntity>

    @Query("DELETE FROM schedules WHERE medicineId = :medicineId")
    suspend fun deleteSchedulesForMedicine(medicineId: Long)

    @Query("DELETE FROM medicines WHERE remoteId IS NOT NULL AND remoteId NOT IN (:remoteIds)")
    suspend fun deleteRemoteMedicinesNotIn(remoteIds: List<String>)

    @Query("DELETE FROM medicines WHERE remoteId IS NOT NULL")
    suspend fun deleteAllRemoteMedicines()

    @Query("SELECT * FROM medicines ORDER BY createdAt DESC")
    fun observeMedicines(): Flow<List<MedicineEntity>>

    @Transaction
    @Query("SELECT * FROM medicines ORDER BY createdAt DESC")
    fun observeMedicinesWithSchedules(): Flow<List<MedicineWithSchedules>>

    @Query("SELECT COUNT(*) FROM medicines")
    suspend fun countMedicines(): Int

    @Query(
        """
        SELECT
            schedules.id AS scheduleId,
            medicines.id AS medicineId,
            medicines.name AS medicineName,
            medicines.dosage AS dosage,
            medicines.instruction AS instruction,
            schedules.reminderTime AS reminderTime,
            medicines.repeatType AS repeatType,
            medicines.startDateEpochDay AS startDateEpochDay
        FROM schedules
        INNER JOIN medicines ON medicines.id = schedules.medicineId
        WHERE schedules.isActive = 1
        ORDER BY schedules.reminderTime ASC
        """
    )
    suspend fun getActiveSchedules(): List<ActiveSchedule>

    @Transaction
    suspend fun insertMedicineWithSchedules(
        medicine: MedicineEntity,
        reminderTimes: List<String>
    ): Long {
        val medicineId = insertMedicine(medicine)
        val schedules = reminderTimes.map { time ->
            ScheduleEntity(
                medicineId = medicineId,
                reminderTime = time,
                isActive = true
            )
        }
        insertSchedules(schedules)
        return medicineId
    }

    @Transaction
    suspend fun replaceRemoteMedicines(
        medicinesWithTimes: List<Pair<MedicineEntity, List<String>>>
    ) {
        val remoteIds = medicinesWithTimes.mapNotNull { it.first.remoteId }
        if (remoteIds.isEmpty()) {
            deleteAllRemoteMedicines()
            return
        }

        deleteRemoteMedicinesNotIn(remoteIds)
        medicinesWithTimes.forEach { (medicine, reminderTimes) ->
            val remoteId = medicine.remoteId
            val existingMedicine = remoteId?.let { getMedicineByRemoteId(it) }

            if (existingMedicine == null) {
                insertMedicineWithSchedules(
                    medicine = medicine,
                    reminderTimes = reminderTimes
                )
            } else {
                val normalizedReminderTimes = reminderTimes.sorted()
                val existingReminderTimes = getSchedulesForMedicine(existingMedicine.id)
                    .filter { it.isActive }
                    .map { it.reminderTime }
                    .sorted()

                val updatedMedicine = existingMedicine.copy(
                    name = medicine.name,
                    dosage = medicine.dosage,
                    instruction = medicine.instruction,
                    repeatType = medicine.repeatType,
                    startDateEpochDay = medicine.startDateEpochDay,
                    notes = medicine.notes,
                    createdAt = medicine.createdAt
                )
                updateMedicine(updatedMedicine)

                if (existingReminderTimes != normalizedReminderTimes) {
                    deleteSchedulesForMedicine(existingMedicine.id)
                    insertSchedules(
                        normalizedReminderTimes.map { time ->
                            ScheduleEntity(
                                medicineId = existingMedicine.id,
                                reminderTime = time,
                                isActive = true
                            )
                        }
                    )
                }
            }
        }
    }
}
