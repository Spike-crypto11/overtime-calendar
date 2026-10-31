package com.dohyun.overtimecalendar

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout

/** Additive entry points for the separately signed, APK-recovered preview edition. */
object PayrollBridge {
    @JvmStatic fun acknowledgeRecords(ctx: Context, date: String, sent: List<DayRecord>) {
        synchronized(Prefs) {
            if (Prefs.getRecords(ctx, date) == sent) Prefs.removePending(ctx, date)
        }
    }
    @JvmStatic fun recordChanged(ctx: Context, date: String) { PayrollStore.mark(ctx, date.take(7)) }
    @JvmStatic fun recordsUploaded(ctx: Context, date: String) { PayrollSync.scheduleMonth(ctx, date.take(7)) }
    @JvmStatic fun monthLoaded(ctx: Context, month: String) { PayrollSync.scheduleMonth(ctx, month) }
    @JvmStatic fun resume(ctx: Context) { PayrollSync.flush(ctx) }
    @JvmStatic fun attachSettings(activity: Activity) {
        fun find(group: ViewGroup): LinearLayout? {
            if (group is LinearLayout && group.orientation == LinearLayout.VERTICAL) return group
            for (i in 0 until group.childCount) {
                val child = group.getChildAt(i)
                if (child is ViewGroup) find(child)?.let { return it }
            }
            return null
        }
        val body = find(activity.findViewById(android.R.id.content)) ?: return
        body.addView(Button(activity).apply {
            text = "월별 집계 · 급여 검증"
            setOnClickListener { activity.startActivity(Intent(activity, PayrollActivity::class.java)) }
        })
    }
}
