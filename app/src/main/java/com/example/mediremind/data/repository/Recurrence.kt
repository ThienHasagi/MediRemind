package com.example.mediremind.data.repository

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Month-end and leap-day schedules use the last valid day of the target month. */
object Recurrence {
    fun isDue(type: String, start: LocalDate, date: LocalDate): Boolean {
        if (date < start) return false
        return when (type) {
            "DAILY", "AS_NEEDED" -> true
            "WEEKLY" -> (date.toEpochDay() - start.toEpochDay()) % 7L == 0L
            "MONTHLY" -> date == start.plusMonths(
                (date.year - start.year) * 12L + date.monthValue - start.monthValue)
            "YEARLY" -> date == start.plusYears((date.year - start.year).toLong())
            else -> false
        }
    }

    fun next(type: String, start: LocalDate, time: LocalTime, now: LocalDateTime): LocalDateTime? {
        if (type == "AS_NEEDED") return null
        var date = maxOf(start, now.toLocalDate())
        // Covers leap years, including legacy schedules anchored on February 29.
        repeat(1462) {
            val candidate = date.atTime(time)
            if (candidate > now && isDue(type, start, date)) return candidate
            date = date.plusDays(1)
        }
        return null
    }
}
