package com.dohyun.overtimecalendar

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

class CalendarAdapter(
    private var cells: List<CalendarCell>,
    private val categories: List<Category>,
    private val onClick: (CalendarCell) -> Unit
) : RecyclerView.Adapter<CalendarAdapter.VH>() {

    private var catMap: Map<String, Category> = categories.associateBy { it.id }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val root: LinearLayout = v.findViewById(R.id.cellRoot)
        val tvDay: TextView = v.findViewById(R.id.tvDay)
        val tvHoliday: TextView = v.findViewById(R.id.tvHoliday)
        val tvLine1: TextView = v.findViewById(R.id.tvOvertime)
        val tvLine2: TextView = v.findViewById(R.id.tvSpecial)
    }

    fun update(newCells: List<CalendarCell>, newCats: List<Category>) {
        cells = newCells
        catMap = newCats.associateBy { it.id }
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_day, parent, false)
        return VH(v)
    }

    override fun getItemCount(): Int = cells.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val cell = cells[position]
        val ctx = holder.itemView.context

        if (cell.day == 0) {
            holder.tvDay.text = ""
            holder.tvHoliday.text = ""
            holder.tvLine1.text = ""
            holder.tvLine2.text = ""
            holder.root.setBackgroundColor(0x00000000)
            holder.root.setOnClickListener(null)
            holder.root.isClickable = false
            return
        }

        holder.tvDay.text = cell.day.toString()

        // 공휴일이면 날짜 빨강, 아니면 요일 색
        val hasHoliday = cell.holidays.any { it.kind == "holiday" }
        val dayColor = when {
            hasHoliday -> R.color.sunday
            cell.weekdayIndex == 0 -> R.color.sunday
            cell.weekdayIndex == 6 -> R.color.saturday
            else -> R.color.weekday_text
        }
        holder.tvDay.setTextColor(ContextCompat.getColor(ctx, dayColor))

        // 특일 이름 표시 (공휴일 > 절기 > 기념일 순, 첫 번째만)
        val topHol = cell.holidays.firstOrNull { it.kind == "holiday" }
            ?: cell.holidays.firstOrNull { it.kind == "term" }
            ?: cell.holidays.firstOrNull()
        if (topHol != null) {
            holder.tvHoliday.text = topHol.name
            holder.tvHoliday.setTextColor(Prefs.colorForKind(topHol.kind))
        } else {
            holder.tvHoliday.text = ""
        }
        holder.root.setBackgroundResource(
            if (cell.isToday) R.drawable.cell_bg_today else R.drawable.cell_bg
        )

        // 앱 화면: 최대 2줄로 항목 표시 (이모지+값). 색은 카테고리 색.
        val recs = cell.records
        val line1 = if (recs.isNotEmpty()) recs[0] else null
        val line2 = if (recs.size > 1) recs[1] else null

        bindLine(holder.tvLine1, line1)
        // 3개 이상이면 두번째 줄에 "+N" 표시
        if (recs.size > 2) {
            holder.tvLine2.text = "+${recs.size - 1}"
            holder.tvLine2.setTextColor(0xFF888888.toInt())
        } else {
            bindLine(holder.tvLine2, line2)
        }

        holder.root.isClickable = true
        holder.root.setOnClickListener { onClick(cell) }
    }

    private fun bindLine(tv: TextView, rec: DayRecord?) {
        if (rec == null) { tv.text = ""; return }
        val cat = catMap[rec.categoryId]
        if (cat == null) { tv.text = ""; return }
        val valStr = if (cat.hasNumber && rec.value > 0) " ${fmt(rec.value)}" else ""
        if (cat.iconOnly) {
            tv.text = "${cat.emoji}$valStr"
            tv.textSize = 16f
        } else {
            tv.text = "${cat.emoji}$valStr"
            tv.textSize = 15f
        }
        tv.setTextColor(cat.color)
    }

    private fun fmt(v: Double): String =
        if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()
}
