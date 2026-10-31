package com.dohyun.overtimecalendar

import java.math.BigDecimal

/** No statutory rates or unverified company rules are built in. Null means unknown. */
enum class PayrollRole { IGNORE, ATTENDANCE, HALF_DAY, OVERTIME, SPECIAL, WEEKLY_PAID }

data class PayrollRules(
    val roles: Map<String, PayrollRole> = mapOf("overtime" to PayrollRole.OVERTIME, "special" to PayrollRole.SPECIAL),
    val hoursPerDay: BigDecimal? = null,
    val hoursPerHalfDay: BigDecimal? = null,
    val companyDaysUseRecordedAttendance: Boolean = false,
    val weeklyMarkersConfirmed: Boolean = false,
    val allowancePerWeek: BigDecimal? = null,
    val evidence: String = "",
    val earnings: Map<String, BigDecimal?> = emptyMap(),
    val deductions: Map<String, BigDecimal?> = emptyMap()
)

data class PayrollSummary(
    val month: String,
    val recordedDays: Int,
    val halfDays: Int,
    val companyDays: Int?,
    val basicHours: BigDecimal?,
    val weeklyCount: Int?,
    val weeklyAllowance: BigDecimal?,
    val overtimeHours: BigDecimal,
    val specialHours: BigDecimal,
    val gross: BigDecimal?,
    val deductions: Map<String, BigDecimal?>,
    val net: BigDecimal?,
    val warnings: List<String>
)

object PayrollEngine {
    /** Ordering is irrelevant, but duplicate records and differing values must remain visible. */
    fun sameRecords(a: Map<String, List<DayRecord>>, b: Map<String, List<DayRecord>>): Boolean {
        fun normalized(records: Map<String, List<DayRecord>>) = records.filterValues { it.isNotEmpty() }
            .mapValues { (_, entries) -> entries.sortedWith(compareBy<DayRecord> { it.categoryId }.thenBy { it.value }) }
        return normalized(a) == normalized(b)
    }
    val deductionKeys = listOf("incomeTax", "localIncomeTax", "nationalPension", "healthInsurance",
        "longTermCare", "employmentInsurance", "insuranceAdjustment", "other")
    // Each component must be explicitly supplied (zero is valid). Additional components are supported.
    val earningKeys = listOf("base", "overtime", "special", "position", "other")

    fun summarize(month: String, records: Map<String, List<DayRecord>>, rules: PayrollRules): PayrollSummary {
        require(month.matches(Regex("\\d{4}-(0[1-9]|1[0-2])")))
        val warnings = linkedSetOf<String>()
        var full = 0; var half = 0; var weekly = 0
        var overtime = BigDecimal.ZERO; var special = BigDecimal.ZERO
        for ((date, entries) in records) {
            if (!date.startsWith("$month-")) continue
            require(date.matches(Regex("\\d{4}-\\d{2}-\\d{2}")))
            val parts = date.split('-').map { it.toInt() }
            require(parts[2] in 1..DateUtils.daysInMonth(parts[0], parts[1]))
            val roles = entries.map { rules.roles[it.categoryId] ?: PayrollRole.IGNORE }.toSet()
            // Half day takes precedence if both attendance and half day were checked.
            if (PayrollRole.HALF_DAY in roles) half++ else if (PayrollRole.ATTENDANCE in roles) full++
            if (PayrollRole.WEEKLY_PAID in roles) weekly++
            for (r in entries) {
                require(r.value.isFinite() && r.value >= 0) { "Invalid record value" }
                if (r.categoryId !in rules.roles) warnings.add("미분류 항목: ${r.categoryId}")
                when (rules.roles[r.categoryId]) {
                    PayrollRole.OVERTIME -> overtime += BigDecimal.valueOf(r.value)
                    PayrollRole.SPECIAL -> special += BigDecimal.valueOf(r.value)
                    else -> Unit
                }
            }
        }
        val confirmed = rules.evidence.isNotBlank()
        fun nonNegative(v: BigDecimal?) { require(v == null || v.signum() >= 0) }
        (listOf(rules.hoursPerDay, rules.hoursPerHalfDay, rules.allowancePerWeek) +
            rules.earnings.values + rules.deductions.values).forEach(::nonNegative)
        val basic = if (confirmed && rules.hoursPerDay != null && (half == 0 || rules.hoursPerHalfDay != null))
            rules.hoursPerDay * full.toBigDecimal() + (rules.hoursPerHalfDay ?: BigDecimal.ZERO) * half.toBigDecimal()
        else null
        val days = if (confirmed && rules.companyDaysUseRecordedAttendance) full + half else null
        val weeks = if (confirmed && rules.weeklyMarkersConfirmed) weekly else null
        val weeklyPay = if (weeks != null && rules.allowancePerWeek != null)
            weeks.toBigDecimal() * rules.allowancePerWeek else null
        val earningsComplete = earningKeys.all { rules.earnings[it] != null } && rules.earnings.values.none { it == null }
        val gross = if (confirmed && earningsComplete && weeklyPay != null)
            rules.earnings.values.filterNotNull().fold(weeklyPay, BigDecimal::add) else null
        val deductions = (deductionKeys + rules.deductions.keys).distinct().associateWith { rules.deductions[it] }
        val net = if (gross != null && deductions.values.none { it == null })
            gross - deductions.values.filterNotNull().fold(BigDecimal.ZERO, BigDecimal::add) else null
        if (days == null) warnings.add("회사 근무일수 기준 미확인")
        if (basic == null) warnings.add("기본근로시간 기준 미확인")
        if (weeks == null) warnings.add("주휴 인정 기준 미확인")
        if (weeklyPay == null) warnings.add("주차수당 미확정")
        if (net == null) warnings.add("급여 또는 공제 미확정: 실수령액 계산 보류")
        return PayrollSummary(month, full + half, half, days, basic, weeks, weeklyPay,
            overtime, special, gross, deductions, net, warnings.toList())
    }

    /** Signed difference: actual statement amount minus estimate. Unknown remains unknown. */
    fun compare(expected: BigDecimal?, statement: BigDecimal?): BigDecimal? =
        if (expected == null || statement == null) null else statement - expected
}
