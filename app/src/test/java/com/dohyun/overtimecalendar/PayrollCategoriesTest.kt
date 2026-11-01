package com.dohyun.overtimecalendar

import org.junit.Assert.*
import org.junit.Test

class PayrollCategoriesTest {
    @Test fun historicalHalfDayCanBeSelectedWithoutChangingOriginalRecords() {
        val records = mapOf("2026-10-21" to listOf(DayRecord("old-half")),
            "2026-10-22" to listOf(DayRecord("attendance")),
            "2026-09-01" to listOf(DayRecord("other-month")))
        val before = records.toMap()
        val labels = PayrollCategories.labels("2026-10", mapOf("attendance" to "출근"), records, emptyMap())
        assertTrue(labels.getValue("old-half").contains("10-21"))
        assertEquals("출근", labels["attendance"])
        assertFalse(labels.containsKey("other-month"))
        val summary = PayrollEngine.summarize("2026-10", records, PayrollRules(roles = mapOf(
            "attendance" to PayrollRole.ATTENDANCE, "old-half" to PayrollRole.HALF_DAY)))
        assertEquals(2, summary.recordedDays)
        assertEquals(1, summary.halfDays)
        assertEquals(before, records)
    }
    @Test fun savedRoleRemainsAvailableWhenNoRecordOrCategoryExists() {
        val roles = mapOf("old-week" to PayrollRole.WEEKLY_PAID)
        assertTrue(PayrollCategories.labels("2026-10", emptyMap(), emptyMap(), roles).containsKey("old-week"))
    }
}
