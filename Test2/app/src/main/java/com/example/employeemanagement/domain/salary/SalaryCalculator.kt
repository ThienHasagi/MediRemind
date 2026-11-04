package com.example.employeemanagement.domain.salary

import androidx.room.withTransaction
import com.example.employeemanagement.data.local.AppDatabase
import com.example.employeemanagement.data.local.entity.Salary

class SalaryCalculator(private val database: AppDatabase) {
    suspend fun calculateMonthlySalary(employeeId: Long, month: Int, year: Int): Salary {
        require(month in 1..12 && year in 1..9999) { "Tháng hoặc năm không hợp lệ." }
        return database.withTransaction {
            val employee = requireNotNull(database.employeeDao().getById(employeeId)) { "Không tìm thấy nhân viên." }
            val logs = database.workLogDao().getCompletedForMonth(employeeId, month, year)
            val totals = SalaryMath.calculate(logs, employee.hourlyRate)
            val existing = database.salaryDao().getByEmployeeAndMonth(employeeId, month, year)
            val salary = Salary(
                id = existing?.id ?: 0,
                employeeId = employeeId,
                month = month,
                year = year,
                totalHours = totals.totalHours,
                overtimeHours = totals.overtimeHours,
                totalSalary = totals.totalSalary,
                status = "PENDING",
            )
            if (existing == null) {
                salary.copy(id = database.salaryDao().insert(salary))
            } else {
                check(database.salaryDao().update(salary) == 1)
                salary
            }
        }
    }

    suspend fun calculateAllEmployees(month: Int, year: Int): Int {
        require(month in 1..12 && year in 1..9999) { "Tháng hoặc năm không hợp lệ." }
        // The whole payroll batch either succeeds or rolls back together.
        return database.withTransaction {
            val employees = database.employeeDao().getAll()
            for (employee in employees) calculateMonthlySalary(employee.id, month, year)
            employees.size
        }
    }
}
