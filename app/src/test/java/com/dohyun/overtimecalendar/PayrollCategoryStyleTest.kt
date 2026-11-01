package com.dohyun.overtimecalendar

import org.junit.Assert.*
import org.junit.Test

class PayrollCategoryStyleTest {
    private fun cat(id: String, name: String) = Category(id, name, "", 1, true, 3.0, false)
    @Test fun preservesExistingIdsAndAddsOnlyMissingNames() {
        val before = listOf(cat("old-half", "오후 반차"), cat("old-toilet", "화장실"), cat("overtime", "잔업"))
        val after = PayrollCategoryStyle.upgraded(before, "old-half")
        assertEquals(before[2], after.first { it.id == "overtime" })
        assertEquals("반차", after.first { it.id == "old-half" }.name)
        assertTrue(after.first { it.id == "old-toilet" }.iconOnly)
        assertEquals("🚽", after.first { it.id == "old-toilet" }.emoji)
        assertEquals(1, after.count { it.name == "반차" })
        assertEquals(1, after.count { it.name == "연차" })
        assertEquals(after, PayrollCategoryStyle.upgraded(after, "old-half"))
    }
    @Test fun reconnectsConfirmedHistoricalHalfDayIdAndDoesNotInventAttendanceForAnnualLeave() {
        val after = PayrollCategoryStyle.upgraded(listOf(cat("attendance", "출근")), "historical-half")
        assertEquals("historical-half", after.first { it.name == "반차" }.id)
        assertEquals(PayrollCategoryStyle.LEAVE_RED, after.first { it.name == "연차" }.color)
        assertEquals(0xFFB08A3E.toInt(), after.first { it.name == "화장실" }.color)
    }
}
