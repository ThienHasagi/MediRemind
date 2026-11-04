package com.example.employeemanagement.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.employeemanagement.data.local.entity.WorkLog
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkLogDao {
    @Query("SELECT * FROM work_logs WHERE employeeId = :employeeId AND workDate = :workDate ORDER BY id DESC LIMIT 1")
    suspend fun getTodayWorkLog(employeeId: Long, workDate: String): WorkLog?

    @Query("SELECT * FROM work_logs WHERE employeeId = :employeeId ORDER BY workDate DESC, checkInTime DESC, id DESC")
    fun getHistoryByEmployee(employeeId: Long): Flow<List<WorkLog>>

    @Query("""
        UPDATE work_logs SET checkOutTime = :checkOutTime, totalHours = :totalHours, status = 'COMPLETED'
        WHERE id = :id AND employeeId = :employeeId AND workDate = :workDate
          AND status = 'CHECKED_IN' AND checkOutTime IS NULL
    """)
    suspend fun completeAttendance(
        id: Long, employeeId: Long, workDate: String, checkOutTime: String, totalHours: Double,
    ): Int

    @Query("""
        SELECT * FROM work_logs
        WHERE employeeId = :employeeId AND status = 'COMPLETED'
          AND CAST(strftime('%m', workDate) AS INTEGER) = :month
          AND CAST(strftime('%Y', workDate) AS INTEGER) = :year
        ORDER BY workDate, id
    """)
    suspend fun getCompletedForMonth(employeeId: Long, month: Int, year: Int): List<WorkLog>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(workLog: WorkLog): Long

    @Update
    suspend fun update(workLog: WorkLog): Int

    @Delete
    suspend fun delete(workLog: WorkLog): Int

    @Query("DELETE FROM work_logs WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("SELECT * FROM work_logs ORDER BY workDate DESC, checkInTime DESC, id DESC")
    fun observeAll(): Flow<List<WorkLog>>

    @Query("SELECT * FROM work_logs WHERE id = :id")
    suspend fun getById(id: Long): WorkLog?

    @Query("SELECT * FROM work_logs WHERE employeeId = :employeeId ORDER BY workDate DESC, checkInTime DESC, id DESC")
    fun observeByEmployee(employeeId: Long): Flow<List<WorkLog>>

    @Query("SELECT * FROM work_logs WHERE employeeId = :employeeId AND workDate = :workDate ORDER BY checkInTime, id")
    suspend fun getByEmployeeAndDate(employeeId: Long, workDate: String): List<WorkLog>

    @Query("""
        SELECT * FROM work_logs
        WHERE employeeId = :employeeId AND checkOutTime IS NULL
        ORDER BY workDate DESC, checkInTime DESC, id DESC LIMIT 1
    """)
    suspend fun getOpenWorkLog(employeeId: Long): WorkLog?

    @Query("""
        SELECT * FROM work_logs
        WHERE employeeId = :employeeId
          AND CAST(strftime('%m', workDate) AS INTEGER) = :month
          AND CAST(strftime('%Y', workDate) AS INTEGER) = :year
        ORDER BY workDate, checkInTime, id
    """)
    suspend fun getByEmployeeAndMonth(employeeId: Long, month: Int, year: Int): List<WorkLog>

    @Query("""
        SELECT COALESCE(SUM(totalHours), 0.0) FROM work_logs
        WHERE employeeId = :employeeId AND checkOutTime IS NOT NULL
          AND CAST(strftime('%m', workDate) AS INTEGER) = :month
          AND CAST(strftime('%Y', workDate) AS INTEGER) = :year
    """)
    suspend fun getTotalHoursForMonth(employeeId: Long, month: Int, year: Int): Double

    @Query("""
        UPDATE work_logs SET checkOutTime = :checkOutTime, totalHours = :totalHours, status = :status
        WHERE id = :id AND checkOutTime IS NULL
    """)
    suspend fun checkOut(id: Long, checkOutTime: String, totalHours: Double, status: String): Int
}
