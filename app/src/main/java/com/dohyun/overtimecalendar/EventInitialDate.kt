package com.dohyun.overtimecalendar

import java.text.SimpleDateFormat
import java.util.Locale

/** A calendar date stays a date, without UTC or timezone conversion. */
object EventInitialDate {
    fun resolve(selected: String?, today: String): String {
        if (selected == null) return today
        return try {
            val format = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
            format.isLenient = false
            val parsed = format.parse(selected)
            if (parsed != null && format.format(parsed) == selected) selected else today
        } catch (_: Exception) {
            today
        }
    }
}
