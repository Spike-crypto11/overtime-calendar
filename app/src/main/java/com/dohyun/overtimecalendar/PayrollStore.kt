package com.dohyun.overtimecalendar

import android.content.Context
import org.json.JSONObject
import org.json.JSONArray
import java.math.BigDecimal

/** Separate preferences: existing otcal data and settings are never migrated or cleared. */
object PayrollStore {
    private fun sp(ctx: Context) = ctx.applicationContext.getSharedPreferences("payroll_v1", Context.MODE_PRIVATE)
    fun enabled(ctx: Context): Boolean = sp(ctx).getBoolean("enabled", false)
    fun setEnabled(ctx: Context, enabled: Boolean) { sp(ctx).edit().putBoolean("enabled", enabled).commit() }
    @Synchronized fun mark(ctx: Context, month: String) {
        if (!month.matches(Regex("\\d{4}-(0[1-9]|1[0-2])"))) return
        val set = pending(ctx).toMutableSet(); set.add(month)
        sp(ctx).edit().putStringSet("pending", set).commit()
        sp(ctx).edit().putLong("revision_$month", revision(ctx, month) + 1).commit()
    }
    @Synchronized fun pending(ctx: Context): Set<String> = HashSet(sp(ctx).getStringSet("pending", emptySet()) ?: emptySet())
    @Synchronized fun revision(ctx: Context, month: String): Long = sp(ctx).getLong("revision_$month", 0)
    @Synchronized fun acknowledge(ctx: Context, month: String, revision: Long) {
        if (revision(ctx, month) != revision) return
        val set = pending(ctx).toMutableSet(); set.remove(month)
        sp(ctx).edit().putStringSet("pending", set).commit()
    }
    fun status(ctx: Context): String = sp(ctx).getString("status", "월별 집계 전송 전")!!
    fun status(ctx: Context, value: String) { sp(ctx).edit().putString("status", value).apply() }
    fun rules(ctx: Context, month: String): PayrollRules = decode(JSONObject(sp(ctx).getString("rules_$month", "{}")!!))
    fun saveRules(ctx: Context, month: String, rules: PayrollRules) {
        sp(ctx).edit().putString("rules_$month", encode(rules).toString()).commit()
        mark(ctx, month)
    }
    private fun decimal(o: JSONObject, key: String): BigDecimal? = if (!o.has(key) || o.isNull(key)) null
        else o.getString(key).toBigDecimal()
    private fun amounts(o: JSONObject): Map<String, BigDecimal?> = o.keys().asSequence().associateWith { decimal(o, it) }
    fun encode(r: PayrollRules): JSONObject = JSONObject().apply {
        put("roles", JSONObject(r.roles.mapValues { it.value.name }))
        put("hoursPerDay", r.hoursPerDay?.toPlainString() ?: JSONObject.NULL)
        put("hoursPerHalfDay", r.hoursPerHalfDay?.toPlainString() ?: JSONObject.NULL)
        put("companyDaysUseRecordedAttendance", r.companyDaysUseRecordedAttendance)
        put("weeklyMarkersConfirmed", r.weeklyMarkersConfirmed)
        put("allowancePerWeek", r.allowancePerWeek?.toPlainString() ?: JSONObject.NULL)
        put("evidence", r.evidence)
        put("earnings", JSONObject(r.earnings.mapValues { it.value?.toPlainString() ?: JSONObject.NULL }))
        put("deductions", JSONObject(r.deductions.mapValues { it.value?.toPlainString() ?: JSONObject.NULL }))
    }
    fun decode(o: JSONObject): PayrollRules {
        val roles = o.optJSONObject("roles")
        return PayrollRules(
            roles = roles?.keys()?.asSequence()?.associateWith { PayrollRole.valueOf(roles.getString(it)) }
                ?: PayrollRules().roles,
            hoursPerDay = decimal(o, "hoursPerDay"), hoursPerHalfDay = decimal(o, "hoursPerHalfDay"),
            companyDaysUseRecordedAttendance = o.optBoolean("companyDaysUseRecordedAttendance"),
            weeklyMarkersConfirmed = o.optBoolean("weeklyMarkersConfirmed"),
            allowancePerWeek = decimal(o, "allowancePerWeek"), evidence = o.optString("evidence", ""),
            earnings = amounts(o.optJSONObject("earnings") ?: JSONObject()),
            deductions = amounts(o.optJSONObject("deductions") ?: JSONObject()))
    }
    fun summary(s: PayrollSummary): JSONObject = JSONObject().apply {
        put("month", s.month); put("recordedDays", s.recordedDays); put("halfDays", s.halfDays)
        put("companyDays", s.companyDays ?: JSONObject.NULL)
        put("basicHours", s.basicHours?.toPlainString() ?: JSONObject.NULL)
        put("weeklyCount", s.weeklyCount ?: JSONObject.NULL)
        put("weeklyAllowance", s.weeklyAllowance?.toPlainString() ?: JSONObject.NULL)
        put("overtimeHours", s.overtimeHours.toPlainString()); put("specialHours", s.specialHours.toPlainString())
        put("gross", s.gross?.toPlainString() ?: JSONObject.NULL); put("net", s.net?.toPlainString() ?: JSONObject.NULL)
        put("deductions", JSONObject(s.deductions.mapValues { it.value?.toPlainString() ?: JSONObject.NULL }))
        put("warnings", JSONArray(s.warnings))
    }
}
