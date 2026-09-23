package com.floating.virtualwindow.tools

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.floating.virtualwindow.R
import com.floating.virtualwindow.tools.calc.CalcHistoryAdapter
import com.floating.virtualwindow.tools.calc.CalcHistoryItem
import com.floating.virtualwindow.tools.calc.MathEvaluator

class FloatingCalculatorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val tvCalcExpression: TextView
    private val tvCalcResult: TextView
    private val hsvExpression: HorizontalScrollView

    private val btnModeStandard: TextView
    private val btnModeScientific: TextView
    private val btnModeHistory: TextView
    private val btnAngleUnit: TextView

    private val llKeypadContainer: LinearLayout
    private val llScientificPanel: LinearLayout
    private val llHistoryPanel: LinearLayout
    private val rvCalcHistory: RecyclerView
    private val tvHistoryEmpty: TextView
    private val btnClearHistory: Button

    private val historyAdapter: CalcHistoryAdapter
    private val historyList = mutableListOf<CalcHistoryItem>()

    private var currentExpression: String = ""
    private var isResultPromoted: Boolean = false

    init {
        val view = LayoutInflater.from(context).inflate(R.layout.view_floating_calculator, this, true)

        tvCalcExpression = view.findViewById(R.id.tvCalcExpression)
        tvCalcResult = view.findViewById(R.id.tvCalcResult)
        hsvExpression = view.findViewById(R.id.hsvExpression)

        btnModeStandard = view.findViewById(R.id.btnModeStandard)
        btnModeScientific = view.findViewById(R.id.btnModeScientific)
        btnModeHistory = view.findViewById(R.id.btnModeHistory)
        btnAngleUnit = view.findViewById(R.id.btnAngleUnit)

        llKeypadContainer = view.findViewById(R.id.llKeypadContainer)
        llScientificPanel = view.findViewById(R.id.llScientificPanel)
        llHistoryPanel = view.findViewById(R.id.llHistoryPanel)
        rvCalcHistory = view.findViewById(R.id.rvCalcHistory)
        tvHistoryEmpty = view.findViewById(R.id.tvHistoryEmpty)
        btnClearHistory = view.findViewById(R.id.btnClearHistory)

        historyAdapter = CalcHistoryAdapter { item ->
            // Tap history item -> insert its result
            currentExpression = item.result.replace(",", "")
            isResultPromoted = true
            updateDisplay()
            switchToStandardMode()
        }
        rvCalcHistory.layoutManager = LinearLayoutManager(context)
        rvCalcHistory.adapter = historyAdapter

        setupModeSwitchers()
        setupKeypad(view)
        setupDisplayCopy()
    }

    private fun setupModeSwitchers() {
        btnModeStandard.setOnClickListener {
            switchToStandardMode()
        }

        btnModeScientific.setOnClickListener {
            switchToScientificMode()
        }

        btnModeHistory.setOnClickListener {
            switchToHistoryMode()
        }

        btnAngleUnit.setOnClickListener {
            MathEvaluator.isDegreeMode = !MathEvaluator.isDegreeMode
            btnAngleUnit.text = if (MathEvaluator.isDegreeMode) "DEG" else "RAD"
            triggerLiveEvaluation()
        }
    }

    private fun switchToStandardMode() {
        highlightTab(btnModeStandard)
        llKeypadContainer.visibility = View.VISIBLE
        llScientificPanel.visibility = View.GONE
        llHistoryPanel.visibility = View.GONE
    }

    private fun switchToScientificMode() {
        highlightTab(btnModeScientific)
        llKeypadContainer.visibility = View.VISIBLE
        llScientificPanel.visibility = View.VISIBLE
        llHistoryPanel.visibility = View.GONE
    }

    private fun switchToHistoryMode() {
        highlightTab(btnModeHistory)
        llKeypadContainer.visibility = View.GONE
        llHistoryPanel.visibility = View.VISIBLE
        refreshHistoryView()
    }

    private fun highlightTab(selectedTab: TextView) {
        val tabs = listOf(btnModeStandard, btnModeScientific, btnModeHistory)
        for (tab in tabs) {
            if (tab == selectedTab) {
                tab.setBackgroundResource(R.drawable.bg_header)
                tab.setTextColor(context.getColor(R.color.accent))
            } else {
                tab.background = null
                tab.setTextColor(context.getColor(R.color.text_muted))
            }
        }
    }

    private fun refreshHistoryView() {
        historyAdapter.submitList(historyList.toList())
        if (historyList.isEmpty()) {
            tvHistoryEmpty.visibility = View.VISIBLE
            rvCalcHistory.visibility = View.GONE
        } else {
            tvHistoryEmpty.visibility = View.GONE
            rvCalcHistory.visibility = View.VISIBLE
        }
    }

    private fun setupKeypad(root: View) {
        // Number digits
        val digits = listOf(
            R.id.btnCalc0 to "0",
            R.id.btnCalc1 to "1",
            R.id.btnCalc2 to "2",
            R.id.btnCalc3 to "3",
            R.id.btnCalc4 to "4",
            R.id.btnCalc5 to "5",
            R.id.btnCalc6 to "6",
            R.id.btnCalc7 to "7",
            R.id.btnCalc8 to "8",
            R.id.btnCalc9 to "9",
            R.id.btnCalcDot to "."
        )

        for ((id, char) in digits) {
            root.findViewById<Button>(id)?.setOnClickListener { btn ->
                btn.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                appendInput(char)
            }
        }

        // Basic operators
        val basicOps = listOf(
            R.id.btnCalcAdd to " + ",
            R.id.btnCalcSub to " − ",
            R.id.btnCalcMul to " × ",
            R.id.btnCalcDiv to " ÷ ",
            R.id.btnCalcPercent to " % "
        )

        for ((id, op) in basicOps) {
            root.findViewById<Button>(id)?.setOnClickListener { btn ->
                btn.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                appendOperator(op)
            }
        }

        // Scientific functions
        val sciOps = listOf(
            R.id.btnCalcSin to "sin(",
            R.id.btnCalcCos to "cos(",
            R.id.btnCalcTan to "tan(",
            R.id.btnCalcLn to "ln(",
            R.id.btnCalcLog to "log(",
            R.id.btnCalcSqrt to "√(",
            R.id.btnCalcPower to "^",
            R.id.btnCalcPi to "π",
            R.id.btnCalcE to "e",
            R.id.btnCalcOpenParen to "(",
            R.id.btnCalcCloseParen to ")"
        )

        for ((id, token) in sciOps) {
            root.findViewById<Button>(id)?.setOnClickListener { btn ->
                btn.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                appendToken(token)
            }
        }

        // x² Square button
        root.findViewById<Button>(R.id.btnCalcSq)?.setOnClickListener { btn ->
            btn.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            appendToken("^2")
        }

        // Plus/Minus ±
        root.findViewById<Button>(R.id.btnCalcPlusMinus)?.setOnClickListener { btn ->
            btn.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            togglePlusMinus()
        }

        // Equals =
        root.findViewById<Button>(R.id.btnCalcEquals)?.setOnClickListener { btn ->
            btn.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            onEqualsPressed()
        }

        // Clear C
        root.findViewById<Button>(R.id.btnCalcClear)?.setOnClickListener { btn ->
            btn.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            currentExpression = ""
            isResultPromoted = false
            tvCalcExpression.text = ""
            tvCalcResult.text = "0"
        }

        // Backspace
        root.findViewById<Button>(R.id.btnCalcBackspace)?.setOnClickListener { btn ->
            btn.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            onBackspacePressed()
        }

        // Clear History button
        btnClearHistory.setOnClickListener {
            historyList.clear()
            refreshHistoryView()
        }
    }

    private fun appendInput(char: String) {
        if (isResultPromoted) {
            currentExpression = if (char == ".") "0." else char
            isResultPromoted = false
        } else {
            if (char == "." && currentExpression.endsWith(".")) return
            currentExpression += char
        }
        updateDisplay()
    }

    private fun appendOperator(op: String) {
        isResultPromoted = false
        if (currentExpression.isEmpty()) {
            if (op == " − ") {
                currentExpression = "-"
                updateDisplay()
            }
            return
        }

        val trimmed = currentExpression.trimEnd()
        if (trimmed.endsWith("+") || trimmed.endsWith("−") || trimmed.endsWith("×") || trimmed.endsWith("÷") || trimmed.endsWith("%")) {
            currentExpression = trimmed.dropLast(1).trimEnd() + op
        } else {
            currentExpression += op
        }
        updateDisplay()
    }

    private fun appendToken(token: String) {
        if (isResultPromoted && (token.startsWith("sin") || token.startsWith("cos") || token.startsWith("tan") || token.startsWith("√"))) {
            currentExpression = token
            isResultPromoted = false
        } else {
            currentExpression += token
            isResultPromoted = false
        }
        updateDisplay()
    }

    private fun togglePlusMinus() {
        if (currentExpression.isEmpty() || currentExpression == "0") return

        if (currentExpression.startsWith("(-") && currentExpression.endsWith(")")) {
            currentExpression = currentExpression.removeSurrounding("(-", ")")
        } else if (currentExpression.startsWith("-")) {
            currentExpression = currentExpression.removePrefix("-")
        } else {
            currentExpression = "-($currentExpression)"
        }
        updateDisplay()
    }

    private fun onBackspacePressed() {
        if (currentExpression.isNotEmpty()) {
            // If deleting an operator with spaces " + "
            if (currentExpression.endsWith(" ")) {
                currentExpression = currentExpression.trimEnd()
                if (currentExpression.isNotEmpty()) {
                    currentExpression = currentExpression.dropLast(1).trimEnd()
                }
            } else if (currentExpression.endsWith("sin(") || currentExpression.endsWith("cos(") || currentExpression.endsWith("tan(") || currentExpression.endsWith("log(") || currentExpression.endsWith("sqrt(")) {
                currentExpression = currentExpression.dropLast(4)
            } else if (currentExpression.endsWith("ln(")) {
                currentExpression = currentExpression.dropLast(3)
            } else if (currentExpression.endsWith("^2")) {
                currentExpression = currentExpression.dropLast(2)
            } else {
                currentExpression = currentExpression.dropLast(1)
            }
            isResultPromoted = false
            updateDisplay()
        }
    }

    private fun onEqualsPressed() {
        if (currentExpression.isBlank()) return

        val resultVal = MathEvaluator.evaluate(currentExpression)
        if (resultVal != null) {
            val formatted = MathEvaluator.formatResult(resultVal)

            // Save to history
            historyList.add(0, CalcHistoryItem(currentExpression, formatted))
            if (historyList.size > 30) historyList.removeAt(historyList.size - 1)

            tvCalcExpression.text = "$currentExpression ="
            tvCalcResult.text = formatted
            currentExpression = formatted.replace(",", "")
            isResultPromoted = true
        } else {
            tvCalcResult.text = "Error"
        }
    }

    private fun updateDisplay() {
        tvCalcExpression.text = currentExpression
        hsvExpression.post {
            hsvExpression.fullScroll(View.FOCUS_RIGHT)
        }
        triggerLiveEvaluation()
    }

    private fun triggerLiveEvaluation() {
        if (currentExpression.isBlank()) {
            tvCalcResult.text = "0"
            return
        }

        // Live preview
        val preview = MathEvaluator.evaluate(currentExpression)
        if (preview != null && !isResultPromoted) {
            tvCalcResult.text = MathEvaluator.formatResult(preview)
        }
    }

    private fun setupDisplayCopy() {
        findViewById<View>(R.id.llDisplayCard)?.setOnClickListener {
            val textToCopy = tvCalcResult.text.toString()
            if (textToCopy.isNotEmpty() && textToCopy != "0" && textToCopy != "Error") {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Calculator Result", textToCopy)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, "Result copied to clipboard", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
