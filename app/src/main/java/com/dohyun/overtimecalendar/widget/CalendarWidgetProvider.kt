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
        when (intent.action) {
            ACTION_PREV -> { shiftMonth(context, -1); updateAll(context) }
            ACTION_NEXT -> { shiftMonth(context, 1); updateAll(context) }
            ACTION_TODAY -> { Prefs.clearWidgetYm(context); updateAll(context) }
            ACTION_REFRESH -> {
                // 현재 보는 달 기준으로 서버에서 다시 받아오고 갱신
                val ym = currentYm(context)
                SyncManager.pullMonth(context, ym) { updateAll(context) }
                SyncManager.pullHolidays(context) { updateAll(context) }
            }
        }
    }

    /** 위젯이 보는 달을 delta만큼 이동해 저장 */
    private fun shiftMonth(context: Context, delta: Int) {
        val ym = currentYm(context)
        var y = ym.substring(0, 4).toInt()
        var m = ym.substring(5, 7).toInt() + delta
        if (m < 1) { m = 12; y-- }
        if (m > 12) { m = 1; y++ }
        Prefs.setWidgetYm(context, String.format("%04d-%02d", y, m))
    }

    /** 현재 위젯이 보는 "yyyy-MM" (저장값 없으면 이번 달) */
    private fun currentYm(context: Context): String {
        val saved = Prefs.getWidgetYm(context)
        if (saved.matches(Regex("\\d{4}-\\d{2}"))) return saved
        val c = Calendar.getInstance()
        return String.format("%04d-%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1)
    }

    companion object {
        const val ACTION_REFRESH = "com.dohyun.overtimecalendar.WIDGET_REFRESH"
        const val ACTION_PREV = "com.dohyun.overtimecalendar.WIDGET_PREV"
        const val ACTION_NEXT = "com.dohyun.overtimecalendar.WIDGET_NEXT"
        const val ACTION_TODAY = "com.dohyun.overtimecalendar.WIDGET_TODAY"
        private const val MAX_LABELS = 2

        /** 네비게이션 버튼용 PendingIntent */
        private fun navPi(context: Context, action: String, reqCode: Int): PendingIntent {
            val intent = Intent(context, CalendarWidgetProvider::class.java).apply { this.action = action }
            return PendingIntent.getBroadcast(context, reqCode, intent, piFlags())
        }

        fun updateAll(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val cn = ComponentName(context, CalendarWidgetProvider::class.java)
            for (id in mgr.getAppWidgetIds(cn)) renderWidget(context, mgr, id)
        }

        private fun renderWidget(context: Context, mgr: AppWidgetManager, widgetId: Int) {
            val rv = RemoteViews(context.packageName, R.layout.widget_calendar)

            // 보고 있는 달 결정: 저장값 없으면 이번 달
            val cal = Calendar.getInstance()
            var year = cal.get(Calendar.YEAR)
            var month = cal.get(Calendar.MONTH) + 1
            val savedYm = Prefs.getWidgetYm(context)
            if (savedYm.matches(Regex("\\d{4}-\\d{2}"))) {
                year = savedYm.substring(0, 4).toInt()
                month = savedYm.substring(5, 7).toInt()
            }
            rv.setTextViewText(R.id.widgetTitle, "${year}년 ${month}월")

            // 제목(년월)을 누르면 앱 열기
            val openAppIntent = Intent(context, com.dohyun.overtimecalendar.MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            rv.setOnClickPendingIntent(
                R.id.widgetTitle,
                PendingIntent.getActivity(context, 9005, openAppIntent, piFlags())
            )

            // 네비게이션 버튼
            rv.setOnClickPendingIntent(R.id.widgetPrev, navPi(context, ACTION_PREV, 9001))
            rv.setOnClickPendingIntent(R.id.widgetNext, navPi(context, ACTION_NEXT, 9002))
            rv.setOnClickPendingIntent(R.id.widgetToday, navPi(context, ACTION_TODAY, 9003))
            rv.setOnClickPendingIntent(R.id.widgetRefresh, navPi(context, ACTION_REFRESH, 9004))

            val allRecords = Prefs.getAllRecords(context)
            val allHolidays = Prefs.getAllHolidays(context)
            val catMap: Map<String, Category> = Prefs.getCategories(context).associateBy { it.id }
            // 공휴일 이름맵 (연휴 대표일 계산용)
            val holNames = HashMap<String, String>()
            for ((d, list) in allHolidays) {
                val h = list.firstOrNull { it.kind == "holiday" }
                if (h != null) holNames[d] = h.name
            }
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
                    val hols = allHolidays[date] ?: emptyList()
                    val hasHoliday = hols.any { it.kind == "holiday" }
                    rv.setTextViewText(dayId, dayNum.toString())
                    val dColor = when {
                        hasHoliday -> 0xFFCC0000.toInt()
                        wd == 0 -> 0xFFCC0000.toInt()
                        wd == 6 -> 0xFF0055CC.toInt()
                        else -> 0xFF333333.toInt()
                    }
                    rv.setTextColor(dayId, dColor)
                    rv.setInt(cellId, "setBackgroundResource",
                        if (date == today) R.drawable.widget_cell_today else R.drawable.widget_cell_border)

                    // 특일 이름 (공휴일 > 절기 > 기념일)
                    val holId = res.getIdentifier("hol_$i", "id", pkg)
                    val topHol = hols.firstOrNull { it.kind == "holiday" }
                        ?: hols.firstOrNull { it.kind == "term" }
                        ?: hols.firstOrNull()
                    if (topHol != null) {
                        val showName = if (topHol.kind == "holiday") {
                            DateUtils.isNameAnchor(topHol.date, topHol.name) { d -> holNames[d] }
                        } else true
                        if (showName) {
                            rv.setTextViewText(holId, topHol.name)
                            rv.setTextColor(holId, Prefs.colorForKind(topHol.kind))
                            rv.setViewVisibility(holId, android.view.View.VISIBLE)
                        } else {
                            rv.setViewVisibility(holId, android.view.View.GONE)
                        }
                    } else {
                        rv.setViewVisibility(holId, android.view.View.GONE)
                    }

                    // 색막대 라벨 채우기: 일정 먼저, 그다음 기록
                    val recs = allRecords[date] ?: emptyList()
                    val evs = Prefs.eventsOn(context, date)
                    // (텍스트, 색, 크기sp) 아이템 목록 구성
                    val items = ArrayList<Triple<String, Int, Float>>()
                    for (ev in evs) items.add(Triple(ev.title, ev.color, 12f))
                    for (r in recs) {
                        val cat = catMap[r.categoryId] ?: continue
                        val numPart = if (cat.hasNumber && r.value > 0) " ${fmt(r.value)}" else ""
                        val text = if (cat.iconOnly) "${cat.emoji}$numPart" else "${cat.emoji}${cat.name}$numPart"
                        val size = if (cat.iconOnly) 16f else 12f
                        items.add(Triple(text, cat.color, size))
                    }

                    for (k in 0 until MAX_LABELS) {
                        val labelId = res.getIdentifier("label_${i}_$k", "id", pkg)
                        if (k < items.size) {
                            if (k == MAX_LABELS - 1 && items.size > MAX_LABELS) {
                                rv.setTextViewText(labelId, "+${items.size - (MAX_LABELS - 1)}")
                                rv.setInt(labelId, "setBackgroundColor", 0xFF999999.toInt())
                                rv.setTextColor(labelId, 0xFFFFFFFF.toInt())
                                rv.setTextViewTextSize(labelId, android.util.TypedValue.COMPLEX_UNIT_SP, 12f)
                            } else {
                                val (text, color, size) = items[k]
                                rv.setTextViewText(labelId, text)
                                rv.setInt(labelId, "setBackgroundColor", color)
                                rv.setTextColor(labelId, 0xFFFFFFFF.toInt())
                                rv.setTextViewTextSize(labelId, android.util.TypedValue.COMPLEX_UNIT_SP, size)
                            }
                            rv.setViewVisibility(labelId, android.view.View.VISIBLE)
                        } else {
                            rv.setViewVisibility(labelId, android.view.View.GONE)
                        }
                    }

                    // 클릭 → 입력 팝업
                    val clickIntent = Intent(context, InputDialogActivity::class.java).apply {
                        putExtra(InputDialogActivity.EXTRA_DATE, date)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_HISTORY)
                        data = Uri.parse("otcal://day/$date")
                    }
                    rv.setOnClickPendingIntent(
                        cellId,
                        PendingIntent.getActivity(context, i, clickIntent, piFlags())
                    )
                } else {
                    rv.setTextViewText(dayId, "")
                    val holId = res.getIdentifier("hol_$i", "id", pkg)
                    rv.setViewVisibility(holId, android.view.View.GONE)
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
