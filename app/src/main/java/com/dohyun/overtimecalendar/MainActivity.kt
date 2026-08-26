package com.dohyun.overtimecalendar

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.dohyun.overtimecalendar.widget.CalendarWidgetProvider
import java.util.Calendar

class MainActivity : AppCompatActivity() {

    private var year = 0
    private var month = 0  // 1~12

    private lateinit var tvMonthTitle: TextView
    private lateinit var tvOvertimeSum: TextView
    private lateinit var tvSpecialSum: TextView
    private lateinit var recycler: RecyclerView
    private lateinit var adapter: CalendarAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val c = Calendar.getInstance()
        year = c.get(Calendar.YEAR)
        month = c.get(Calendar.MONTH) + 1

        tvMonthTitle = findViewById(R.id.tvMonthTitle)
        tvOvertimeSum = findViewById(R.id.tvOvertimeSum)
        tvSpecialSum = findViewById(R.id.tvSpecialSum)
        recycler = findViewById(R.id.recyclerCalendar)

        buildWeekdayHeader()

        adapter = CalendarAdapter(emptyList()) { cell -> openInput(cell.date) }
        recycler.layoutManager = GridLayoutManager(this, 7)
        recycler.adapter = adapter

        findViewById<Button>(R.id.btnPrev).setOnClickListener { changeMonth(-1) }
        findViewById<Button>(R.id.btnNext).setOnClickListener { changeMonth(1) }
        findViewById<Button>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        render()
        // 서버에서 현재 월 당겨오기 + 대기분 재전송
        SyncManager.flushPending(this)
        SyncManager.pullMonth(this, DateUtils.monthPrefix(year, month)) { ok ->
            if (ok) {
                render()
                CalendarWidgetProvider.updateAll(this)
            }
        }
    }

    private fun buildWeekdayHeader() {
        val header = findViewById<LinearLayout>(R.id.weekdayHeader)
        header.removeAllViews()
        for (i in 0..6) {
            val tv = TextView(this)
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            tv.layoutParams = lp
            tv.gravity = Gravity.CENTER
            tv.text = DateUtils.weekdayLabel(i)
            tv.textSize = 13f
            tv.setPadding(0, 12, 0, 12)
            val col = when (i) {
                0 -> R.color.sunday
                6 -> R.color.saturday
                else -> R.color.weekday_text
            }
            tv.setTextColor(ContextCompat.getColor(this, col))
            header.addView(tv)
        }
    }

    private fun changeMonth(delta: Int) {
        month += delta
        if (month < 1) { month = 12; year-- }
        if (month > 12) { month = 1; year++ }
        render()
        SyncManager.pullMonth(this, DateUtils.monthPrefix(year, month)) { ok ->
            if (ok) render()
        }
    }

    private fun render() {
        tvMonthTitle.text = "${year}년 ${month}월"

        val all = Prefs.getAll(this)
        val cells = ArrayList<CalendarCell>()
        val lead = DateUtils.firstWeekdayIndex(year, month)
        val ndays = DateUtils.daysInMonth(year, month)
        val today = DateUtils.today()

        // 앞쪽 빈 칸
        for (i in 0 until lead) {
            cells.add(CalendarCell(0, "", i % 7, false, null))
        }
        var otSum = 0.0
        var spSum = 0.0
        for (d in 1..ndays) {
            val date = DateUtils.ymd(year, month, d)
            val wd = (lead + d - 1) % 7
            val entry = all[date]
            if (entry != null) { otSum += entry.overtime; spSum += entry.special }
            cells.add(CalendarCell(d, date, wd, date == today, entry))
        }
        // 뒤쪽 빈 칸 (7의 배수로 채움)
        while (cells.size % 7 != 0) {
            cells.add(CalendarCell(0, "", cells.size % 7, false, null))
        }

        adapter.update(cells)
        tvOvertimeSum.text = "잔업 합계: ${fmt(otSum)}"
        tvSpecialSum.text = "특근 합계: ${fmt(spSum)}"
    }

    private fun openInput(date: String) {
        if (date.isEmpty()) return
        val i = Intent(this, InputDialogActivity::class.java)
        i.putExtra(InputDialogActivity.EXTRA_DATE, date)
        startActivity(i)
    }

    private fun fmt(v: Double): String =
        if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()
}
