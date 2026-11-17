package com.dohyun.overtimecalendar

import java.math.BigDecimal
import java.math.RoundingMode

/** Future rate/table adapters resolve a separate basis for EACH deduction and supply evidence. */
sealed class PayrollDeduction {
    abstract val evidence: String
    abstract fun estimate(): BigDecimal?
    data class Fixed(val amount: BigDecimal?, override val evidence: String) : PayrollDeduction() {
        override fun estimate(): BigDecimal? {
            require(amount == null || amount.signum() >= 0)
            return if (evidence.isBlank()) null else amount
        }
    }
    data class Rate(val basis: BigDecimal?, val rate: BigDecimal?, val roundingUnit: BigDecimal,
                    val roundingMode: RoundingMode, override val evidence: String) : PayrollDeduction() {
        override fun estimate(): BigDecimal? {
            require(roundingUnit.signum() > 0)
            require(basis == null || basis.signum() >= 0)
            require(rate == null || rate.signum() >= 0)
            if (evidence.isBlank() || basis == null || rate == null) return null
            return (basis * rate).divide(roundingUnit, 0, roundingMode) * roundingUnit
        }
    }
}
