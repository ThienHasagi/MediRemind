package com.example.employeemanagement.data.local

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.employeemanagement.data.local.entity.Salary
import com.example.employeemanagement.data.local.entity.WorkLog
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseTest {
    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private val databaseName = "employee-database-test.db"

    private fun openDatabase(): AppDatabase = Room.databaseBuilder(
        context, AppDatabase::class.java, databaseName,
    ).addCallback(AppDatabase.SeedCallback()).build()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(databaseName)
        database = openDatabase()
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun firstQuerySeesAllSeedsAndReopenDoesNotReseed() = runBlocking {
        val accounts = database.accountDao().observeAll().first()
        assertEquals(3, accounts.size)
        assertEquals("ADMIN", database.accountDao().login("admin", "123456")?.role)
        assertNull(database.accountDao().login("admin", "wrong"))
        for ((username, code) in listOf("emp01" to "NV01", "emp02" to "NV02")) {
            val account = requireNotNull(database.accountDao().login(username, "123456"))
            assertEquals("EMPLOYEE", account.role)
            val employee = requireNotNull(database.employeeDao().getByAccountId(account.id))
            assertEquals(code, employee.code)
            assertEquals(50000.0, employee.hourlyRate, 0.0)
        }
        val employee = requireNotNull(database.employeeDao().getByCode("NV01"))
        database.employeeDao().update(employee.copy(fullname = "Updated Name"))
        database.accountDao().deleteById(requireNotNull(database.accountDao().getByUsername("emp02")).id)
        database.close()
        database = openDatabase()
        assertEquals(2, database.accountDao().observeAll().first().size)
        assertNull(database.accountDao().getByUsername("emp02"))
        assertEquals("Updated Name", database.employeeDao().getByCode("NV01")?.fullname)
    }

    @Test
    fun workLogCheckoutAndMonthQueriesHandleUnfinishedShifts() = runBlocking {
        val employee = requireNotNull(database.employeeDao().getByCode("NV01"))
        val dao = database.workLogDao()
        val id = dao.insert(WorkLog(
            employeeId = employee.id, workDate = "2026-10-24",
            checkInTime = "08:00:00", checkOutTime = null,
            totalHours = 0.0, status = "WORKING",
        ))
        assertEquals(id, dao.getOpenWorkLog(employee.id)?.id)
        assertEquals(0.0, dao.getTotalHoursForMonth(employee.id, 10, 2026), 0.0)
        assertEquals(1, dao.checkOut(id, "17:00:00", 8.0, "COMPLETED"))
        assertEquals(0, dao.checkOut(id, "18:00:00", 9.0, "COMPLETED"))
        assertNull(dao.getOpenWorkLog(employee.id))
        assertEquals(8.0, dao.getTotalHoursForMonth(employee.id, 10, 2026), 0.0)
        assertEquals(0.0, dao.getTotalHoursForMonth(employee.id, 10, 2025), 0.0)
        assertEquals(1, dao.getByEmployeeAndMonth(employee.id, 10, 2026).size)
        assertEquals(1, dao.deleteById(id))
        assertNull(dao.getById(id))
    }

    @Test
    fun duplicateSalaryIsRejectedAndAccountDeletionCascadesToDependents() = runBlocking {
        val employee = requireNotNull(database.employeeDao().getByCode("NV01"))
        val salary = Salary(
            employeeId = employee.id, month = 10, year = 2026,
            totalHours = 8.0, overtimeHours = 0.0, totalSalary = 400000.0, status = "PENDING",
        )
        val salaryId = database.salaryDao().insert(salary)
        try {
            database.salaryDao().insert(salary)
            fail("A duplicate monthly salary must violate the unique index")
        } catch (_: SQLiteConstraintException) {
            // The existing salary must survive the rejected insert.
        }
        assertEquals(salaryId, database.salaryDao().getByEmployeeAndMonth(employee.id, 10, 2026)?.id)
        assertEquals(1, database.salaryDao().updateStatus(salaryId, "PAID"))
        assertEquals("PAID", database.salaryDao().getById(salaryId)?.status)
        val workLogId = database.workLogDao().insert(WorkLog(
            employeeId = employee.id, workDate = "2026-10-24", checkInTime = "08:00:00",
            checkOutTime = "17:00:00", totalHours = 8.0, status = "COMPLETED",
        ))
        assertEquals(1, database.accountDao().deleteById(employee.accountId))
        assertNull(database.employeeDao().getById(employee.id))
        assertNull(database.workLogDao().getById(workLogId))
        assertNull(database.salaryDao().getById(salaryId))
        assertNotNull(database.employeeDao().getByCode("NV02"))
    }
}
