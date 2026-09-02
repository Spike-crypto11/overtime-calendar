package com.dohyun.overtimecalendar

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors

/** Apps Script 웹앱과 GET 방식 통신 (다중 항목 지원) */
object SyncManager {

    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    private fun httpGet(urlStr: String): String {
        val url = URL(urlStr)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 15000
        conn.readTimeout = 15000
        conn.instanceFollowRedirects = true
        try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val reader = BufferedReader(InputStreamReader(stream, "UTF-8"))
            val sb = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) sb.append(line)
            reader.close()
            return sb.toString()
        } finally {
            conn.disconnect()
        }
    }

    private fun recordsToJson(records: List<DayRecord>): String {
        val arr = JSONArray()
        for (r in records) {
            val o = JSONObject()
            o.put("c", r.categoryId)
            o.put("v", r.value)
            arr.put(o)
        }
        return arr.toString()
    }

    /** 한 날짜 기록 전체 저장 */
    fun push(ctx: Context, date: String, records: List<DayRecord>, cb: ((Boolean) -> Unit)? = null) {
        val base = Prefs.getUrl(ctx)
        if (base.isEmpty()) {
            Prefs.addPending(ctx, date)
            cb?.invoke(false)
            return
        }
        executor.execute {
            var ok = false
            try {
                val u = base + "?action=save&date=" + enc(date) +
                        "&records=" + enc(recordsToJson(records))
                val res = httpGet(u)
                ok = JSONObject(res).optBoolean("ok", false)
            } catch (_: Exception) {
                ok = false
            }
            if (ok) Prefs.removePending(ctx, date) else Prefs.addPending(ctx, date)
            main.post { cb?.invoke(ok) }
        }
    }

    /** 한 달치 조회 후 로컬 반영 */
    fun pullMonth(ctx: Context, monthPrefix: String, cb: ((Boolean) -> Unit)? = null) {
        val base = Prefs.getUrl(ctx)
        if (base.isEmpty()) { cb?.invoke(false); return }
        executor.execute {
            var ok = false
            try {
                val u = base + "?action=list&month=" + enc(monthPrefix)
                val res = httpGet(u)
                val obj = JSONObject(res)
                if (obj.optBoolean("ok", false)) {
                    val arr = obj.getJSONArray("entries")
                    val map = HashMap<String, List<DayRecord>>()
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        val date = o.getString("date")
                        val recArr = o.getJSONArray("records")
                        val recs = ArrayList<DayRecord>()
                        for (j in 0 until recArr.length()) {
                            val ro = recArr.getJSONObject(j)
                            recs.add(DayRecord(ro.getString("c"), ro.optDouble("v", 0.0)))
                        }
                        if (recs.isNotEmpty()) map[date] = recs
                    }
                    Prefs.replaceMonth(ctx, monthPrefix, map)
                    ok = true
                }
            } catch (_: Exception) {
                ok = false
            }
            main.post { cb?.invoke(ok) }
        }
    }

    /** 특일(공휴일/절기/기념일) 받아와 로컬 저장. 연 1회 정도면 충분 */
    fun pullHolidays(ctx: Context, cb: ((Boolean) -> Unit)? = null) {
        val base = Prefs.getUrl(ctx)
        if (base.isEmpty()) { cb?.invoke(false); return }
        executor.execute {
            var ok = false
            try {
                val all = ArrayList<Holiday>()
                // 2026~2030 한 번에 받기 (year 파라미터 없이 전체) — 서버는 year 없으면 전체 반환
                val u = base + "?action=holidays"
                val res = httpGet(u)
                val obj = JSONObject(res)
                if (obj.optBoolean("ok", false)) {
                    val arr = obj.getJSONArray("holidays")
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        all.add(Holiday(o.getString("date"), o.optString("kind", "anniv"), o.optString("name", "")))
                    }
                    Prefs.saveHolidays(ctx, all)
                    ok = true
                }
            } catch (_: Exception) {
                ok = false
            }
            main.post { cb?.invoke(ok) }
        }
    }

    /** 일정 목록을 서버에서 받아 로컬 저장 */
    fun pullEvents(ctx: Context, cb: ((Boolean) -> Unit)? = null) {
        val base = Prefs.getUrl(ctx)
        if (base.isEmpty()) { cb?.invoke(false); return }
        executor.execute {
            var ok = false
            try {
                val res = httpGet(base + "?action=events")
                val obj = JSONObject(res)
                if (obj.optBoolean("ok", false)) {
                    val arr = obj.getJSONArray("events")
                    val list = ArrayList<Event>()
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        val colorStr = o.optString("color", "2F5496")
                        val color = try { (0xFF000000.toInt()) or colorStr.toLong(16).toInt() } catch (_: Exception) { 0xFF2F5496.toInt() }
                        list.add(
                            Event(
                                o.getString("id"),
                                o.optString("title", ""),
                                o.optString("start", ""),
                                o.optString("end", o.optString("start", "")),
                                color,
                                o.optBoolean("yearly", false)
                            )
                        )
                    }
                    Prefs.saveEvents(ctx, list)
                    ok = true
                }
            } catch (_: Exception) {
                ok = false
            }
            main.post { cb?.invoke(ok) }
        }
    }

    /** 일정 하나 저장 (서버) */
    fun pushEvent(ctx: Context, e: Event, cb: ((Boolean) -> Unit)? = null) {
        val base = Prefs.getUrl(ctx)
        if (base.isEmpty()) { cb?.invoke(false); return }
        executor.execute {
            var ok = false
            try {
                val colorHex = String.format("%06X", e.color and 0xFFFFFF)
                val u = base + "?action=saveEvent" +
                        "&id=" + enc(e.id) +
                        "&title=" + enc(e.title) +
                        "&start=" + enc(e.start) +
                        "&end=" + enc(e.end) +
                        "&color=" + enc(colorHex) +
                        "&yearly=" + (if (e.yearly) "true" else "false")
                val res = httpGet(u)
                ok = JSONObject(res).optBoolean("ok", false)
            } catch (_: Exception) {
                ok = false
            }
            main.post { cb?.invoke(ok) }
        }
    }

    /** 일정 삭제 (서버) */
    fun deleteEvent(ctx: Context, id: String, cb: ((Boolean) -> Unit)? = null) {
        val base = Prefs.getUrl(ctx)
        if (base.isEmpty()) { cb?.invoke(false); return }
        executor.execute {
            var ok = false
            try {
                val res = httpGet(base + "?action=deleteEvent&id=" + enc(id))
                ok = JSONObject(res).optBoolean("ok", false)
            } catch (_: Exception) {
                ok = false
            }
            main.post { cb?.invoke(ok) }
        }
    }

    fun flushPending(ctx: Context) {
        val pending = Prefs.getPending(ctx)
        for (date in pending) {
            push(ctx, date, Prefs.getRecords(ctx, date), null)
        }
    }

    fun test(ctx: Context, url: String, cb: (Boolean, String) -> Unit) {
        executor.execute {
            var ok = false
            var msg: String
            try {
                val res = httpGet(url.trim() + "?action=list&month=__test__")
                val obj = JSONObject(res)
                ok = obj.optBoolean("ok", false)
                msg = if (ok) "연결 성공! 서버가 정상 응답했습니다." else "응답 형식이 예상과 다릅니다: $res"
            } catch (ex: Exception) {
                msg = "연결 실패: ${ex.message}"
            }
            main.post { cb(ok, msg) }
        }
    }
}
