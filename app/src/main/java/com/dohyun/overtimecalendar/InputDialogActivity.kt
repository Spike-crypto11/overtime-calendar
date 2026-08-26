package com.dohyun.overtimecalendar

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.dohyun.overtimecalendar.widget.CalendarWidgetProvider

/** 날짜 하나의 잔업/특근/메모를 입력하는 팝업. 앱과 위젯 모두에서 호출됨. */
class InputDialogActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_DATE = "date"
    }

    private lateinit var date: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_input)

        date = intent.getStringExtra(EXTRA_DATE) ?: DateUtils.today()

        val tvTitle = findViewById<TextView>(R.id.tvDialogTitle)
        val etOvertime = findViewById<EditText>(R.id.etOvertime)
        val etSpecial = findViewById<EditText>(R.id.etSpecial)
        val etMemo = findViewById<EditText>(R.id.etMemo)

        tvTitle.text = DateUtils.withWeekday(date)

        val cur = Prefs.get(this, date)
        if (cur != null) {
            if (cur.overtime > 0) etOvertime.setText(fmt(cur.overtime))
            if (cur.special > 0) etSpecial.setText(fmt(cur.special))
            if (cur.memo.isNotBlank()) etMemo.setText(cur.memo)
        }

        findViewById<Button>(R.id.btnSave).setOnClickListener {
            val ot = parse(etOvertime.text.toString())
            val sp = parse(etSpecial.text.toString())
            val memo = etMemo.text.toString().trim()
            saveEntry(DayEntry(ot, sp, memo))
        }
        findViewById<Button>(R.id.btnDelete).setOnClickListener {
            saveEntry(DayEntry())  // 빈 값 = 삭제
        }
        findViewById<Button>(R.id.btnCancel).setOnClickListener { finish() }
    }

    private fun saveEntry(entry: DayEntry) {
        Prefs.set(this, date, entry)
        CalendarWidgetProvider.updateAll(this)
        SyncManager.push(this, date, entry) { ok ->
            if (!ok) {
                Toast.makeText(
                    this,
                    "저장됨(로컬). 서버 전송은 나중에 재시도합니다.",
                    Toast.LENGTH_SHORT
                ).show()
            }
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
}
