package com.example.employeemanagement.domain.salary

import com.example.employeemanagement.data.local.entity.WorkLog
import kotlin.math.max

data class SalaryTotals(val totalHours: Double, val overtimeHours: Double, val totalSalary: Double)

object SalaryMath {
    fun calculate(logs: List<WorkLog>, hourlyRate: Double): SalaryTotals {
        require(hourlyRate.isFinite() && hourlyRate > 0) { "Mức lương theo giờ phải là số dương." }
        val completed = logs.filter { it.status == "COMPLETED" }
        require(completed.all { it.totalHours.isFinite() && it.totalHours >= 0 }) {
            "Dữ liệu giờ làm không hợp lệ. Vui lòng kiểm tra chấm công."
        }
        // Multiple shifts on the same date share one eight-hour daily allowance.
        val dailyHours = completed.groupBy { it.workDate }.values.map { day -> day.sumOf { it.totalHours } }
        val totalHours = dailyHours.sum()
        val overtimeHours = dailyHours.sumOf { max(0.0, it - 8.0) }
        val totalSalary = (totalHours - overtimeHours) * hourlyRate + overtimeHours * hourlyRate * 1.5
        require(totalHours.isFinite() && overtimeHours.isFinite() && totalSalary.isFinite()) {
            "Kết quả tính lương vượt giới hạn số cho phép."
        }
        return SalaryTotals(totalHours, overtimeHours, totalSalary)
    }
}
