package com.dohyun.overtimecalendar.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import com.dohyun.overtimecalendar.Category
import com.dohyun.overtimecalendar.DateUtils
import com.dohyun.overtimecalendar.InputDialogActivity
import com.dohyun.overtimecalendar.Prefs
import com.dohyun.overtimecalendar.R
import com.dohyun.overtimecalendar.SyncManager
import java.util.Calendar

/** 홈 위젯: 삼성 캘린더 스타일 (색막대 + 이모지 + 이름 + 숫자), 4x6. */
class CalendarWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        for (id in ids) renderWidget(context, mgr, id)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            val c = Calendar.getInstance()
            val prefix = DateUtils.monthPrefix(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1)
            SyncManager.pullMonth(context, prefix) { updateAll(context) }
        }
    }

    companion object {
        const val ACTION_REFRESH = "com.dohyun.overtimecalendar.WIDGET_REFRESH"
        private const val MAX_LABELS = 2

        fun updateAll(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val cn = ComponentName(context, CalendarWidgetProvider::class.java)
            for (id in mgr.getAppWidgetIds(cn)) renderWidget(context, mgr, id)
        }

        private fun renderWidget(context: Context, mgr: AppWidgetManager, widgetId: Int) {
            val rv = RemoteViews(context.packageName, R.layout.widget_calendar)
            val c = Calendar.getInstance()
            val year = c.get(Calendar.YEAR)
            val month = c.get(Calendar.MONTH) + 1
            rv.setTextViewText(R.id.widgetTitle, "${year}년 ${month}월")

            // 새로고침 버튼
            val refreshIntent = Intent(context, CalendarWidgetProvider::class.java).apply { action = ACTION_REFRESH }
            rv.setOnClickPendingIntent(
                R.id.widgetRefresh,
                PendingIntent.getBroadcast(context, 9999, refreshIntent, piFlags())
            )

            val allRecords = Prefs.getAllRecords(context)
            val catMap: Map<String, Category> = Prefs.getCategories(context).associateBy { it.id }
            val lead = DateUtils.firstWeekdayIndex(year, month)
            val ndays = DateUtils.daysInMonth(year, month)
            val today = DateUtils.today()
            val pkg = context.packageName
            val res = context.resources

            for (i in 0 until 42) {
                val dayId = res.getIdentifier("day_$i", "id", pkg)
                val cellId = res.getIdentifier("cell_$i", "id", pkg)
                val dayNum = i - lead + 1

                if (dayNum in 1..ndays) {
                    val date = DateUtils.ymd(year, month, dayNum)
                    val wd = i % 7
                    rv.setTextViewText(dayId, dayNum.toString())
                    val dColor = when (wd) {
                        0 -> 0xFFCC0000.toInt()
                        6 -> 0xFF0055CC.toInt()
                        else -> 0xFF333333.toInt()
                    }
                    rv.setTextColor(dayId, dColor)
                    rv.setInt(cellId, "setBackgroundResource",
                        if (date == today) R.drawable.widget_cell_today else R.drawable.widget_cell_border)

                    // 색막대 라벨 채우기
                    val recs = allRecords[date] ?: emptyList()
                    for (k in 0 until MAX_LABELS) {
                        val labelId = res.getIdentifier("label_${i}_$k", "id", pkg)
                        if (k < recs.size && k < MAX_LABELS) {
                            // 마지막 슬롯인데 항목이 더 많으면 "+N"
                            if (k == MAX_LABELS - 1 && recs.size > MAX_LABELS) {
                                rv.setTextViewText(labelId, "+${recs.size - (MAX_LABELS - 1)}")
                                rv.setInt(labelId, "setBackgroundColor", 0xFF999999.toInt())
                                rv.setTextColor(labelId, 0xFFFFFFFF.toInt())
                            } else {
                                val r = recs[k]
                                val cat = catMap[r.categoryId]
                                if (cat != null) {
                                    val numPart = if (cat.hasNumber && r.value > 0) " ${fmt(r.value)}" else ""
                                    rv.setTextViewText(labelId, "${cat.emoji}${cat.name}$numPart")
                                    rv.setInt(labelId, "setBackgroundColor", cat.color)
                                    rv.setTextColor(labelId, 0xFFFFFFFF.toInt())
                                } else {
                                    rv.setTextViewText(labelId, "")
                                }
                            }
                            rv.setViewVisibility(labelId, android.view.View.VISIBLE)
                        } else {
                            rv.setViewVisibility(labelId, android.view.View.GONE)
                        }
                    }

                    // 클릭 → 입력 팝업
                    val clickIntent = Intent(context, InputDialogActivity::class.java).apply {
                        putExtra(InputDialogActivity.EXTRA_DATE, date)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        data = Uri.parse("otcal://day/$date")
                    }
                    rv.setOnClickPendingIntent(
                        cellId,
                        PendingIntent.getActivity(context, i, clickIntent, piFlags())
                    )
                } else {
                    rv.setTextViewText(dayId, "")
                    for (k in 0 until MAX_LABELS) {
                        val labelId = res.getIdentifier("label_${i}_$k", "id", pkg)
                        rv.setViewVisibility(labelId, android.view.View.GONE)
                    }
                    rv.setInt(cellId, "setBackgroundResource", R.drawable.widget_cell_border)
                    rv.setOnClickPendingIntent(cellId, null)
                }
            }

            mgr.updateAppWidget(widgetId, rv)
        }

        private fun piFlags(): Int =
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        private fun fmt(v: Double): String =
            if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()
    }
}
