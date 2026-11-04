package com.example.employeemanagement.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.employeemanagement.data.local.entity.Account
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(account: Account): Long

    @Update
    suspend fun update(account: Account): Int

    @Delete
    suspend fun delete(account: Account): Int

    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("SELECT * FROM accounts ORDER BY id")
    fun observeAll(): Flow<List<Account>>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun getById(id: Long): Account?

    @Query("SELECT * FROM accounts WHERE username = :username LIMIT 1")
    suspend fun getByUsername(username: String): Account?

    @Query("SELECT * FROM accounts WHERE username = :username AND password = :password LIMIT 1")
    suspend fun login(username: String, password: String): Account?

    @Query("SELECT * FROM accounts WHERE role = :role ORDER BY username")
    fun observeByRole(role: String): Flow<List<Account>>
}
