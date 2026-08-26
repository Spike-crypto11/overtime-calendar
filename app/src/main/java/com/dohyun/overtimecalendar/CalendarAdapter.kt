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
    private val onClick: (CalendarCell) -> Unit
) : RecyclerView.Adapter<CalendarAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val root: LinearLayout = v.findViewById(R.id.cellRoot)
        val tvDay: TextView = v.findViewById(R.id.tvDay)
        val tvOvertime: TextView = v.findViewById(R.id.tvOvertime)
        val tvSpecial: TextView = v.findViewById(R.id.tvSpecial)
    }

    fun update(newCells: List<CalendarCell>) {
        cells = newCells
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
            // 빈 칸
            holder.tvDay.text = ""
            holder.tvOvertime.text = ""
            holder.tvSpecial.text = ""
            holder.root.setBackgroundColor(0x00000000)
            holder.root.setOnClickListener(null)
            holder.root.isClickable = false
            return
        }

        holder.tvDay.text = cell.day.toString()

        // 요일 색상
        val dayColor = when (cell.weekdayIndex) {
            0 -> R.color.sunday
            6 -> R.color.saturday
            else -> R.color.weekday_text
        }
        holder.tvDay.setTextColor(ContextCompat.getColor(ctx, dayColor))

        // 배경 (오늘 강조)
        holder.root.setBackgroundResource(
            if (cell.isToday) R.drawable.cell_bg_today else R.drawable.cell_bg
        )

        // 값 표시
        val e = cell.entry
        holder.tvOvertime.text = if (e != null && e.overtime > 0) fmt(e.overtime) else ""
        holder.tvSpecial.text = if (e != null && e.special > 0) fmt(e.special) else ""

        holder.root.isClickable = true
        holder.root.setOnClickListener { onClick(cell) }
    }

    private fun fmt(v: Double): String =
        if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()
}
