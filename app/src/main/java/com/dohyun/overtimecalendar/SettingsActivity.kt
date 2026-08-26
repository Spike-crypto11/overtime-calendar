package com.dohyun.overtimecalendar

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.dohyun.overtimecalendar.widget.CalendarWidgetProvider

class SettingsActivity : AppCompatActivity() {

    private var selectedOt = 0
    private var selectedSp = 0

    private lateinit var paletteOt: LinearLayout
    private lateinit var paletteSp: LinearLayout
    private lateinit var tvPreview: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val etUrl = findViewById<EditText>(R.id.etUrl)
        val tvResult = findViewById<TextView>(R.id.tvTestResult)
        paletteOt = findViewById(R.id.paletteOvertime)
        paletteSp = findViewById(R.id.paletteSpecial)
        tvPreview = findViewById(R.id.tvColorPreview)

        etUrl.setText(Prefs.getUrl(this))

        selectedOt = Prefs.getOvertimeColor(this)
        selectedSp = Prefs.getSpecialColor(this)

        buildPalette(paletteOt, isOvertime = true)
        buildPalette(paletteSp, isOvertime = false)
        updatePreview()

        findViewById<Button>(R.id.btnSaveUrl).setOnClickListener {
            Prefs.setUrl(this, etUrl.text.toString())
            Toast.makeText(this, "URL 저장됨", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnTest).setOnClickListener {
            val url = etUrl.text.toString().trim()
            if (url.isEmpty()) {
                tvResult.text = "URL을 먼저 입력하세요."
                return@setOnClickListener
            }
            tvResult.text = "연결 확인 중..."
            SyncManager.test(this, url) { ok, msg ->
                tvResult.text = msg
                tvResult.setTextColor(if (ok) 0xFF1A6E1A.toInt() else 0xFFCC0000.toInt())
                if (ok) Prefs.setUrl(this, url)
            }
        }

        findViewById<Button>(R.id.btnSaveColor).setOnClickListener {
            Prefs.setOvertimeColor(this, selectedOt)
            Prefs.setSpecialColor(this, selectedSp)
            CalendarWidgetProvider.updateAll(this)
            Toast.makeText(this, "색상 저장됨", Toast.LENGTH_SHORT).show()
        }
    }

    /** 팔레트 색 동그라미들을 생성 */
    private fun buildPalette(container: LinearLayout, isOvertime: Boolean) {
        container.removeAllViews()
        val size = dp(36)
        val margin = dp(4)
        for (color in Prefs.PALETTE) {
            val dot = View(this)
            val lp = LinearLayout.LayoutParams(size, size)
            lp.setMargins(margin, margin, margin, margin)
            dot.layoutParams = lp
            dot.background = makeDot(color, selected = isSelected(isOvertime, color))
            dot.setOnClickListener {
                if (isOvertime) selectedOt = color else selectedSp = color
                buildPalette(container, isOvertime)  // 선택표시 갱신
                updatePreview()
            }
            container.addView(dot)
        }
    }

    private fun isSelected(isOvertime: Boolean, color: Int): Boolean =
        if (isOvertime) color == selectedOt else color == selectedSp

    /** 원형 색상 배경 (선택 시 테두리) */
    private fun makeDot(color: Int, selected: Boolean): GradientDrawable {
        val d = GradientDrawable()
        d.shape = GradientDrawable.OVAL
        d.setColor(color)
        if (selected) {
            d.setStroke(dp(3), Color.BLACK)
        } else {
            d.setStroke(dp(1), 0xFFCCCCCC.toInt())
        }
        return d
    }

    private fun updatePreview() {
        val s = android.text.SpannableString("미리보기:  잔업 3   특근 8")
        val otStart = s.indexOf("잔업")
        val spStart = s.indexOf("특근")
        s.setSpan(
            android.text.style.ForegroundColorSpan(selectedOt),
            otStart, otStart + 4,
            android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        s.setSpan(
            android.text.style.ForegroundColorSpan(selectedSp),
            spStart, spStart + 4,
            android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        tvPreview.text = s
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
