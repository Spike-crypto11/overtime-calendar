package com.dohyun.overtimecalendar

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.RemoteViews
import android.widget.TextView

/** Additive, once-only category presentation upgrade. Never edits work records. */
object PayrollCategoryStyle {
    val LEAVE_RED = 0xFFC62828.toInt()
    val TOILET_OCHRE = 0xFFB08A3E.toInt()
    fun upgraded(existing: List<Category>, confirmedHalfId: String?): List<Category> {
        val result = existing.map { cat -> when {
            cat.name.contains("반차") || cat.id == confirmedHalfId ->
                cat.copy(name = "반차", emoji = "", color = LEAVE_RED, hasNumber = false, defaultValue = 0.0, iconOnly = false)
            cat.name == "연차" -> cat.copy(emoji = "", color = LEAVE_RED, hasNumber = false, defaultValue = 0.0, iconOnly = false)
            cat.name in listOf("화장실", "변기") ->
                cat.copy(name = "화장실", emoji = "🚽", color = TOILET_OCHRE, hasNumber = false, defaultValue = 0.0, iconOnly = true)
            else -> cat
        } }.toMutableList()
        fun add(id: String, name: String, emoji: String, color: Int, iconOnly: Boolean) {
            if (result.none { it.name == name }) {
                var freeId = id
                while (result.any { it.id == freeId }) freeId += "_new"
                result.add(Category(freeId, name, emoji, color, false, 0.0, iconOnly))
            }
        }
        if (confirmedHalfId != null && result.none { it.id == confirmedHalfId })
            result.add(Category(confirmedHalfId, "반차", "", LEAVE_RED, false, 0.0, false))
        add(confirmedHalfId ?: "half_day", "반차", "", LEAVE_RED, false)
        add("annual_leave", "연차", "", LEAVE_RED, false)
        add("toilet", "화장실", "🚽", TOILET_OCHRE, true)
        return result
    }

    @JvmStatic @Synchronized fun ensure(ctx: Context, existing: List<Category>): List<Category> {
        val prefs = ctx.getSharedPreferences("payroll_v1", Context.MODE_PRIVATE)
        if (prefs.getBoolean("category_style_v18", false)) return existing
        val halfId = ctx.packageManager.getApplicationInfo(ctx.packageName, PackageManager.GET_META_DATA)
            .metaData?.getString("daylog.confirmedHalfDayId")
        val updated = upgraded(existing, halfId)
        // Keep the old category definition as a private recovery snapshot; records stay untouched.
        val raw = ctx.getSharedPreferences("otcal", Context.MODE_PRIVATE).getString("categories", null)
        prefs.edit().putString("category_style_v18_backup", raw).commit()
        Prefs.saveCategories(ctx, updated)
        val halfIds = updated.filter { it.name == "반차" }.map { it.id }.toSet()
        val excludedIds = updated.filter { it.name in listOf("연차", "화장실") }.map { it.id }.toSet()
        val records = Prefs.getAllRecords(ctx)
        records.keys.map { it.take(7) }.distinct().forEach { month ->
            val rules = PayrollStore.rules(ctx, month)
            val roles = rules.roles + halfIds.associateWith { PayrollRole.HALF_DAY } +
                excludedIds.filter { it !in rules.roles }.associateWith { PayrollRole.IGNORE }
            if (roles != rules.roles) PayrollStore.saveRules(ctx, month, rules.copy(roles = roles))
        }
        prefs.edit().putBoolean("category_style_v18", true).commit()
        return updated
    }

    private fun isLeave(text: CharSequence) = text.toString().trim() in listOf("반차", "연차")
    @JvmStatic fun labelColor(text: String, fallback: Int): Int = if (isLeave(text)) LEAVE_RED else fallback
    @JvmStatic fun chipColor(color: Int): Int = if (color == LEAVE_RED) Color.TRANSPARENT else color
    @JvmStatic fun styleText(tv: TextView) {
        if (isLeave(tv.text)) { tv.setBackgroundColor(Color.TRANSPARENT); tv.setTextColor(LEAVE_RED) }
    }
    @JvmStatic fun setWidgetLabel(views: RemoteViews, id: Int, text: CharSequence) {
        if (!isLeave(text)) { views.setTextViewText(id, text); return }
        val styled = SpannableString(text)
        styled.setSpan(ForegroundColorSpan(LEAVE_RED), 0, styled.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        views.setTextViewText(id, styled)
        // Resolve the original recovered R IDs rather than assuming the source-build R ordering.
        views.setInt(id, "setBackgroundColor", Color.TRANSPARENT)
        runCatching {
            val ids = Class.forName("com.dohyun.overtimecalendar.R\$id")
            val name = ids.fields.firstOrNull { it.type == Int::class.javaPrimitiveType && it.getInt(null) == id }?.name
            if (name != null && name.startsWith("label_")) {
                val background = ids.getField("bg_$name").getInt(null)
                views.setImageViewBitmap(background, null as Bitmap?)
                views.setViewVisibility(background, View.GONE)
            }
        }
    }
}
