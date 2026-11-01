package com.dohyun.overtimecalendar

/** Include historical IDs without adding, renaming or deleting original categories/records. */
object PayrollCategories {
    fun labels(month: String, names: Map<String, String>, records: Map<String, List<DayRecord>>,
               savedRoles: Map<String, PayrollRole>): Map<String, String> {
        val result = names.toMutableMap()
        val dates = linkedMapOf<String, MutableSet<String>>()
        records.toSortedMap().forEach { (date, entries) ->
            if (date.startsWith("$month-")) entries.forEach { record ->
                dates.getOrPut(record.categoryId) { linkedSetOf() }.add(date.substring(5))
            }
        }
        dates.forEach { (id, days) ->
            if (id !in result) {
                val preview = days.take(6).joinToString(", ")
                result[id] = "미등록 항목 · 기록일 $preview${if (days.size > 6) " 외 ${days.size - 6}일" else ""}"
            }
        }
        savedRoles.keys.forEach { id -> if (id !in result) result[id] = "저장된 역할 · 이번 달 기록 없음" }
        return result
    }
}
