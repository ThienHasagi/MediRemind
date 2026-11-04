package com.example.employeemanagement.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Transaction
import com.example.employeemanagement.data.local.entity.Account
import com.example.employeemanagement.data.local.entity.Employee
import kotlinx.coroutines.flow.Flow

@Dao
interface EmployeeDao {
    @Query("SELECT * FROM employees ORDER BY code")
    suspend fun getAll(): List<Employee>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAccount(account: Account): Long

    @Transaction
    suspend fun insertWithAccount(employee: Employee): Long {
        val accountId = insertAccount(Account(
            username = employee.code,
            password = "123456",
            role = "EMPLOYEE",
        ))
        return insert(employee.copy(id = 0, accountId = accountId))
    }

    @Query("UPDATE accounts SET username = :username WHERE id = :accountId AND role = 'EMPLOYEE'")
    suspend fun updateAccountUsername(accountId: Long, username: String): Int

    @Transaction
    suspend fun updateWithAccount(employee: Employee) {
        val previous = checkNotNull(getById(employee.id)) { "Employee no longer exists" }
        // Existing seed usernames (emp01/emp02) remain intact unless the code changes.
        if (previous.code != employee.code) {
            check(updateAccountUsername(previous.accountId, employee.code) == 1)
        }
        check(update(employee.copy(accountId = previous.accountId)) == 1)
    }

    @Query("DELETE FROM accounts WHERE id = :accountId AND role = 'EMPLOYEE'")
    suspend fun deleteEmployeeAccount(accountId: Long): Int

    @Transaction
    suspend fun deleteWithAccount(employeeId: Long) {
        val employee = checkNotNull(getById(employeeId)) { "Employee no longer exists" }
        // Deleting the parent also removes the employee, work logs and salaries via CASCADE.
        check(deleteEmployeeAccount(employee.accountId) == 1)
    }

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(employee: Employee): Long

    @Update
    suspend fun update(employee: Employee): Int

    @Delete
    suspend fun delete(employee: Employee): Int

    @Query("DELETE FROM employees WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("SELECT * FROM employees ORDER BY code")
    fun observeAll(): Flow<List<Employee>>

    @Query("SELECT * FROM employees WHERE id = :id")
    suspend fun getById(id: Long): Employee?

    @Query("SELECT * FROM employees WHERE accountId = :accountId LIMIT 1")
    suspend fun getByAccountId(accountId: Long): Employee?

    @Query("SELECT * FROM employees WHERE code = :code LIMIT 1")
    suspend fun getByCode(code: String): Employee?

    @Query("""
        SELECT * FROM employees
        WHERE code LIKE '%' || :keyword || '%'
           OR fullname LIKE '%' || :keyword || '%'
        ORDER BY code
    """)
    fun search(keyword: String): Flow<List<Employee>>
}
