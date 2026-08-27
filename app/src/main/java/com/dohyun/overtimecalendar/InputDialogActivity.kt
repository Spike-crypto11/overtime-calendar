package com.dohyun.overtimecalendar

import android.content.Intent
import android.os.Bundle
import android.text.InputType
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

/** 날짜 하나에 대해 여러 항목을 체크/입력하는 팝업. 앱·위젯 공용. */
class InputDialogActivity : AppCompatActivity() {

    companion object { const val EXTRA_DATE = "date" }

    private lateinit var date: String
    private lateinit var container: LinearLayout

    // 카테고리별 UI 참조
    private val checkBoxes = HashMap<String, CheckBox>()
    private val valueInputs = HashMap<String, EditText>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_input)

        date = intent.getStringExtra(EXTRA_DATE) ?: DateUtils.today()
        container = findViewById(R.id.itemContainer)
        findViewById<TextView>(R.id.tvDialogTitle).text = DateUtils.withWeekday(date)

        buildItems()

        findViewById<TextView>(R.id.tvManageCats).setOnClickListener {
            startActivity(Intent(this, CategoryActivity::class.java))
        }
        findViewById<Button>(R.id.btnSave).setOnClickListener { save() }
        findViewById<Button>(R.id.btnCancel).setOnClickListener { finish() }
    }

    override fun onResume() {
        super.onResume()
        // 항목 관리 후 돌아왔을 때 목록 갱신
        buildItems()
    }

    private fun buildItems() {
        container.removeAllViews()
        checkBoxes.clear()
        valueInputs.clear()

        val cats = Prefs.getCategories(this)
        val existing = Prefs.getRecords(this, date).associateBy { it.categoryId }

        for (cat in cats) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(0, dp(6), 0, dp(6))

            val cb = CheckBox(this)
            cb.text = "${cat.emoji} ${cat.name}"
            cb.textSize = 16f
            cb.setTextColor(cat.color)
            val rec = existing[cat.id]
            cb.isChecked = rec != null
            val cbLp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            cb.layoutParams = cbLp
            checkBoxes[cat.id] = cb
            row.addView(cb)

            if (cat.hasNumber) {
                val et = EditText(this)
                et.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                et.hint = "시간"
                et.width = dp(80)
                // 기존 기록이 있으면 그 값, 없으면 체크 상태에 따라 기본값
                when {
                    rec != null && rec.value > 0 -> et.setText(fmt(rec.value))
                    cb.isChecked && cat.defaultValue > 0 -> et.setText(fmt(cat.defaultValue))
                    else -> et.setText("")
                }
                valueInputs[cat.id] = et
                row.addView(et)

                // 체크할 때 비어있으면 기본값 자동 채움
                cb.setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked && et.text.toString().isBlank() && cat.defaultValue > 0) {
                        et.setText(fmt(cat.defaultValue))
                    }
                }
            }

            container.addView(row)
        }

        if (cats.isEmpty()) {
            val tv = TextView(this)
            tv.text = "항목이 없습니다. 아래에서 항목을 추가하세요."
            tv.setPadding(0, dp(12), 0, dp(12))
            container.addView(tv)
        }
    }

    private fun save() {
        val records = ArrayList<DayRecord>()
        val cats = Prefs.getCategories(this)
        for (cat in cats) {
            val cb = checkBoxes[cat.id] ?: continue
            if (!cb.isChecked) continue
            val value = if (cat.hasNumber) {
                parse(valueInputs[cat.id]?.text?.toString() ?: "")
            } else 0.0
            records.add(DayRecord(cat.id, value))
        }

        Prefs.setRecords(this, date, records)
        CalendarWidgetProvider.updateAll(this)
        SyncManager.push(this, date, records) { ok ->
            if (!ok) Toast.makeText(this, "저장됨(로컬). 서버 전송은 나중에 재시도합니다.", Toast.LENGTH_SHORT).show()
        }
        Toast.makeText(this, "저장되었습니다", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun parse(s: String): Double {
        val t = s.trim()
        if (t.isEmpty()) return 0.0
        return try { t.toDouble().coerceAtLeast(0.0) } catch (_: Exception) { 0.0 }
    }

    private fun fmt(v: Double): String =
        if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
