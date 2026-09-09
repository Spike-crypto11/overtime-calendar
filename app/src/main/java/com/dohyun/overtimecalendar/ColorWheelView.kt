package com.dohyun.overtimecalendar

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * 원형 HSV 색상환. 원 안에서 터치하면 그 위치의 색을 고름.
 * - 각도 = 색상(Hue), 중심에서의 거리 = 채도(Saturation)
 * - 밝기(Value)는 setBrightness로 조절
 */
class ColorWheelView(context: Context) : View(context) {

    private var brightness = 1f
    private var selHue = 0f
    private var selSat = 0f
    var onColorChanged: ((Int) -> Unit)? = null

    private val wheelPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val selPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        color = Color.BLACK
    }
    private val selPaintInner = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.WHITE
    }

    private var cx = 0f
    private var cy = 0f
    private var radius = 0f

    fun setBrightness(v: Float) {
        brightness = v.coerceIn(0f, 1f)
        invalidate()
        emit()
    }

    fun setColor(color: Int) {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        selHue = hsv[0]
        selSat = hsv[1]
        brightness = hsv[2]
        invalidate()
    }

    fun currentColor(): Int = Color.HSVToColor(floatArrayOf(selHue, selSat, brightness))

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        cx = w / 2f
        cy = h / 2f
        radius = (minOf(w, h) / 2f) - 8f
    }

    override fun onDraw(canvas: Canvas) {
        // 색상환: 각도별 색상, 방사형 채도 그라데이션
        val sweepColors = IntArray(361)
        for (i in 0..360) {
            sweepColors[i] = Color.HSVToColor(floatArrayOf(i.toFloat() % 360f, 1f, brightness))
        }
        val sweep = SweepGradient(cx, cy, sweepColors, null)
        wheelPaint.shader = sweep
        canvas.drawCircle(cx, cy, radius, wheelPaint)
        // 중심에서 바깥으로 흰색→투명 (채도)
        val radial = RadialGradient(
            cx, cy, radius,
            Color.HSVToColor(floatArrayOf(0f, 0f, brightness)), // 중심: 무채색(밝기 반영)
            Color.TRANSPARENT, Shader.TileMode.CLAMP
        )
        val p2 = Paint(Paint.ANTI_ALIAS_FLAG)
        p2.shader = radial
        canvas.drawCircle(cx, cy, radius, p2)

        // 선택 표시
        val selR = selSat * radius
        val rad = Math.toRadians(selHue.toDouble())
        val sx = cx + (selR * cos(rad)).toFloat()
        val sy = cy + (selR * sin(rad)).toFloat()
        canvas.drawCircle(sx, sy, 16f, selPaint)
        canvas.drawCircle(sx, sy, 16f, selPaintInner)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val dx = event.x - cx
                val dy = event.y - cy
                val dist = hypot(dx, dy)
                val r = dist.coerceAtMost(radius)
                selSat = (r / radius).coerceIn(0f, 1f)
                var deg = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                if (deg < 0) deg += 360f
                selHue = deg
                invalidate()
                emit()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun emit() { onColorChanged?.invoke(currentColor()) }
}
