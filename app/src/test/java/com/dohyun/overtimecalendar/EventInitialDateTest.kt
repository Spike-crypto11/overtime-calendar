package com.dohyun.overtimecalendar

import org.junit.Assert.assertEquals
import org.junit.Test

class EventInitialDateTest {
    @Test fun selectedCalendarDateWinsOverToday() {
        assertEquals("2026-11-10", EventInitialDate.resolve("2026-11-10", "2026-11-03"))
    }
    @Test fun menuWithoutSelectedDateUsesToday() {
        assertEquals("2026-11-03", EventInitialDate.resolve(null, "2026-11-03"))
    }
    @Test fun rejectsInvalidOrNonCanonicalDates() {
        for (date in listOf("", "2026-02-29", "2026-11-31", "2026-1-2", "2026-11-10x")) {
            assertEquals(date, "2026-11-03", EventInitialDate.resolve(date, "2026-11-03"))
        }
    }
    @Test fun keepsLeapDayAndYearBoundary() {
        for (date in listOf("2028-02-29", "2026-12-31", "2027-01-01")) {
            assertEquals(date, EventInitialDate.resolve(date, "2026-11-03"))
        }
    }
}
