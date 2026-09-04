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
    private var month = 0

    private lateinit var tvMonthTitle: TextView
    private lateinit var sumContainer: LinearLayout
    private lateinit var recycler: RecyclerView
    private lateinit var adapter: CalendarAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val c = Calendar.getInstance()
        year = c.get(Calendar.YEAR)
        month = c.get(Calendar.MONTH) + 1

        tvMonthTitle = findViewById(R.id.tvMonthTitle)
        sumContainer = findViewById(R.id.sumContainer)
        recycler = findViewById(R.id.recyclerCalendar)

        buildWeekdayHeader()

        adapter = CalendarAdapter(emptyList(), Prefs.getCategories(this)) { cell -> openInput(cell.date) }
        recycler.layoutManager = GridLayoutManager(this, 7)
        recycler.adapter = adapter

        findViewById<Button>(R.id.btnPrev).setOnClickListener { changeMonth(-1) }
        findViewById<Button>(R.id.btnNext).setOnClickListener { changeMonth(1) }
        tvMonthTitle.setOnClickListener { showMonthPicker() }
        findViewById<Button>(R.id.btnToday).setOnClickListener { goToday() }
        findViewById<Button>(R.id.btnRefresh).setOnClickListener { refreshAll() }
        findViewById<Button>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        render()
        SyncManager.flushPending(this)
        SyncManager.pullMonth(this, DateUtils.monthPrefix(year, month)) { ok ->
            if (ok) { render(); CalendarWidgetProvider.updateAll(this) }
        }
        // 특일을 항상 재시도 (이미 있어도 조용히 최신화)
        SyncManager.pullHolidays(this) { ok ->
            if (ok) { render(); CalendarWidgetProvider.updateAll(this) }
        }
        // 일정 받아오기
        SyncManager.pullEvents(this) { ok ->
            if (ok) { render(); CalendarWidgetProvider.updateAll(this) }
        }
    }

    private fun buildWeekdayHeader() {
        val header = findViewById<LinearLayout>(R.id.weekdayHeader)
        header.removeAllViews()
        for (i in 0..6) {
            val tv = TextView(this)
            tv.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            tv.gravity = Gravity.CENTER
            tv.text = DateUtils.weekdayLabel(i)
            tv.textSize = 13f
            tv.setPadding(0, 12, 0, 12)
            val col = when (i) { 0 -> R.color.sunday; 6 -> R.color.saturday; else -> R.color.weekday_text }
            tv.setTextColor(ContextCompat.getColor(this, col))
            header.addView(tv)
        }
    }

    /** 서버에서 기록·특일·일정 강제 새로고침 */
    private fun refreshAll() {
        SyncManager.flushPending(this)
        SyncManager.pullMonth(this, DateUtils.monthPrefix(year, month)) { ok ->
            if (ok) { render(); CalendarWidgetProvider.updateAll(this) }
        }
        SyncManager.pullHolidays(this) { ok ->
            if (ok) { render(); CalendarWidgetProvider.updateAll(this) }
        }
        SyncManager.pullEvents(this) { ok ->
            if (ok) { render(); CalendarWidgetProvider.updateAll(this) }
        }
        android.widget.Toast.makeText(this, "새로고침 중...", android.widget.Toast.LENGTH_SHORT).show()
    }

    /** 이번 달로 이동 */
    private fun goToday() {
        val c = Calendar.getInstance()
        year = c.get(Calendar.YEAR)
        month = c.get(Calendar.MONTH) + 1
        render()
        SyncManager.pullMonth(this, DateUtils.monthPrefix(year, month)) { ok -> if (ok) render() }
    }

    /** 상단 제목 클릭 → 연/월 선택 */
    private fun showMonthPicker() {
        val ctx = this
        val container = LinearLayout(ctx)
        container.orientation = LinearLayout.HORIZONTAL
        container.gravity = Gravity.CENTER
        val pad = (16 * resources.displayMetrics.density).toInt()
        container.setPadding(pad, pad, pad, pad)

        val yearPicker = android.widget.NumberPicker(ctx)
        yearPicker.minValue = 2020
        yearPicker.maxValue = 2035
        yearPicker.value = year

        val monthPicker = android.widget.NumberPicker(ctx)
        monthPicker.minValue = 1
        monthPicker.maxValue = 12
        monthPicker.value = month

        val yl = TextView(ctx); yl.text = "년 "; yl.textSize = 16f
        val ml = TextView(ctx); ml.text = "월"; ml.textSize = 16f
        container.addView(yearPicker)
        container.addView(yl)
        container.addView(monthPicker)
        container.addView(ml)

        androidx.appcompat.app.AlertDialog.Builder(ctx)
            .setTitle("연·월 선택")
            .setView(container)
            .setPositiveButton("이동") { _, _ ->
                year = yearPicker.value
                month = monthPicker.value
                render()
                SyncManager.pullMonth(this, DateUtils.monthPrefix(year, month)) { ok -> if (ok) render() }
            }
            .setNegativeButton("취소", null)
            .setNeutralButton("오늘") { _, _ -> goToday() }
            .show()
    }

    private fun changeMonth(delta: Int) {
        month += delta
        if (month < 1) { month = 12; year-- }
        if (month > 12) { month = 1; year++ }
        render()
        SyncManager.pullMonth(this, DateUtils.monthPrefix(year, month)) { ok -> if (ok) render() }
    }

    private fun render() {
        tvMonthTitle.text = "${year}년 ${month}월"

        val allRecords = Prefs.getAllRecords(this)
        val allHolidays = Prefs.getAllHolidays(this)
        val cats = Prefs.getCategories(this)
        val cells = ArrayList<CalendarCell>()
        val lead = DateUtils.firstWeekdayIndex(year, month)
        val ndays = DateUtils.daysInMonth(year, month)
        val today = DateUtils.today()

        for (i in 0 until lead) cells.add(CalendarCell(0, "", i % 7, false, emptyList(), emptyList(), emptyList()))

        val sums = HashMap<String, Double>()
        for (d in 1..ndays) {
            val date = DateUtils.ymd(year, month, d)
            val wd = (lead + d - 1) % 7
            val recs = allRecords[date] ?: emptyList()
            val hols = allHolidays[date] ?: emptyList()
            val evs = Prefs.eventsOn(this, date)
            for (r in recs) sums[r.categoryId] = (sums[r.categoryId] ?: 0.0) + r.value
            cells.add(CalendarCell(d, date, wd, date == today, recs, hols, evs))
        }
        while (cells.size % 7 != 0) cells.add(CalendarCell(0, "", cells.size % 7, false, emptyList(), emptyList(), emptyList()))

        // 공휴일 이름맵 (연휴 대표일 계산용): 날짜 -> 공휴일 이름
        val holNames = HashMap<String, String>()
        for ((d, list) in allHolidays) {
            val h = list.firstOrNull { it.kind == "holiday" }
            if (h != null) holNames[d] = h.name
        }
        adapter.update(cells, cats, holNames)
        renderSums(cats, sums)
    }

    /** 숫자 항목별 합계를 하단에 표시 */
    private fun renderSums(cats: List<Category>, sums: Map<String, Double>) {
        sumContainer.removeAllViews()
        val numCats = cats.filter { it.hasNumber }
        for (cat in numCats) {
            val tv = TextView(this)
            tv.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            tv.gravity = Gravity.CENTER
            tv.textSize = 15f
            tv.setTextColor(cat.color)
            val v = sums[cat.id] ?: 0.0
            tv.text = "${cat.name} ${fmt(v)}"
            sumContainer.addView(tv)
        }
    }

    private fun openInput(date: String) {
        if (date.isEmpty()) return
        showInputDialog(date)
    }

    /** 날짜 입력 다이얼로그 (앱 내부에서 직접 → 저장 후 즉시 갱신됨) */
    private fun showInputDialog(date: String) {
        val view = layoutInflater.inflate(R.layout.dialog_input, null)
        val container = view.findViewById<LinearLayout>(R.id.itemContainer)
        view.findViewById<TextView>(R.id.tvDialogTitle).text = DateUtils.withWeekday(date)

        val cats = Prefs.getCategories(this)
        val existing = Prefs.getRecords(this, date).associateBy { it.categoryId }
        val checkBoxes = HashMap<String, android.widget.CheckBox>()
        val valueInputs = HashMap<String, android.widget.EditText>()

        for (cat in cats) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(0, dpM(4), 0, dpM(4))

            val cb = android.widget.CheckBox(this)
            cb.text = "${cat.emoji} ${cat.name}"
            cb.textSize = 15f
            cb.setTextColor(cat.color)
            cb.setSingleLine(true)
            val rec = existing[cat.id]
            cb.isChecked = rec != null
            cb.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            checkBoxes[cat.id] = cb
            row.addView(cb)

            if (cat.hasNumber) {
                val et = android.widget.EditText(this)
                et.inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
                et.hint = "시간"
                et.setSingleLine(true)
                et.layoutParams = LinearLayout.LayoutParams(dpM(70), LinearLayout.LayoutParams.WRAP_CONTENT)
                when {
                    rec != null && rec.value > 0 -> et.setText(fmt(rec.value))
                    cb.isChecked && cat.defaultValue > 0 -> et.setText(fmt(cat.defaultValue))
                    else -> et.setText("")
                }
                valueInputs[cat.id] = et
                row.addView(et)
                cb.setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked && et.text.toString().isBlank() && cat.defaultValue > 0) et.setText(fmt(cat.defaultValue))
                }
            }
            container.addView(row)
        }

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this).setView(view).create()

        view.findViewById<TextView>(R.id.tvManageCats).setOnClickListener {
            dialog.dismiss()
            startActivity(Intent(this, CategoryActivity::class.java))
        }
        view.findViewById<TextView>(R.id.tvOpenApp).setOnClickListener {
            dialog.dismiss()
            startActivity(Intent(this, EventActivity::class.java))
        }
        view.findViewById<Button>(R.id.btnCancel).setOnClickListener { dialog.dismiss() }
        view.findViewById<Button>(R.id.btnSave).setOnClickListener {
            val records = ArrayList<DayRecord>()
            for (cat in cats) {
                val cb = checkBoxes[cat.id] ?: continue
                if (!cb.isChecked) continue
                val value = if (cat.hasNumber) parseNum(valueInputs[cat.id]?.text?.toString() ?: "") else 0.0
                records.add(DayRecord(cat.id, value))
            }
            Prefs.setRecords(this, date, records)
            SyncManager.push(this, date, records, null)
            CalendarWidgetProvider.updateAll(this)
            render()  // ★ 저장 즉시 앱 달력 갱신
            dialog.dismiss()
            android.widget.Toast.makeText(this, "저장되었습니다", android.widget.Toast.LENGTH_SHORT).show()
        }
        dialog.show()
    }

    private fun parseNum(s: String): Double {
        val t = s.trim()
        if (t.isEmpty()) return 0.0
        return try { t.toDouble().coerceAtLeast(0.0) } catch (_: Exception) { 0.0 }
    }

    private fun dpM(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun fmt(v: Double): String =
        if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()
}
