package com.dohyun.overtimecalendar

/** 하루치 기록 */
data class DayEntry(
    val overtime: Double = 0.0,
    val special: Double = 0.0,
    val memo: String = ""
) {
    fun isEmpty(): Boolean = overtime == 0.0 && special == 0.0 && memo.isBlank()
}

/** 달력 한 칸 (빈 칸이면 day=0) */
data class CalendarCell(
    val day: Int,           // 0 = 이번 달에 속하지 않는 빈 칸
    val date: String,       // yyyy-MM-dd (빈 칸이면 "")
    val weekdayIndex: Int,  // 0=일 .. 6=토
    val isToday: Boolean,
    val entry: DayEntry?
)
