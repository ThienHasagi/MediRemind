package com.example.mediremind.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DoseLogDao {
    @Query("SELECT * FROM dose_logs WHERE isTaken = 1 ORDER BY dateEpochDay DESC")
    suspend fun getTakenHistory(): List<DoseLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDoseLog(log: DoseLogEntity): Long

    @Query(
        """
        SELECT * FROM dose_logs
        WHERE dateEpochDay = :dateEpochDay
        ORDER BY takenTimestamp DESC
        """
    )
    fun observeDoseLogsByDay(dateEpochDay: Long): Flow<List<DoseLogEntity>>

    @Query(
        """
        SELECT * FROM dose_logs
        WHERE scheduleId = :scheduleId AND dateEpochDay = :dateEpochDay
        LIMIT 1
        """
    )
    suspend fun getDoseLog(scheduleId: Long, dateEpochDay: Long): DoseLogEntity?

    @Query(
        """
        SELECT * FROM dose_logs
        WHERE dateEpochDay = :dateEpochDay
        """
    )
    suspend fun getDoseLogsByDay(dateEpochDay: Long): List<DoseLogEntity>

    @Query(
        """
        DELETE FROM dose_logs
        WHERE scheduleId = :scheduleId AND dateEpochDay = :dateEpochDay
        """
    )
    suspend fun deleteDoseLog(scheduleId: Long, dateEpochDay: Long)

    suspend fun markDoseTaken(
        medicineId: Long,
        scheduleId: Long,
        dateEpochDay: Long,
        takenTimestamp: Long = System.currentTimeMillis()
    ) {
        val existing = getDoseLog(scheduleId, dateEpochDay)
        upsertDoseLog(
            DoseLogEntity(
                id = existing?.id ?: 0,
                medicineId = medicineId,
                scheduleId = scheduleId,
                dateEpochDay = dateEpochDay,
                isTaken = true,
                takenTimestamp = takenTimestamp
            )
        )
    }

    suspend fun markDoseNotTaken(
        medicineId: Long,
        scheduleId: Long,
        dateEpochDay: Long
    ) {
        val existing = getDoseLog(scheduleId, dateEpochDay)
        upsertDoseLog(
            DoseLogEntity(
                id = existing?.id ?: 0,
                medicineId = medicineId,
                scheduleId = scheduleId,
                dateEpochDay = dateEpochDay,
                isTaken = false,
                takenTimestamp = null
            )
        )
    }
}
