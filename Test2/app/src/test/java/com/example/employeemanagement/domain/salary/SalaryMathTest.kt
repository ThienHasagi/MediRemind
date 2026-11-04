package com.example.employeemanagement.domain.salary

import com.example.employeemanagement.data.local.entity.WorkLog
import org.junit.Assert.assertEquals
import org.junit.Test

class SalaryMathTest {
    private fun log(date: String, hours: Double, status: String = "COMPLETED") = WorkLog(
        employeeId = 1, workDate = date, checkInTime = "08:00:00",
        checkOutTime = "18:00:00", totalHours = hours, status = status,
    )

    @Test
    fun dailyOvertimeDoesNotCancelAgainstShorterDays() {
        val result = SalaryMath.calculate(listOf(log("2026-10-01", 10.0), log("2026-10-02", 6.0)), 50000.0)
        assertEquals(16.0, result.totalHours, 0.0)
        assertEquals(2.0, result.overtimeHours, 0.0)
        assertEquals(850000.0, result.totalSalary, 0.0)
    }

    @Test
    fun multipleShiftsOnSameDateShareEightHourThreshold() {
        val result = SalaryMath.calculate(listOf(log("2026-10-01", 6.0), log("2026-10-01", 4.0)), 50000.0)
        assertEquals(2.0, result.overtimeHours, 0.0)
        assertEquals(550000.0, result.totalSalary, 0.0)
    }

    @Test
    fun onlyCompletedLogsArePaid() {
        val result = SalaryMath.calculate(listOf(log("2026-10-01", 8.0), log("2026-10-02", 20.0, "WORKING")), 50000.0)
        assertEquals(8.0, result.totalHours, 0.0)
        assertEquals(0.0, result.overtimeHours, 0.0)
        assertEquals(400000.0, result.totalSalary, 0.0)
    }

    @Test
    fun emptyMonthIsZero() {
        assertEquals(SalaryTotals(0.0, 0.0, 0.0), SalaryMath.calculate(emptyList(), 50000.0))
    }

    @Test
    fun monthlyHoursAbove160DoNotAddOvertimeWhenEveryDayIsEightHours() {
        val logs = (1..21).map { log("2026-10-${it.toString().padStart(2, '0')}", 8.0) }
        val result = SalaryMath.calculate(logs, 50000.0)
        assertEquals(168.0, result.totalHours, 0.0)
        assertEquals(0.0, result.overtimeHours, 0.0)
        assertEquals(8400000.0, result.totalSalary, 0.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun negativeHoursAreRejected() {
        SalaryMath.calculate(listOf(log("2026-10-01", -1.0)), 50000.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun nonFiniteRateIsRejected() {
        SalaryMath.calculate(emptyList(), Double.NaN)
    }
}
