package com.dohyun.overtimecalendar

import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.math.RoundingMode
import org.json.JSONObject

class PayrollEngineTest {
    @Test fun sourceComparisonIgnoresOrderButNotMissingOrDuplicateRecords() {
        val a = mapOf("2026-10-01" to listOf(DayRecord("work"),DayRecord("overtime",3.0)))
        assertTrue(PayrollEngine.sameRecords(a,a.mapValues { it.value.reversed() }))
        assertFalse(PayrollEngine.sameRecords(a,emptyMap()))
        assertFalse(PayrollEngine.sameRecords(a,a.mapValues { it.value + DayRecord("work") }))
    }
    private fun n(v: String) = BigDecimal(v)
    private val records = mapOf(
        "2026-10-01" to listOf(DayRecord("work"),DayRecord("overtime",3.0)),
        "2026-10-02" to listOf(DayRecord("work"),DayRecord("half"),DayRecord("overtime",1.5)),
        "2026-10-04" to listOf(DayRecord("week")),
        "2026-10-10" to listOf(DayRecord("special",8.0)),
        "2026-09-30" to listOf(DayRecord("work")))
    private val roles = PayrollRules().roles + mapOf("work" to PayrollRole.ATTENDANCE,
        "half" to PayrollRole.HALF_DAY,"week" to PayrollRole.WEEKLY_PAID)
    private val rules = PayrollRules(roles,n("8"),n("4"),true,true,n("100"),"test-fixture; not company policy",
        PayrollEngine.earningKeys.associateWith { n("10") },PayrollEngine.deductionKeys.associateWith { n("1") })

    @Test fun unknownRulesDoNotInventCompanyPayOrTaxes() {
        val s = PayrollEngine.summarize("2026-10",records,PayrollRules())
        assertNull(s.companyDays); assertNull(s.basicHours); assertNull(s.weeklyCount)
        assertNull(s.weeklyAllowance); assertNull(s.gross); assertNull(s.net)
        assertTrue(s.deductions.values.all { it == null })
        assertEquals(0,n("4.5").compareTo(s.overtimeHours)); assertEquals(0,n("8").compareTo(s.specialHours))
    }
    @Test fun halfDayIsOneRecordedDayAndFourHoursWithoutDoubleCount() {
        val s = PayrollEngine.summarize("2026-10",records,rules)
        assertEquals(2,s.recordedDays); assertEquals(1,s.halfDays); assertEquals(2,s.companyDays)
        assertEquals(0,n("12").compareTo(s.basicHours)); assertEquals(1,s.weeklyCount)
        assertEquals(0,n("150").compareTo(s.gross)); assertEquals(0,n("142").compareTo(s.net))
    }
    @Test fun overtimeAndWeekendWorkDoNotImplicitlyCountAsAttendance() {
        val s = PayrollEngine.summarize("2026-10",mapOf("2026-10-01" to listOf(DayRecord("overtime",3.0))),rules)
        assertEquals(0,s.recordedDays); assertEquals(0,s.companyDays)
    }
    @Test fun missingSingleDeductionBlocksNet() {
        val s = PayrollEngine.summarize("2026-10",records,rules.copy(deductions=rules.deductions-"healthInsurance"))
        assertNotNull(s.gross); assertNull(s.net); assertNull(s.deductions["healthInsurance"])
    }
    @Test fun missingEvidenceBlocksCompanyAmountsEvenWithCheckboxes() {
        val s = PayrollEngine.summarize("2026-10",records,rules.copy(evidence=""))
        assertEquals(2,s.recordedDays); assertNull(s.companyDays); assertNull(s.basicHours); assertNull(s.net)
    }
    @Test fun explicitZeroIsKnownAndExtraDeductionsRemainSeparate() {
        val s = PayrollEngine.summarize("2026-10",emptyMap(),rules.copy(
            earnings=PayrollEngine.earningKeys.associateWith { BigDecimal.ZERO },
            deductions=PayrollEngine.deductionKeys.associateWith { BigDecimal.ZERO } + ("unionFee" to n("10"))))
        assertEquals(0,n("-10").compareTo(s.net)); assertEquals(n("10"),s.deductions["unionFee"])
    }
    @Test(expected=IllegalArgumentException::class) fun invalidHoursCannotPoisonTotals() {
        PayrollEngine.summarize("2026-10",mapOf("2026-10-01" to listOf(DayRecord("overtime",Double.NaN))),rules)
    }
    @Test(expected=IllegalArgumentException::class) fun invalidDateRejected() {
        PayrollEngine.summarize("2026-02",mapOf("2026-02-30" to listOf(DayRecord("work"))),rules)
    }
    @Test fun jsonRoundTripPreservesUnknownAndExtraComponents() {
        val original = rules.copy(deductions=rules.deductions + ("unionFee" to null))
        assertEquals(original,PayrollStore.decode(JSONObject(PayrollStore.encode(original).toString())))
    }
    @Test fun rateRequiresBasisEvidenceAndExplicitRoundingPolicy() {
        assertNull(PayrollDeduction.Rate(null,n("0.01"),n("10"),RoundingMode.DOWN,"fixture").estimate())
        assertEquals(n("1230"),PayrollDeduction.Rate(n("123456"),n("0.01"),n("10"),RoundingMode.DOWN,"fixture").estimate())
        assertNull(PayrollDeduction.Fixed(n("100"),"").estimate())
    }
    @Test fun statementComparisonNeverTurnsUnknownIntoZero() {
        assertNull(PayrollEngine.compare(null,n("100")))
        assertEquals(n("-10"),PayrollEngine.compare(n("110"),n("100")))
    }
}
