package com.dohyun.overtimecalendar.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.dohyun.overtimecalendar.DateUtils
import com.dohyun.overtimecalendar.InputDialogActivity
import com.dohyun.overtimecalendar.Prefs
import com.dohyun.overtimecalendar.R
import java.util.Calendar

/** 홈 화면 위젯: 이번 달 달력(42칸). 칸을 누르면 입력 팝업이 열림. */
class CalendarWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        for (id in ids) renderWidget(context, mgr, id)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            // 서버에서 이번 달 당겨오고 갱신
            val c = Calendar.getInstance()
            val prefix = DateUtils.monthPrefix(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1)
            com.dohyun.overtimecalendar.SyncManager.pullMonth(context, prefix) {
                updateAll(context)
            }
        }
    }

    companion object {
        const val ACTION_REFRESH = "com.dohyun.overtimecalendar.WIDGET_REFRESH"

        /** 배치된 모든 위젯 갱신 */
        fun updateAll(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val cn = ComponentName(context, CalendarWidgetProvider::class.java)
            val ids = mgr.getAppWidgetIds(cn)
            for (id in ids) renderWidget(context, mgr, id)
        }

        private fun renderWidget(context: Context, mgr: AppWidgetManager, widgetId: Int) {
            val rv = RemoteViews(context.packageName, R.layout.widget_calendar)

            val c = Calendar.getInstance()
            val year = c.get(Calendar.YEAR)
            val month = c.get(Calendar.MONTH) + 1

            rv.setTextViewText(R.id.widgetTitle, "${year}년 ${month}월")

            // 새로고침 버튼
            val refreshIntent = Intent(context, CalendarWidgetProvider::class.java).apply {
                action = ACTION_REFRESH
            }
            val refreshPi = PendingIntent.getBroadcast(
                context, 9999, refreshIntent, piFlags()
            )
            rv.setOnClickPendingIntent(R.id.widgetRefresh, refreshPi)

            val all = Prefs.getAll(context)
            val lead = DateUtils.firstWeekdayIndex(year, month)
            val ndays = DateUtils.daysInMonth(year, month)
            val today = DateUtils.today()
            val pkg = context.packageName
            val res = context.resources

            for (i in 0 until 42) {
                val dayId = res.getIdentifier("day_$i", "id", pkg)
                val otId = res.getIdentifier("ot_$i", "id", pkg)
                val spId = res.getIdentifier("sp_$i", "id", pkg)
                val cellId = res.getIdentifier("cell_$i", "id", pkg)

                val dayNum = i - lead + 1
                if (dayNum in 1..ndays) {
                    val date = DateUtils.ymd(year, month, dayNum)
                    val wd = i % 7
                    // 날짜 숫자 + 색상
                    rv.setTextViewText(dayId, dayNum.toString())
                    val dColor = when (wd) {
                        0 -> 0xFFCC0000.toInt()
                        6 -> 0xFF0055CC.toInt()
                        else -> 0xFF333333.toInt()
                    }
                    rv.setTextColor(dayId, dColor)

                    // 오늘 배경 강조
                    rv.setInt(
                        cellId, "setBackgroundColor",
                        if (date == today) 0xFFFFF3CC.toInt() else 0xFFFFFFFF.toInt()
                    )

                    val e = all[date]
                    rv.setTextViewText(otId, if (e != null && e.overtime > 0) fmt(e.overtime) else "")
                    rv.setTextViewText(spId, if (e != null && e.special > 0) fmt(e.special) else "")

                    // 클릭 → 입력 팝업
                    val clickIntent = Intent(context, InputDialogActivity::class.java).apply {
                        putExtra(InputDialogActivity.EXTRA_DATE, date)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        // 각 셀마다 고유 Intent가 되도록 data 설정
                        setData(android.net.Uri.parse("otcal://day/$date"))
                    }
                    val pi = PendingIntent.getActivity(
                        context, i, clickIntent, piFlags()
                    )
                    rv.setOnClickPendingIntent(cellId, pi)
                } else {
                    // 빈 칸
                    rv.setTextViewText(dayId, "")
                    rv.setTextViewText(otId, "")
                    rv.setTextViewText(spId, "")
                    rv.setInt(cellId, "setBackgroundColor", 0xFFF7F7F7.toInt())
                    rv.setOnClickPendingIntent(cellId, null)
                }
            }

            mgr.updateAppWidget(widgetId, rv)
        }

        private fun piFlags(): Int {
            return PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        }

        private fun fmt(v: Double): String =
            if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()
    }
}
