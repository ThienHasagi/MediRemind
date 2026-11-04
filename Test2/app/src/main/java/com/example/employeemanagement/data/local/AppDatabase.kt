package com.example.employeemanagement.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.employeemanagement.data.local.dao.AccountDao
import com.example.employeemanagement.data.local.dao.EmployeeDao
import com.example.employeemanagement.data.local.dao.SalaryDao
import com.example.employeemanagement.data.local.dao.WorkLogDao
import com.example.employeemanagement.data.local.entity.Account
import com.example.employeemanagement.data.local.entity.Employee
import com.example.employeemanagement.data.local.entity.Salary
import com.example.employeemanagement.data.local.entity.WorkLog

@Database(
    entities = [Account::class, Employee::class, WorkLog::class, Salary::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun employeeDao(): EmployeeDao
    abstract fun workLogDao(): WorkLogDao
    abstract fun salaryDao(): SalaryDao

    companion object {
        private const val DATABASE_NAME = "employee_management.db"

        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                DATABASE_NAME,
            ).addCallback(SeedCallback()).build().also { instance = it }
        }
    }

    /** Runs inside the database creation transaction, before the first DAO query. */
    internal class SeedCallback : Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            // Use the supplied connection: opening the singleton here would recurse.
            insertAccount(db, "admin", "ADMIN")
            val firstAccountId = insertAccount(db, "emp01", "EMPLOYEE")
            val secondAccountId = insertAccount(db, "emp02", "EMPLOYEE")
            insertEmployee(db, "NV01", "Nguyen Van An", "emp01@example.com", "0900000001", firstAccountId)
            insertEmployee(db, "NV02", "Tran Thi Binh", "emp02@example.com", "0900000002", secondAccountId)
        }

        private fun insertAccount(db: SupportSQLiteDatabase, username: String, role: String): Long =
            db.insert("accounts", SQLiteDatabase.CONFLICT_ABORT, ContentValues().apply {
                put("username", username)
                put("password", "123456")
                put("role", role)
            })

        private fun insertEmployee(
            db: SupportSQLiteDatabase,
            code: String,
            fullname: String,
            email: String,
            phone: String,
            accountId: Long,
        ) {
            db.insert("employees", SQLiteDatabase.CONFLICT_ABORT, ContentValues().apply {
                put("code", code)
                put("fullname", fullname)
                put("email", email)
                put("phone", phone)
                put("hourlyRate", 50000.0)
                put("accountId", accountId)
            })
        }
    }
}
