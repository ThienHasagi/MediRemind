package com.example.employeemanagement.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.employeemanagement.data.local.entity.Salary
import com.example.employeemanagement.data.local.model.SalaryDetails
import kotlinx.coroutines.flow.Flow

@Dao
interface SalaryDao {
    @Query("""
        SELECT s.*, e.code AS employeeCode, e.fullname AS employeeName,
               e.hourlyRate AS currentHourlyRate
        FROM salaries s INNER JOIN employees e ON e.id = s.employeeId
        WHERE s.month = :month AND s.year = :year ORDER BY e.code
    """)
    fun observeMonthlyDetails(month: Int, year: Int): Flow<List<SalaryDetails>>

    @Query("""
        SELECT s.*, e.code AS employeeCode, e.fullname AS employeeName,
               e.hourlyRate AS currentHourlyRate
        FROM salaries s INNER JOIN employees e ON e.id = s.employeeId
        WHERE s.employeeId = :employeeId AND s.month = :month AND s.year = :year
    """)
    fun observeEmployeeMonthlyDetails(employeeId: Long, month: Int, year: Int): Flow<List<SalaryDetails>>

    @Query("UPDATE salaries SET status = 'PAID' WHERE id = :id AND status = 'PENDING'")
    suspend fun markAsPaid(id: Long): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(salary: Salary): Long

    @Update
    suspend fun update(salary: Salary): Int

    @Delete
    suspend fun delete(salary: Salary): Int

    @Query("DELETE FROM salaries WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("SELECT * FROM salaries ORDER BY year DESC, month DESC, employeeId")
    fun observeAll(): Flow<List<Salary>>

    @Query("SELECT * FROM salaries WHERE id = :id")
    suspend fun getById(id: Long): Salary?

    @Query("SELECT * FROM salaries WHERE employeeId = :employeeId ORDER BY year DESC, month DESC")
    fun observeByEmployee(employeeId: Long): Flow<List<Salary>>

    @Query("SELECT * FROM salaries WHERE employeeId = :employeeId AND month = :month AND year = :year LIMIT 1")
    suspend fun getByEmployeeAndMonth(employeeId: Long, month: Int, year: Int): Salary?

    @Query("SELECT * FROM salaries WHERE month = :month AND year = :year ORDER BY employeeId")
    fun observeByMonth(month: Int, year: Int): Flow<List<Salary>>

    @Query("SELECT * FROM salaries WHERE status = :status ORDER BY year DESC, month DESC, employeeId")
    fun observeByStatus(status: String): Flow<List<Salary>>

    @Query("UPDATE salaries SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String): Int
}
