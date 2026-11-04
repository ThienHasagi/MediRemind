package com.example.employeemanagement.domain.attendance

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class AttendanceMoment(val date: String, val time: String) {
    val month: String get() = date.substring(0, 7)
}

object AttendanceTime {
    fun now(date: Date = Date(), zone: TimeZone = TimeZone.getDefault()): AttendanceMoment {
        // yyyy is the calendar year; YYYY would incorrectly use the week-based year.
        fun format(pattern: String) = SimpleDateFormat(pattern, Locale.US).apply { timeZone = zone }.format(date)
        return AttendanceMoment(format("yyyy-MM-dd"), format("HH:mm:ss"))
    }

    fun calculateHours(checkInTime: String, checkOutTime: String): Double {
        val elapsedSeconds = secondsOfDay(checkOutTime) - secondsOfDay(checkInTime)
        require(elapsedSeconds >= 0) { "Giờ Check Out không được sớm hơn giờ Check In. Vui lòng kiểm tra đồng hồ thiết bị." }
        // Divide seconds by 3600, rounding once with an explicit HALF_UP rule.
        return BigDecimal.valueOf(elapsedSeconds.toLong())
            .divide(BigDecimal.valueOf(3600), 2, RoundingMode.HALF_UP)
            .toDouble()
    }

    private fun secondsOfDay(time: String): Int {
        require(Regex("^(?:[01][0-9]|2[0-3]):[0-5][0-9]:[0-5][0-9]$").matches(time)) {
            "Giờ chấm công phải đúng định dạng HH:mm:ss."
        }
        val (hours, minutes, seconds) = time.split(':').map(String::toInt)
        return hours * 3600 + minutes * 60 + seconds
    }

    fun shiftMonth(month: String, offset: Int): String {
        val (year, monthNumber) = month.split('-').map(String::toInt)
        val index = (year * 12 + monthNumber - 1 + offset).coerceIn(12, 119999)
        return String.format(Locale.US, "%04d-%02d", index / 12, index % 12 + 1)
    }
}
