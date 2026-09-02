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

    /** 날짜에 하루 더하거나 뺀 yyyy-MM-dd */
    fun addDays(date: String, delta: Int): String {
        return try {
            val p = date.split("-")
            val c = java.util.Calendar.getInstance()
            c.clear()
            c.set(p[0].toInt(), p[1].toInt() - 1, p[2].toInt())
            c.add(java.util.Calendar.DAY_OF_MONTH, delta)
            ymd(c.get(java.util.Calendar.YEAR), c.get(java.util.Calendar.MONTH) + 1, c.get(java.util.Calendar.DAY_OF_MONTH))
        } catch (_: Exception) {
            date
        }
    }

    /**
     * 같은 이름의 공휴일이 여러 날 이어질 때(설날·추석 연휴 등),
     * 이름을 이 날짜에 표시할지 여부. 연속 구간의 "가운데 날"에만 true.
     * nameOf(date) = 그 날짜의 대표 공휴일 이름(없으면 null)
     */
    fun isNameAnchor(date: String, name: String, nameOf: (String) -> String?): Boolean {
        // 앞뒤로 같은 이름이 며칠씩 이어지는지 센다
        var back = 0
        while (nameOf(addDays(date, -(back + 1))) == name) back++
        var fwd = 0
        while (nameOf(addDays(date, fwd + 1)) == name) fwd++
        val total = back + fwd + 1
        if (total <= 1) return true  // 하루짜리면 그냥 표시
        // 연속 구간에서 내 위치(0-based)가 가운데인지
        val myIndex = back
        val center = total / 2  // 3일이면 index 1(가운데), 2일이면 index 1
        return myIndex == center
    }
}
