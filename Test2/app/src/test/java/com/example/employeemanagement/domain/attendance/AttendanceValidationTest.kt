package com.example.employeemanagement.domain.attendance

import com.example.employeemanagement.data.local.entity.WorkLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone

class AttendanceValidationTest {
    private fun checkedIn() = WorkLog(
        id = 1, employeeId = 1, workDate = "2026-10-24", checkInTime = "08:00:00",
        checkOutTime = null, totalHours = 0.0, status = "CHECKED_IN",
    )

    @Test
    fun computesWholeAndFractionalHours() {
        assertEquals(8.0, AttendanceTime.calculateHours("08:00:00", "16:00:00"), 0.0)
        assertEquals(8.5, AttendanceTime.calculateHours("08:15:00", "16:45:00"), 0.0)
        assertEquals(1.02, AttendanceTime.calculateHours("08:00:00", "09:01:00"), 0.0)
    }

    @Test
    fun roundsHalfUpAtExactHalfCentihour() {
        assertEquals(0.01, AttendanceTime.calculateHours("08:00:00", "08:00:18"), 0.0)
        assertEquals(0.0, AttendanceTime.calculateHours("08:00:00", "08:00:17"), 0.0)
    }

    @Test
    fun sameSecondIsZeroHours() {
        assertEquals(0.0, AttendanceTime.calculateHours("08:00:00", "08:00:00"), 0.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun reversedTimeIsRejectedRatherThanTreatedAsOvernight() {
        AttendanceTime.calculateHours("23:00:00", "01:00:00")
    }

    @Test(expected = IllegalArgumentException::class)
    fun impossibleTimeIsRejected() {
        AttendanceTime.calculateHours("08:60:00", "17:00:00")
    }

    @Test(expected = IllegalArgumentException::class)
    fun nonPaddedTimeIsRejected() {
        AttendanceTime.calculateHours("8:00:00", "17:00:00")
    }

    @Test
    fun dateUsesCalendarYearAtYearBoundary() {
        val zone = TimeZone.getTimeZone("UTC")
        val calendar = GregorianCalendar(zone, Locale.US).apply {
            clear()
            set(2018, Calendar.DECEMBER, 31, 23, 59, 59)
        }
        assertEquals(AttendanceMoment("2018-12-31", "23:59:59"), AttendanceTime.now(calendar.time, zone))
        assertEquals("2019-01", AttendanceTime.shiftMonth("2018-12", 1))
        assertEquals("2018-12", AttendanceTime.shiftMonth("2019-01", -1))
    }

    @Test
    fun firstCheckInAndOpenCheckOutAreAllowed() {
        AttendanceValidation.validateCheckIn(null)
        val log = checkedIn()
        assertSame(log, AttendanceValidation.validateCheckOut(log, log.workDate))
    }

    @Test(expected = IllegalArgumentException::class)
    fun secondCheckInIsRejected() {
        AttendanceValidation.validateCheckIn(checkedIn())
    }

    @Test(expected = IllegalArgumentException::class)
    fun secondCheckInAfterCompletedShiftIsAlsoRejected() {
        AttendanceValidation.validateCheckIn(checkedIn().copy(checkOutTime = "17:00:00", status = "COMPLETED"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun checkOutWithoutCheckInIsRejected() {
        AttendanceValidation.validateCheckOut(null, "2026-10-24")
    }

    @Test(expected = IllegalArgumentException::class)
    fun secondCheckOutIsRejected() {
        AttendanceValidation.validateCheckOut(checkedIn().copy(checkOutTime = "17:00:00", status = "COMPLETED"), "2026-10-24")
    }

    @Test(expected = IllegalArgumentException::class)
    fun previousDayCheckOutIsRejected() {
        AttendanceValidation.validateCheckOut(checkedIn(), "2026-10-25")
    }

    @Test(expected = IllegalArgumentException::class)
    fun unexpectedStatusIsRejected() {
        AttendanceValidation.validateCheckOut(checkedIn().copy(status = "UNKNOWN"), "2026-10-24")
    }
}
