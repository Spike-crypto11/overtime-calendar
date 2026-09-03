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
    // 날짜 -> 대표 공휴일 이름 (연휴 대표일 계산용)
    private var holidayNames: Map<String, String> = emptyMap()

    private fun holidayNameOf(date: String): String? = holidayNames[date]

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val root: LinearLayout = v.findViewById(R.id.cellRoot)
        val tvDay: TextView = v.findViewById(R.id.tvDay)
        val tvHoliday: TextView = v.findViewById(R.id.tvHoliday)
        val tvEvent: TextView = v.findViewById(R.id.tvEvent)
        val tvLine1: TextView = v.findViewById(R.id.tvOvertime)
        val tvLine2: TextView = v.findViewById(R.id.tvSpecial)
    }

    fun update(newCells: List<CalendarCell>, newCats: List<Category>, holNames: Map<String, String>) {
        cells = newCells
        catMap = newCats.associateBy { it.id }
        holidayNames = holNames
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
            holder.tvEvent.visibility = View.GONE
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
            // 연휴(같은 이름 연속)면 가운데 날에만 이름 표시, 앞뒤는 빈칸(색만)
            val showName = if (topHol.kind == "holiday") {
                DateUtils.isNameAnchor(topHol.date, topHol.name) { d -> holidayNameOf(d) }
            } else true
            holder.tvHoliday.text = if (showName) topHol.name else ""
            holder.tvHoliday.setTextColor(Prefs.colorForKind(topHol.kind))
        } else {
            holder.tvHoliday.text = ""
        }

        // 일정 표시 (첫 일정을 색막대로, 여러 개면 +N)
        val evs = cell.events
        if (evs.isNotEmpty()) {
            val first = evs[0]
            val extra = if (evs.size > 1) " +${evs.size - 1}" else ""
            holder.tvEvent.text = first.title + extra
            holder.tvEvent.setBackgroundColor(first.color)
            holder.tvEvent.setTextColor(0xFFFFFFFF.toInt())
            holder.tvEvent.visibility = View.VISIBLE
        } else {
            holder.tvEvent.visibility = View.GONE
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
        val valStr = if (cat.hasNumber && rec.value > 0) fmt(rec.value) else ""
        // 표시 텍스트: 이모지 우선, 없으면 이름. 숫자 있으면 뒤에 붙임.
        val label = when {
            cat.emoji.isNotBlank() -> cat.emoji + (if (valStr.isNotEmpty()) " $valStr" else "")
            else -> cat.name + (if (valStr.isNotEmpty()) " $valStr" else "")
        }
        tv.text = label
        tv.textSize = if (cat.iconOnly && cat.emoji.isNotBlank()) 16f else 13f
        tv.setTextColor(cat.color)
    }

    private fun fmt(v: Double): String =
        if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()
}
