package com.dohyun.overtimecalendar

import android.app.Activity
import android.os.Bundle
import android.text.InputType
import android.widget.*
import java.math.BigDecimal

/** Optional additional screen; existing calendar, widget and settings controls are retained. */
class PayrollActivity : Activity() {
    private lateinit var body: LinearLayout
    private var month = DateUtils.today().take(7)
    private val labels = mapOf("base" to "기본급", "overtime" to "잔업수당", "special" to "특근수당",
        "position" to "직책수당", "other" to "기타 지급", "incomeTax" to "소득세", "localIncomeTax" to "지방소득세",
        "nationalPension" to "국민연금", "healthInsurance" to "건강보험", "longTermCare" to "장기요양보험",
        "employmentInsurance" to "고용보험", "insuranceAdjustment" to "보험 정산", "deductionOther" to "기타 공제")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        month = savedInstanceState?.getString("month") ?: month
        show()
    }
    override fun onSaveInstanceState(outState: Bundle) { outState.putString("month", month); super.onSaveInstanceState(outState) }
    private fun text(value: String) { body.addView(TextView(this).apply { text = value; textSize = 15f; setPadding(0, 12, 0, 4) }) }
    private fun input(label: String, value: String, numeric: Boolean = true): EditText {
        text(label)
        return EditText(this).apply {
            inputType = if (numeric) InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL else InputType.TYPE_CLASS_TEXT
            setText(value); hint = if (numeric) "미확정은 비워 두세요 (0원은 0)" else ""
            body.addView(this)
        }
    }
    private fun button(label: String, action: () -> Unit) { body.addView(Button(this).apply { text = label; setOnClickListener { action() } }) }
    private fun decimal(input: EditText): BigDecimal? {
        val value = input.text.toString().trim()
        return if (value.isEmpty()) null else value.toBigDecimal().also { require(it.signum() >= 0) }
    }
    private fun show() {
        val scroll = ScrollView(this)
        body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 24, 24, 24) }
        scroll.addView(body); setContentView(scroll)
        text("월별 집계 · 급여 검증")
        val monthInput = input("대상 월 (yyyy-MM)", month, false)
        button("이 월 불러오기 (입력 중인 값은 저장 후 이동)") {
            val next = monthInput.text.toString().trim()
            if (next.matches(Regex("\\d{4}-(0[1-9]|1[0-2])"))) { month = next; show() }
            else Toast.makeText(this, "예: 2026-10", Toast.LENGTH_SHORT).show()
        }
        val rules = PayrollStore.rules(this, month)
        val summary = PayrollEngine.summarize(month, Prefs.getAllRecords(this), rules)
        fun amount(v: Any?) = v?.toString() ?: "미확정"
        text("로컬 기록 집계 (급여명세서 확정값이 아닙니다)\n" +
            "출근·반차 기록일 ${summary.recordedDays}일 / 반차 ${summary.halfDays}일\n" +
            "회사 근무일수 ${amount(summary.companyDays)} / 기본시간 ${amount(summary.basicHours)}\n" +
            "주휴 ${amount(summary.weeklyCount)}회 / 주차수당 ${amount(summary.weeklyAllowance)}원\n" +
            "잔업 ${summary.overtimeHours}시간 / 특근 ${summary.specialHours}시간\n" +
            "예상 세전 ${amount(summary.gross)}원 / 실수령 ${amount(summary.net)}원\n" + summary.warnings.joinToString("\n"))
        text(PayrollStore.status(this))
        body.addView(CheckBox(this).apply {
            text = "서버 월별 집계 기능 배포 확인 · 자동 전송 사용"
            isChecked = PayrollStore.enabled(this@PayrollActivity)
            setOnCheckedChangeListener { _, checked ->
                PayrollStore.setEnabled(this@PayrollActivity, checked)
                if (checked) PayrollSync.flush(this@PayrollActivity)
            }
        })
        button("월별 집계 재전송") { PayrollSync.scheduleMonth(this, month); Toast.makeText(this, "전송 요청됨. 잠시 후 다시 확인하세요.", Toast.LENGTH_SHORT).show() }
        text("항목 역할 (잔업·특근만으로 출근을 추정하지 않습니다)")
        val roleNames = arrayOf("집계 제외", "출근", "반차", "잔업 시간", "특근 시간", "확인된 주휴 1회 표시")
        text("미등록 항목도 원본 ID와 기록일로 표시합니다. 달력에서 의미를 확인한 뒤 역할을 선택하세요.")
        val categoryLabels = PayrollCategories.labels(month,
            Prefs.getCategories(this).associate { it.id to it.name }, Prefs.getAllRecords(this), rules.roles)
        val roleInputs = categoryLabels.mapValues { (id, label) ->
            text("$label ($id)")
            Spinner(this).apply {
                adapter = ArrayAdapter(this@PayrollActivity, android.R.layout.simple_spinner_dropdown_item, roleNames)
                setSelection((rules.roles[id] ?: PayrollRole.IGNORE).ordinal); body.addView(this)
            }
        }
        val dayHours = input("확인된 출근 1일 기본근로시간", rules.hoursPerDay?.toPlainString() ?: "")
        val halfHours = input("확인된 반차 1일 실제 기본근로시간", rules.hoursPerHalfDay?.toPlainString() ?: "")
        val useDays = CheckBox(this).apply { text = "회사 근무일수 = 출근·반차 기록일수임을 확인함"; isChecked = rules.companyDaysUseRecordedAttendance; body.addView(this) }
        val useWeeks = CheckBox(this).apply { text = "주휴 표시 1개 = 회사 인정 주휴 1회임을 확인함"; isChecked = rules.weeklyMarkersConfirmed; body.addView(this) }
        val perWeek = input("확인된 주휴 1회당 주차수당 (원)", rules.allowancePerWeek?.toPlainString() ?: "")
        val evidence = input("적용 근거·급여명세서 월 (미확인이면 비워 두세요)", rules.evidence, false)
        text("월별 예상 지급액 · 공제액\n확인된 금액만 입력하세요. 주차수당은 위 규칙으로 별도 합산합니다. 세율·보험 요율 자동 계산은 아직 설정되지 않았습니다.")
        val earningInputs = (PayrollEngine.earningKeys + rules.earnings.keys).distinct().associateWith {
            input(labels[it] ?: it, rules.earnings[it]?.toPlainString() ?: "")
        }
        val deductionInputs = (PayrollEngine.deductionKeys + rules.deductions.keys).distinct().associateWith {
            input(if (it == "other") labels.getValue("deductionOther") else labels[it] ?: it,
                rules.deductions[it]?.toPlainString() ?: "")
        }
        button("이 월의 규칙·금액 저장") {
            try {
                val updated = PayrollRules(rules.roles + roleInputs.mapValues { PayrollRole.values()[it.value.selectedItemPosition] },
                    decimal(dayHours), decimal(halfHours), useDays.isChecked, useWeeks.isChecked,
                    decimal(perWeek), evidence.text.toString().trim(), earningInputs.mapValues { decimal(it.value) },
                    deductionInputs.mapValues { decimal(it.value) })
                PayrollEngine.summarize(month, Prefs.getAllRecords(this), updated)
                PayrollStore.saveRules(this, month, updated); PayrollSync.flush(this); show()
            } catch (_: Exception) { Toast.makeText(this, "금액·시간에는 0 이상의 숫자를 입력하세요.", Toast.LENGTH_LONG).show() }
        }
        button("닫기") { finish() }
    }
}
