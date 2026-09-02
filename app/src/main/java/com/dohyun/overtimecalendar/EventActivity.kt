package com.dohyun.overtimecalendar

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.dohyun.overtimecalendar.widget.CalendarWidgetProvider
import java.util.Calendar

class EventActivity : AppCompatActivity() {

    private lateinit var listContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_events)
        listContainer = findViewById(R.id.eventList)

        findViewById<Button>(R.id.btnAddEvent).setOnClickListener { showEdit(null) }
        renderList()
    }

    override fun onResume() {
        super.onResume()
        // 서버에서 최신 일정 받아와 갱신
        SyncManager.pullEvents(this) { renderList() }
    }

    private fun renderList() {
        listContainer.removeAllViews()
        val events = Prefs.getEvents(this).sortedBy { it.start }
        if (events.isEmpty()) {
            val tv = TextView(this)
            tv.text = "등록된 일정이 없습니다.\n아래 '새 일정 추가'를 눌러보세요."
            tv.setPadding(dp(8), dp(24), dp(8), dp(24))
            tv.setTextColor(0xFF999999.toInt())
            listContainer.addView(tv)
            return
        }
        for (ev in events) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(dp(8), dp(14), dp(8), dp(14))
            row.setBackgroundResource(R.drawable.cell_bg)

            val dot = View(this)
            val dotLp = LinearLayout.LayoutParams(dp(20), dp(20)); dotLp.rightMargin = dp(12)
            dot.layoutParams = dotLp
            val d = GradientDrawable(); d.shape = GradientDrawable.OVAL; d.setColor(ev.color)
            dot.background = d
            row.addView(dot)

            val tv = TextView(this)
            val period = if (ev.start == ev.end) ev.start else "${ev.start} ~ ${ev.end}"
            val repeat = if (ev.yearly) " (매년)" else ""
            tv.text = "${ev.title}\n$period$repeat"
            tv.textSize = 15f
            val tvLp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            tv.layoutParams = tvLp
            row.addView(tv)

            row.setOnClickListener { showEdit(ev) }
            listContainer.addView(row)

            val div = View(this)
            div.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1))
            div.setBackgroundColor(0xFFEEEEEE.toInt())
            listContainer.addView(div)
        }
    }

    private fun showEdit(ev: Event?) {
        val view = layoutInflater.inflate(R.layout.dialog_event_edit, null)
        val etTitle = view.findViewById<EditText>(R.id.etEventTitle)
        val btnStart = view.findViewById<Button>(R.id.btnStartDate)
        val btnEnd = view.findViewById<Button>(R.id.btnEndDate)
        val cbYearly = view.findViewById<CheckBox>(R.id.cbYearly)
        val palette = view.findViewById<LinearLayout>(R.id.eventColorPalette)
        val btnDelete = view.findViewById<Button>(R.id.btnEventDelete)

        var startDate = ev?.start ?: DateUtils.today()
        var endDate = ev?.end ?: startDate
        var selectedColor = ev?.color ?: Prefs.PALETTE[3]

        etTitle.setText(ev?.title ?: "")
        cbYearly.isChecked = ev?.yearly ?: false
        btnStart.text = startDate
        btnEnd.text = endDate
        if (ev == null) btnDelete.visibility = View.GONE

        btnStart.setOnClickListener {
            pickDate(startDate) { picked -> startDate = picked; btnStart.text = picked
                if (endDate < startDate) { endDate = startDate; btnEnd.text = endDate } }
        }
        btnEnd.setOnClickListener {
            pickDate(endDate) { picked -> endDate = picked; btnEnd.text = picked }
        }

        // 색 팔레트
        val dots = ArrayList<View>()
        fun refresh() {
            for ((i, dv) in dots.withIndex()) {
                val gd = GradientDrawable(); gd.shape = GradientDrawable.OVAL; gd.setColor(Prefs.PALETTE[i])
                if (Prefs.PALETTE[i] == selectedColor) gd.setStroke(dp(3), Color.BLACK)
                else gd.setStroke(dp(1), 0xFFCCCCCC.toInt())
                dv.background = gd
            }
        }
        for (color in Prefs.PALETTE) {
            val dot = View(this)
            val lp = LinearLayout.LayoutParams(dp(30), dp(30)); lp.setMargins(dp(3), dp(3), dp(3), dp(3))
            dot.layoutParams = lp
            dot.setOnClickListener { selectedColor = color; refresh() }
            palette.addView(dot); dots.add(dot)
        }
        refresh()

        val dialog = AlertDialog.Builder(this).setView(view).create()

        view.findViewById<Button>(R.id.btnEventCancel).setOnClickListener { dialog.dismiss() }
        view.findViewById<Button>(R.id.btnEventSave).setOnClickListener {
            val title = etTitle.text.toString().trim()
            if (title.isEmpty()) { Toast.makeText(this, "제목을 입력하세요", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            if (endDate < startDate) endDate = startDate
            val id = ev?.id ?: ("ev_" + System.currentTimeMillis())
            val newEv = Event(id, title, startDate, endDate, selectedColor, cbYearly.isChecked)
            Prefs.upsertEvent(this, newEv)
            SyncManager.pushEvent(this, newEv, null)
            CalendarWidgetProvider.updateAll(this)
            dialog.dismiss()
            renderList()
        }
        btnDelete.setOnClickListener {
            if (ev == null) return@setOnClickListener
            AlertDialog.Builder(this)
                .setMessage("'${ev.title}' 일정을 삭제할까요?")
                .setPositiveButton("삭제") { _, _ ->
                    Prefs.deleteEvent(this, ev.id)
                    SyncManager.deleteEvent(this, ev.id, null)
                    CalendarWidgetProvider.updateAll(this)
                    dialog.dismiss()
                    renderList()
                }
                .setNegativeButton("취소", null).show()
        }
        dialog.show()
    }

    private fun pickDate(current: String, onPicked: (String) -> Unit) {
        val c = Calendar.getInstance()
        try {
            val p = current.split("-")
            c.set(p[0].toInt(), p[1].toInt() - 1, p[2].toInt())
        } catch (_: Exception) {}
        DatePickerDialog(this, { _, y, m, d ->
            onPicked(DateUtils.ymd(y, m + 1, d))
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
