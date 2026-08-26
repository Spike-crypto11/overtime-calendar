package com.dohyun.overtimecalendar

import android.content.Context
import org.json.JSONObject

/** 로컬 캐시(SharedPreferences + JSON) 및 설정 저장소 */
object Prefs {
    private const val PREF = "otcal"
    private const val KEY_ENTRIES = "entries"     // {"yyyy-MM-dd":{"o":3,"s":0,"m":""}}
    private const val KEY_URL = "web_app_url"
    private const val KEY_PENDING = "pending"     // 서버 전송 실패분: ["yyyy-MM-dd", ...]
    private const val KEY_COLOR_OT = "color_overtime"
    private const val KEY_COLOR_SP = "color_special"

    // 기본 색상 (팔레트에도 포함)
    const val DEFAULT_OT = 0xFF1A6E1A.toInt()  // 초록
    const val DEFAULT_SP = 0xFFC56A00.toInt()  // 주황

    /** 팔레트: 선택 가능한 색 10개 */
    val PALETTE = intArrayOf(
        0xFF1A6E1A.toInt(), // 초록
        0xFFC56A00.toInt(), // 주황
        0xFFC0392B.toInt(), // 빨강
        0xFF2F5496.toInt(), // 파랑
        0xFF6A1B9A.toInt(), // 보라
        0xFF00838F.toInt(), // 청록
        0xFFD81B60.toInt(), // 분홍
        0xFF5D4037.toInt(), // 갈색
        0xFF455A64.toInt(), // 청회색
        0xFF000000.toInt()  // 검정
    )

    private fun sp(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    // ----- 서버 URL -----
    fun getUrl(ctx: Context): String = sp(ctx).getString(KEY_URL, "") ?: ""
    fun setUrl(ctx: Context, url: String) {
        sp(ctx).edit().putString(KEY_URL, url.trim()).apply()
    }

    // ----- 색상 -----
    fun getOvertimeColor(ctx: Context): Int = sp(ctx).getInt(KEY_COLOR_OT, DEFAULT_OT)
    fun getSpecialColor(ctx: Context): Int = sp(ctx).getInt(KEY_COLOR_SP, DEFAULT_SP)
    fun setOvertimeColor(ctx: Context, color: Int) {
        sp(ctx).edit().putInt(KEY_COLOR_OT, color).apply()
    }
    fun setSpecialColor(ctx: Context, color: Int) {
        sp(ctx).edit().putInt(KEY_COLOR_SP, color).apply()
    }

    // ----- 전체 기록 -----
    fun getAll(ctx: Context): MutableMap<String, DayEntry> {
        val raw = sp(ctx).getString(KEY_ENTRIES, "{}") ?: "{}"
        val map = HashMap<String, DayEntry>()
        try {
            val obj = JSONObject(raw)
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val o = obj.getJSONObject(k)
                map[k] = DayEntry(
                    o.optDouble("o", 0.0),
                    o.optDouble("s", 0.0),
                    o.optString("m", "")
                )
            }
        } catch (_: Exception) {
        }
        return map
    }

    private fun saveAll(ctx: Context, map: Map<String, DayEntry>) {
        val obj = JSONObject()
        for ((k, v) in map) {
            if (v.isEmpty()) continue
            val e = JSONObject()
            e.put("o", v.overtime)
            e.put("s", v.special)
            e.put("m", v.memo)
            obj.put(k, e)
        }
        sp(ctx).edit().putString(KEY_ENTRIES, obj.toString()).apply()
    }

    fun get(ctx: Context, date: String): DayEntry? = getAll(ctx)[date]

    fun set(ctx: Context, date: String, entry: DayEntry) {
        val map = getAll(ctx)
        if (entry.isEmpty()) map.remove(date) else map[date] = entry
        saveAll(ctx, map)
    }

    /** 서버에서 받은 한 달치로 로컬의 해당 월을 교체(단, 전송 대기중인 날짜는 보존) */
    fun replaceMonth(ctx: Context, monthPrefix: String, serverEntries: Map<String, DayEntry>) {
        val map = getAll(ctx)
        val pending = getPending(ctx)
        // 해당 월의 기존 항목 제거 (전송 대기분은 남김)
        val toRemove = map.keys.filter { it.startsWith(monthPrefix) && !pending.contains(it) }
        for (k in toRemove) map.remove(k)
        // 서버 값 반영 (전송 대기분은 로컬 우선)
        for ((k, v) in serverEntries) {
            if (pending.contains(k)) continue
            if (!v.isEmpty()) map[k] = v
        }
        saveAll(ctx, map)
    }

    // ----- 전송 대기 목록 -----
    fun getPending(ctx: Context): MutableSet<String> {
        val raw = sp(ctx).getStringSet(KEY_PENDING, emptySet()) ?: emptySet()
        return HashSet(raw)
    }
    fun addPending(ctx: Context, date: String) {
        val s = getPending(ctx); s.add(date)
        sp(ctx).edit().putStringSet(KEY_PENDING, s).apply()
    }
    fun removePending(ctx: Context, date: String) {
        val s = getPending(ctx); s.remove(date)
        sp(ctx).edit().putStringSet(KEY_PENDING, s).apply()
    }
}
