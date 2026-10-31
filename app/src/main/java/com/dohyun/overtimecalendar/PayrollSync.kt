package com.dohyun.overtimecalendar

import android.content.Context
import org.json.JSONObject
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors

/** Independent, opt-in protocol. Legacy save/list/events/holidays endpoints are unchanged. */
object PayrollSync {
    private val executor = Executors.newSingleThreadExecutor()
    fun scheduleMonth(ctx: Context, month: String) {
        PayrollStore.mark(ctx, month)
        flush(ctx)
    }
    fun flush(ctx: Context) {
        val app = ctx.applicationContext
        executor.execute {
            if (!PayrollStore.enabled(app)) {
                PayrollStore.status(app, "서버 기능 배포 후 월별 집계 자동 전송을 켜세요 (기존 동기화는 유지)")
                return@execute
            }
            for (month in PayrollStore.pending(app)) sync(app, month)
        }
    }
    private fun query(base: String, action: String, month: String = ""): String = base +
        (if ('?' in base) "&" else "?") + "action=$action&month=" + URLEncoder.encode(month, "UTF-8")

    private fun get(url: String): JSONObject {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15000; conn.readTimeout = 15000
        return try {
            check(conn.responseCode in 200..299) { "HTTP ${conn.responseCode}" }
            JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        } finally { conn.disconnect() }
    }

    private fun post(url: String, obj: JSONObject): JSONObject {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"; conn.doOutput = true
        conn.connectTimeout = 15000; conn.readTimeout = 15000
        // Apps Script ContentService redirects POST results to a read-only GET URL.
        conn.instanceFollowRedirects = false
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        return try {
            conn.outputStream.use { it.write(obj.toString().toByteArray(Charsets.UTF_8)) }
            if (conn.responseCode in listOf(301, 302, 303)) {
                val next = URL(URL(url), conn.getHeaderField("Location"))
                check(next.protocol == "https" && next.host == "script.googleusercontent.com")
                get(next.toString())
            } else {
                check(conn.responseCode in 200..299)
                JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            }
        } finally { conn.disconnect() }
    }

    private fun sync(ctx: Context, month: String) {
        val revision = PayrollStore.revision(ctx, month)
        try {
            // Never publish a server-only total while any local edits for that month are unsent.
            if (Prefs.getPending(ctx).any { it.startsWith("$month-") }) {
                PayrollStore.status(ctx, "$month: 근무기록 전송 대기"); return
            }
            val base = Prefs.getUrl(ctx)
            val endpoint = URL(base)
            check(endpoint.protocol == "https" && endpoint.host == "script.google.com")
            val capability = get(query(base, "payrollCapabilities"))
            if (!capability.optBoolean("ok") || capability.optInt("payrollProtocol") != 1) {
                PayrollStore.status(ctx, "서버 월별 집계 기능 배포 필요 (기존 기록 동기화는 유지)")
                return
            }
            val response = get(query(base, "list", month))
            check(response.getBoolean("ok"))
            val entries = response.getJSONArray("entries")
            val records = linkedMapOf<String, List<DayRecord>>()
            for (i in 0 until entries.length()) {
                val entry = entries.getJSONObject(i); val date = entry.getString("date")
                check(date.startsWith("$month-") && date !in records)
                val values = entry.getJSONArray("records")
                records[date] = (0 until values.length()).map { j ->
                    val r = values.getJSONObject(j); DayRecord(r.getString("c"), r.getDouble("v"))
                }
            }
            val rules = PayrollStore.rules(ctx, month)
            val local = Prefs.getAllRecords(ctx).filterKeys { it.startsWith("$month-") }
            if (!PayrollEngine.sameRecords(local, records)) {
                PayrollStore.status(ctx, "$month: 로컬·서버 기록 차이 확인 필요 · 원본은 모두 보존됨")
                return
            }
            val summary = PayrollEngine.summarize(month, records, rules)
            if (revision != PayrollStore.revision(ctx, month) || Prefs.getPending(ctx).any { it.startsWith("$month-") }) return
            val result = post(base, JSONObject().apply {
                put("action", "payrollSync"); put("payrollProtocol", 1); put("month", month)
                put("source", entries); put("rules", PayrollStore.encode(rules))
                put("categories", JSONArray().apply {
                    Prefs.getCategories(ctx).forEach { put(JSONObject().put("id", it.id).put("name", it.name)) }
                })
                put("summary", PayrollStore.summary(summary))
            })
            check(result.optBoolean("ok") && result.optInt("payrollProtocol") == 1 && result.optString("month") == month) {
                result.optString("error", "월별 집계 응답 불일치")
            }
            PayrollStore.acknowledge(ctx, month, revision)
            PayrollStore.status(ctx, "$month: 월별 집계 전송 완료 (미확정 항목은 빈값)")
        } catch (_: Exception) {
            // No raw response/URL in status: it can contain private records or deployment identifiers.
            PayrollStore.status(ctx, "$month: 월별 집계 전송 보류 · 앱 실행/새로고침 때 재시도")
        }
    }
}
