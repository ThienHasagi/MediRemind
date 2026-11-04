package com.example.mediremind.data.repository

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class RecurrenceTest {
    @Test fun weeklyDoesNotBecomeDaily() {
        val start = LocalDate.parse("2026-10-15")
        assertFalse(Recurrence.isDue("WEEKLY", start, start.plusDays(1)))
        assertTrue(Recurrence.isDue("WEEKLY", start, start.plusWeeks(1)))
        assertEquals(LocalDateTime.parse("2026-10-22T08:00"),
            Recurrence.next("WEEKLY", start, LocalTime.of(8, 0), start.atTime(8, 0)))
    }

    @Test fun monthEndClampsWithoutDrifting() {
        val start = LocalDate.parse("2026-01-31")
        assertTrue(Recurrence.isDue("MONTHLY", start, LocalDate.parse("2026-02-28")))
        assertTrue(Recurrence.isDue("MONTHLY", start, LocalDate.parse("2026-03-31")))
        assertFalse(Recurrence.isDue("MONTHLY", start, LocalDate.parse("2026-03-28")))
        assertEquals(LocalDateTime.parse("2026-03-31T08:00"), Recurrence.next("MONTHLY", start,
            LocalTime.of(8, 0), LocalDateTime.parse("2026-02-28T09:00")))
    }

    @Test fun leapDayClampsAndReturnsInLeapYear() {
        val start = LocalDate.parse("2024-02-29")
        assertTrue(Recurrence.isDue("YEARLY", start, LocalDate.parse("2025-02-28")))
        assertFalse(Recurrence.isDue("YEARLY", start, LocalDate.parse("2028-02-28")))
        assertTrue(Recurrence.isDue("YEARLY", start, LocalDate.parse("2028-02-29")))
    }

    @Test fun futureStartIsRespected() {
        val start = LocalDate.parse("2027-01-01")
        for (type in listOf("DAILY", "WEEKLY", "MONTHLY", "YEARLY", "AS_NEEDED")) {
            assertFalse(Recurrence.isDue(type, start, start.minusDays(1)))
        }
        assertEquals(start.atTime(8, 0), Recurrence.next("YEARLY", start, LocalTime.of(8, 0),
            LocalDateTime.parse("2026-10-15T12:00")))
    }

    @Test fun asNeededNeverSchedulesAnAlarm() {
        val start = LocalDate.parse("2026-10-15")
        assertNull(Recurrence.next("AS_NEEDED", start, LocalTime.NOON, start.atStartOfDay()))
    }

    @Test fun dailyRollsOverAfterTimeHasPassed() {
        val start = LocalDate.parse("2026-10-15")
        assertEquals(start.plusDays(1).atTime(8, 0), Recurrence.next("DAILY", start,
            LocalTime.of(8, 0), start.atTime(9, 0)))
    }

    @Test fun unknownRepeatTypeDoesNotCreateDailyAlarm() {
        val start = LocalDate.parse("2026-10-15")
        assertNull(Recurrence.next("UNKNOWN", start, LocalTime.NOON, start.atStartOfDay()))
    }
}
