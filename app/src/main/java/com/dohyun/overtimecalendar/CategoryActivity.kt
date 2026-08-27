package com.dohyun.overtimecalendar

import android.app.AlertDialog
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

class CategoryActivity : AppCompatActivity() {

    private lateinit var listContainer: LinearLayout
    private val emojiChoices = listOf("🕒", "📋", "🚽", "🍚", "💊", "🏃", "🛌", "🚗", "📌", "❤️", "⭐", "🔥")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_category)
        listContainer = findViewById(R.id.catList)

        findViewById<Button>(R.id.btnAddCat).setOnClickListener { showEdit(null) }
        renderList()
    }

    private fun renderList() {
        listContainer.removeAllViews()
        val cats = Prefs.getCategories(this)
        for (cat in cats) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(dp(8), dp(14), dp(8), dp(14))
            row.isClickable = true
            row.setBackgroundResource(R.drawable.cell_bg)

            // 색 동그라미
            val dot = View(this)
            val dotLp = LinearLayout.LayoutParams(dp(24), dp(24))
            dotLp.rightMargin = dp(12)
            dot.layoutParams = dotLp
            val d = GradientDrawable()
            d.shape = GradientDrawable.OVAL
            d.setColor(cat.color)
            dot.background = d
            row.addView(dot)

            val tv = TextView(this)
            tv.text = "${cat.emoji}  ${cat.name}" + if (cat.hasNumber) "  (숫자)" else ""
            tv.textSize = 16f
            val tvLp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            tv.layoutParams = tvLp
            row.addView(tv)

            row.setOnClickListener { showEdit(cat) }
            listContainer.addView(row)

            val div = View(this)
            div.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1))
            div.setBackgroundColor(0xFFEEEEEE.toInt())
            listContainer.addView(div)
        }
    }

    /** cat이 null이면 새로 추가, 아니면 수정 */
    private fun showEdit(cat: Category?) {
        val view = layoutInflater.inflate(R.layout.dialog_category_edit, null)
        val etName = view.findViewById<EditText>(R.id.etCatName)
        val etEmoji = view.findViewById<EditText>(R.id.etCatEmoji)
        val emojiPalette = view.findViewById<LinearLayout>(R.id.emojiPalette)
        val colorPalette = view.findViewById<LinearLayout>(R.id.colorPalette)
        val cbHasNumber = view.findViewById<CheckBox>(R.id.cbHasNumber)
        val etDefault = view.findViewById<EditText>(R.id.etCatDefault)
        val btnDelete = view.findViewById<Button>(R.id.btnCatDelete)

        var selectedColor = cat?.color ?: Prefs.PALETTE[0]

        if (cat != null) {
            etName.setText(cat.name)
            etEmoji.setText(cat.emoji)
            cbHasNumber.isChecked = cat.hasNumber
            if (cat.defaultValue > 0) etDefault.setText(fmt(cat.defaultValue))
        } else {
            cbHasNumber.isChecked = false
            btnDelete.visibility = View.GONE
        }

        // 이모지 팔레트
        for (em in emojiChoices) {
            val tv = TextView(this)
            tv.text = em
            tv.textSize = 22f
            tv.setPadding(dp(6), dp(4), dp(6), dp(4))
            tv.setOnClickListener { etEmoji.setText(em) }
            emojiPalette.addView(tv)
        }

        // 색 팔레트
        val dots = ArrayList<View>()
        fun refreshColorDots() {
            for ((i, dv) in dots.withIndex()) {
                val gd = GradientDrawable()
                gd.shape = GradientDrawable.OVAL
                gd.setColor(Prefs.PALETTE[i])
                if (Prefs.PALETTE[i] == selectedColor) gd.setStroke(dp(3), Color.BLACK)
                else gd.setStroke(dp(1), 0xFFCCCCCC.toInt())
                dv.background = gd
            }
        }
        for (color in Prefs.PALETTE) {
            val dot = View(this)
            val lp = LinearLayout.LayoutParams(dp(32), dp(32))
            lp.setMargins(dp(3), dp(3), dp(3), dp(3))
            dot.layoutParams = lp
            dot.setOnClickListener { selectedColor = color; refreshColorDots() }
            colorPalette.addView(dot)
            dots.add(dot)
        }
        refreshColorDots()

        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .create()

        view.findViewById<Button>(R.id.btnCatCancel).setOnClickListener { dialog.dismiss() }
        view.findViewById<Button>(R.id.btnCatSave).setOnClickListener {
            val name = etName.text.toString().trim()
            val emoji = etEmoji.text.toString().trim()
            if (name.isEmpty()) {
                Toast.makeText(this, "이름을 입력하세요", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val defVal = etDefault.text.toString().trim().toDoubleOrNull() ?: 0.0
            if (cat == null) {
                val newCat = Category(
                    id = "cat_" + System.currentTimeMillis(),
                    name = name, emoji = emoji, color = selectedColor,
                    hasNumber = cbHasNumber.isChecked, defaultValue = defVal
                )
                Prefs.addCategory(this, newCat)
            } else {
                Prefs.updateCategory(this, cat.copy(
                    name = name, emoji = emoji, color = selectedColor,
                    hasNumber = cbHasNumber.isChecked, defaultValue = defVal
                ))
            }
            CalendarWidgetProvider.updateAll(this)
            dialog.dismiss()
            renderList()
        }
        btnDelete.setOnClickListener {
            if (cat == null) return@setOnClickListener
            AlertDialog.Builder(this)
                .setMessage("'${cat.name}' 항목과 관련 기록을 모두 삭제할까요?")
                .setPositiveButton("삭제") { _, _ ->
                    Prefs.deleteCategory(this, cat.id)
                    CalendarWidgetProvider.updateAll(this)
                    dialog.dismiss()
                    renderList()
                }
                .setNegativeButton("취소", null)
                .show()
        }
        dialog.show()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun fmt(v: Double): String =
        if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()
}
