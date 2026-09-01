package com.dohyun.overtimecalendar

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * 로컬 저장소 + 설정.
 * - 카테고리 목록: KEY_CATS  [{id,name,emoji,color,hasNumber}, ...]
 * - 날짜별 기록:   KEY_RECORDS  {"yyyy-MM-dd":[{c:categoryId, v:value}, ...]}
 */
object Prefs {
    private const val PREF = "otcal"
    private const val KEY_RECORDS = "records"
    private const val KEY_CATS = "categories"
    private const val KEY_URL = "web_app_url"
    private const val KEY_PENDING = "pending"
    private const val KEY_HOLIDAYS = "holidays"  // {"yyyy-MM-dd":[{kind,name},...]}

    // 특일 종류별 색
    const val COLOR_HOLIDAY = 0xFFC0392B.toInt() // 공휴일: 빨강
    const val COLOR_TERM = 0xFF00838F.toInt()    // 24절기: 청록
    const val COLOR_ANNIV = 0xFF888888.toInt()   // 기념일: 회색

    // 팔레트: 항목 색 선택용 10색
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
        0xFF111111.toInt()  // 검정
    )

    private fun sp(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    // ---------- 서버 URL ----------
    fun getUrl(ctx: Context): String = sp(ctx).getString(KEY_URL, "") ?: ""
    fun setUrl(ctx: Context, url: String) { sp(ctx).edit().putString(KEY_URL, url.trim()).apply() }

    // ---------- 카테고리 ----------
    /** 기본 카테고리(최초 실행 시): 잔업 🕒 초록, 특근 📋 주황 */
    private fun defaultCategories(): List<Category> = listOf(
        Category("overtime", "잔업", "🕒", 0xFF1A6E1A.toInt(), true, 3.0, false),
        Category("special", "특근", "📋", 0xFF6A1B9A.toInt(), true, 8.0, false)
    )

    fun getCategories(ctx: Context): MutableList<Category> {
        val raw = sp(ctx).getString(KEY_CATS, null)
        if (raw == null) {
            val def = defaultCategories()
            saveCategories(ctx, def)
            return def.toMutableList()
        }
        val list = ArrayList<Category>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    Category(
                        o.getString("id"),
                        o.getString("name"),
                        o.optString("emoji", ""),
                        o.getInt("color"),
                        o.optBoolean("hasNumber", true),
                        o.optDouble("defaultValue", 0.0),
                        o.optBoolean("iconOnly", false)
                    )
                )
            }
        } catch (_: Exception) {
        }
        if (list.isEmpty()) {
            val def = defaultCategories()
            saveCategories(ctx, def)
            return def.toMutableList()
        }
        return list
    }

    fun saveCategories(ctx: Context, cats: List<Category>) {
        val arr = JSONArray()
        for (c in cats) {
            val o = JSONObject()
            o.put("id", c.id)
            o.put("name", c.name)
            o.put("emoji", c.emoji)
            o.put("color", c.color)
            o.put("hasNumber", c.hasNumber)
            o.put("defaultValue", c.defaultValue)
            o.put("iconOnly", c.iconOnly)
            arr.put(o)
        }
        sp(ctx).edit().putString(KEY_CATS, arr.toString()).apply()
    }

    fun getCategory(ctx: Context, id: String): Category? =
        getCategories(ctx).firstOrNull { it.id == id }

    fun addCategory(ctx: Context, cat: Category) {
        val list = getCategories(ctx)
        list.add(cat)
        saveCategories(ctx, list)
    }

    fun updateCategory(ctx: Context, cat: Category) {
        val list = getCategories(ctx)
        val idx = list.indexOfFirst { it.id == cat.id }
        if (idx >= 0) { list[idx] = cat; saveCategories(ctx, list) }
    }

    fun deleteCategory(ctx: Context, id: String) {
        val list = getCategories(ctx)
        list.removeAll { it.id == id }
        saveCategories(ctx, list)
        // 해당 카테고리의 기록도 정리
        val all = getAllRecords(ctx)
        var changed = false
        val it = all.entries.iterator()
        while (it.hasNext()) {
            val e = it.next()
            val filtered = e.value.filter { r -> r.categoryId != id }
            if (filtered.size != e.value.size) {
                changed = true
                if (filtered.isEmpty()) it.remove() else e.setValue(filtered.toMutableList())
            }
        }
        if (changed) saveAllRecords(ctx, all)
    }

    // ---------- 날짜별 기록 ----------
    fun getAllRecords(ctx: Context): MutableMap<String, MutableList<DayRecord>> {
        val raw = sp(ctx).getString(KEY_RECORDS, "{}") ?: "{}"
        val map = HashMap<String, MutableList<DayRecord>>()
        try {
            val obj = JSONObject(raw)
            val keys = obj.keys()
            while (keys.hasNext()) {
                val date = keys.next()
                val arr = obj.getJSONArray(date)
                val recs = ArrayList<DayRecord>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    recs.add(DayRecord(o.getString("c"), o.optDouble("v", 0.0)))
                }
                if (recs.isNotEmpty()) map[date] = recs
            }
        } catch (_: Exception) {
        }
        return map
    }

    private fun saveAllRecords(ctx: Context, map: Map<String, List<DayRecord>>) {
        val obj = JSONObject()
        for ((date, recs) in map) {
            if (recs.isEmpty()) continue
            val arr = JSONArray()
            for (r in recs) {
                val o = JSONObject()
                o.put("c", r.categoryId)
                o.put("v", r.value)
                arr.put(o)
            }
            obj.put(date, arr)
        }
        sp(ctx).edit().putString(KEY_RECORDS, obj.toString()).apply()
    }

    fun getRecords(ctx: Context, date: String): List<DayRecord> =
        getAllRecords(ctx)[date] ?: emptyList()

    /** 특정 날짜의 기록 전체를 교체 (빈 리스트면 그날 삭제) */
    fun setRecords(ctx: Context, date: String, recs: List<DayRecord>) {
        val map = getAllRecords(ctx)
        if (recs.isEmpty()) map.remove(date) else map[date] = recs.toMutableList()
        saveAllRecords(ctx, map)
    }

    /** 서버에서 받은 한 달치로 로컬의 해당 월 교체 (전송 대기분 보존) */
    fun replaceMonth(ctx: Context, monthPrefix: String, serverRecords: Map<String, List<DayRecord>>) {
        val map = getAllRecords(ctx)
        val pending = getPending(ctx)
        val toRemove = map.keys.filter { it.startsWith(monthPrefix) && !pending.contains(it) }
        for (k in toRemove) map.remove(k)
        for ((date, recs) in serverRecords) {
            if (pending.contains(date)) continue
            if (recs.isNotEmpty()) map[date] = recs.toMutableList()
        }
        saveAllRecords(ctx, map)
    }

    // ---------- 특일 (공휴일/절기/기념일) ----------
    fun getAllHolidays(ctx: Context): Map<String, List<Holiday>> {
        val raw = sp(ctx).getString(KEY_HOLIDAYS, "{}") ?: "{}"
        val map = HashMap<String, List<Holiday>>()
        try {
            val obj = JSONObject(raw)
            val keys = obj.keys()
            while (keys.hasNext()) {
                val date = keys.next()
                val arr = obj.getJSONArray(date)
                val list = ArrayList<Holiday>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    list.add(Holiday(date, o.optString("kind", "anniv"), o.optString("name", "")))
                }
                if (list.isNotEmpty()) map[date] = list
            }
        } catch (_: Exception) {
        }
        return map
    }

    fun saveHolidays(ctx: Context, holidays: List<Holiday>) {
        val byDate = HashMap<String, MutableList<Holiday>>()
        for (h in holidays) byDate.getOrPut(h.date) { ArrayList() }.add(h)
        val obj = JSONObject()
        for ((date, list) in byDate) {
            val arr = JSONArray()
            for (h in list) {
                val o = JSONObject()
                o.put("kind", h.kind)
                o.put("name", h.name)
                arr.put(o)
            }
            obj.put(date, arr)
        }
        sp(ctx).edit().putString(KEY_HOLIDAYS, obj.toString()).apply()
    }

    fun colorForKind(kind: String): Int = when (kind) {
        "holiday" -> COLOR_HOLIDAY
        "term" -> COLOR_TERM
        else -> COLOR_ANNIV
    }

    // ---------- 전송 대기 ----------
        HashSet(sp(ctx).getStringSet(KEY_PENDING, emptySet()) ?: emptySet())
    fun addPending(ctx: Context, date: String) {
        val s = getPending(ctx); s.add(date)
        sp(ctx).edit().putStringSet(KEY_PENDING, s).apply()
    }
    fun removePending(ctx: Context, date: String) {
        val s = getPending(ctx); s.remove(date)
        sp(ctx).edit().putStringSet(KEY_PENDING, s).apply()
    }
}
