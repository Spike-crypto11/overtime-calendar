package com.dohyun.overtimecalendar

import android.app.Activity
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog

object ColorPicker {
    /** 원형 색상환 + 밝기 슬라이더로 색을 고르는 다이얼로그 */
    fun show(activity: Activity, current: Int, onPicked: (Int) -> Unit) {
        val d = activity.resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()

        val box = LinearLayout(activity)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(20), dp(20), dp(20), dp(10))
        box.gravity = Gravity.CENTER_HORIZONTAL

        // 미리보기
        val preview = View(activity)
        val pvLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(44))
        pvLp.bottomMargin = dp(12)
        preview.layoutParams = pvLp
        preview.setBackgroundColor(current)
        box.addView(preview)

        // 색상환
        val wheel = ColorWheelView(activity)
        val wLp = LinearLayout.LayoutParams(dp(240), dp(240))
        wheel.layoutParams = wLp
        wheel.setColor(current)
        box.addView(wheel)

        // 밝기 슬라이더
        val bLabel = TextView(activity); bLabel.text = "밝기"; bLabel.setPadding(0, dp(14), 0, 0)
        box.addView(bLabel)
        val bright = SeekBar(activity)
        bright.max = 100
        val hsv = FloatArray(3); Color.colorToHSV(current, hsv)
        bright.progress = (hsv[2] * 100).toInt()
        val bLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        bright.layoutParams = bLp
        box.addView(bright)

        wheel.onColorChanged = { c -> preview.setBackgroundColor(c) }
        bright.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, u: Boolean) {
                wheel.setBrightness(p / 100f)
                preview.setBackgroundColor(wheel.currentColor())
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })

        AlertDialog.Builder(activity)
            .setTitle("색 고르기")
            .setView(box)
            .setPositiveButton("선택") { _, _ ->
                onPicked(0xFF000000.toInt() or (wheel.currentColor() and 0xFFFFFF))
            }
            .setNegativeButton("취소", null)
            .show()
    }
}
