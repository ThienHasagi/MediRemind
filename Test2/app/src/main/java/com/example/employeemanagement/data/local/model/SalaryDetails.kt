package com.example.employeemanagement.data.local.model

import androidx.room.Embedded
import androidx.room.Ignore
import com.example.employeemanagement.data.local.entity.Salary

data class SalaryDetails(
    @Embedded val salary: Salary,
    val employeeCode: String,
    val employeeName: String,
    val currentHourlyRate: Double,
) {
    // Recover the rate used in this calculation without changing the requested Salary schema.
    @get:Ignore
    val calculatedHourlyRate: Double
        get() {
            val weightedHours = salary.totalHours + salary.overtimeHours * 0.5
            return if (weightedHours > 0) salary.totalSalary / weightedHours else currentHourlyRate
        }
}
