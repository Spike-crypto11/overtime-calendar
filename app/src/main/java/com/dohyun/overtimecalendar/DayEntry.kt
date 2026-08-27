package com.dohyun.overtimecalendar

/**
 * 항목 종류(카테고리) 정의.
 * 예) 잔업 🕒 초록 숫자입력O, 화장실 🚽 파랑 숫자입력X
 */
data class Category(
    val id: String,        // 고유 id (예: "overtime", "custom_1712...")
    val name: String,      // 표시 이름 (예: "잔업")
    val emoji: String,     // 이모지 (예: "🕒")
    val color: Int,        // 막대 색 (ARGB)
    val hasNumber: Boolean // true면 시간 숫자 입력, false면 표시만
)

/**
 * 하루의 기록 하나. 어떤 항목(categoryId)이 그날 있었는지 + (숫자항목이면) 값.
 */
data class DayRecord(
    val categoryId: String,
    val value: Double = 0.0   // hasNumber=false면 0
)

/** 달력 한 칸 (빈 칸이면 day=0) */
data class CalendarCell(
    val day: Int,           // 0 = 이번 달에 속하지 않는 빈 칸
    val date: String,       // yyyy-MM-dd (빈 칸이면 "")
    val weekdayIndex: Int,  // 0=일 .. 6=토
    val isToday: Boolean,
    val records: List<DayRecord>
)
