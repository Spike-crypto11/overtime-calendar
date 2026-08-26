package com.dohyun.overtimecalendar

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors

/** Apps Script 웹앱과 GET 방식으로 통신 (POST 리디렉션 버그 회피) */
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
        conn.instanceFollowRedirects = true   // Apps Script 302(GET) 자동 추적
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

    /** 한 날짜 저장 (백그라운드). 성공/실패를 콜백으로 전달 */
    fun push(ctx: Context, date: String, e: DayEntry, cb: ((Boolean) -> Unit)? = null) {
        val base = Prefs.getUrl(ctx)
        if (base.isEmpty()) {
            Prefs.addPending(ctx, date)
            cb?.invoke(false)
            return
        }
        executor.execute {
            var ok = false
            try {
                val u = base +
                        "?action=save" +
                        "&date=" + enc(date) +
                        "&overtime=" + enc(e.overtime.toString()) +
                        "&special=" + enc(e.special.toString()) +
                        "&memo=" + enc(e.memo)
                val res = httpGet(u)
                ok = JSONObject(res).optBoolean("ok", false)
            } catch (_: Exception) {
                ok = false
            }
            if (ok) Prefs.removePending(ctx, date) else Prefs.addPending(ctx, date)
            main.post { cb?.invoke(ok) }
        }
    }

    /** 한 달치 조회 후 로컬 반영 (백그라운드) → 완료 콜백(main) */
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
                    val map = HashMap<String, DayEntry>()
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        map[o.getString("date")] = DayEntry(
                            o.optDouble("overtime", 0.0),
                            o.optDouble("special", 0.0),
                            o.optString("memo", "")
                        )
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

    /** 전송 대기중이던 날짜들 재전송 */
    fun flushPending(ctx: Context) {
        val pending = Prefs.getPending(ctx)
        for (date in pending) {
            val e = Prefs.get(ctx, date) ?: DayEntry()
            push(ctx, date, e, null)
        }
    }

    /** 연결 테스트 (설정 화면용) */
    fun test(ctx: Context, url: String, cb: (Boolean, String) -> Unit) {
        executor.execute {
            var ok = false
            var msg: String
            try {
                val res = httpGet(url.trim() + "?action=list&month=__test__")
                val obj = JSONObject(res)
                ok = obj.optBoolean("ok", false)
                msg = if (ok) "연결 성공! 서버가 정상 응답했습니다." else "응답은 왔지만 형식이 예상과 다릅니다: $res"
            } catch (ex: Exception) {
                msg = "연결 실패: ${ex.message}"
            }
            main.post { cb(ok, msg) }
        }
    }
}
