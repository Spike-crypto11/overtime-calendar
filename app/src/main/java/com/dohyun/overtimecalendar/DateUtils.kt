package com.dohyun.overtimecalendar

import java.util.Calendar

object DateUtils {

    /** yyyy-MM-dd 문자열 생성 */
    fun ymd(year: Int, month1: Int, day: Int): String =
        String.format("%04d-%02d-%02d", year, month1, day)

    /** 오늘 날짜 문자열 */
    fun today(): String {
        val c = Calendar.getInstance()
        return ymd(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
    }

    /** month1: 1~12, 해당 월의 일수 */
    fun daysInMonth(year: Int, month1: Int): Int {
        val c = Calendar.getInstance()
        c.set(year, month1 - 1, 1)
        return c.getActualMaximum(Calendar.DAY_OF_MONTH)
    }

    /** 1일의 요일 인덱스 (0=일 .. 6=토) */
    fun firstWeekdayIndex(year: Int, month1: Int): Int {
        val c = Calendar.getInstance()
        c.set(year, month1 - 1, 1)
        return c.get(Calendar.DAY_OF_WEEK) - 1  // DAY_OF_WEEK: 1(일)~7(토)
    }

    /** 특정 날짜의 요일 인덱스 (0=일 .. 6=토) */
    fun weekdayIndexOf(year: Int, month1: Int, day: Int): Int {
        val c = Calendar.getInstance()
        c.set(year, month1 - 1, day)
        return c.get(Calendar.DAY_OF_WEEK) - 1
    }

    private val WD = arrayOf("일", "월", "화", "수", "목", "금", "토")
    fun weekdayLabel(idx: Int): String = WD[((idx % 7) + 7) % 7]

    /** "2026-01-05" → "2026-01-05 (월)" */
    fun withWeekday(date: String): String {
        return try {
            val p = date.split("-")
            val idx = weekdayIndexOf(p[0].toInt(), p[1].toInt(), p[2].toInt())
            "$date (${weekdayLabel(idx)})"
        } catch (_: Exception) {
            date
        }
    }

    /** 이번 달 프리픽스 "yyyy-MM" */
    fun monthPrefix(year: Int, month1: Int): String = String.format("%04d-%02d", year, month1)
}
