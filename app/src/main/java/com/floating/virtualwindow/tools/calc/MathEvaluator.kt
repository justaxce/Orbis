package com.floating.virtualwindow.tools.calc

import java.util.ArrayDeque
import kotlin.math.*

object MathEvaluator {

    var isDegreeMode: Boolean = true

    fun evaluate(expression: String): Double? {
        if (expression.isBlank()) return null

        try {
            val sanitized = sanitize(expression)
            val tokens = tokenize(sanitized)
            val rpn = toRpn(tokens)
            return evaluateRpn(rpn)
        } catch (e: Exception) {
            return null
        }
    }

    private fun sanitize(expr: String): String {
        return expr
            .replace("×", "*")
            .replace("−", "-")
            .replace("÷", "/")
            .replace("π", "PI")
            .replace("√", "sqrt")
            .replace(" ", "")
    }

    private fun tokenize(expr: String): List<String> {
        val tokens = mutableListOf<String>()
        var i = 0
        val len = expr.length

        while (i < len) {
            val c = expr[i]

            // Numbers (including decimal point)
            if (c.isDigit() || c == '.') {
                val sb = StringBuilder()
                while (i < len && (expr[i].isDigit() || expr[i] == '.')) {
                    sb.append(expr[i])
                    i++
                }
                tokens.add(sb.toString())
                continue
            }

            // Word identifiers (functions / constants: sin, cos, tan, sqrt, ln, log, PI, E)
            if (c.isLetter()) {
                val sb = StringBuilder()
                while (i < len && expr[i].isLetter()) {
                    sb.append(expr[i])
                    i++
                }
                val word = sb.toString()
                tokens.add(word)
                continue
            }

            // Unary minus: if '-' is at start or preceded by an operator or '('
            if (c == '-') {
                val prev = tokens.lastOrNull()
                val isUnary = prev == null || prev in listOf("+", "-", "*", "/", "^", "(", "%")
                if (isUnary) {
                    // Attach to the following number if next is digit
                    if (i + 1 < len && (expr[i + 1].isDigit() || expr[i + 1] == '.')) {
                        val sb = StringBuilder("-")
                        i++
                        while (i < len && (expr[i].isDigit() || expr[i] == '.')) {
                            sb.append(expr[i])
                            i++
                        }
                        tokens.add(sb.toString())
                        continue
                    } else {
                        // Unary negate token
                        tokens.add("neg")
                        i++
                        continue
                    }
                }
            }

            // Single-char operators
            if (c in listOf('+', '-', '*', '/', '%', '^', '(', ')')) {
                tokens.add(c.toString())
                i++
                continue
            }

            i++
        }

        // Handle implicit multiplication (e.g. 5(3) -> 5 * (3), 2PI -> 2 * PI, (2)(3) -> (2) * (3))
        val withImplicitMul = mutableListOf<String>()
        for (idx in tokens.indices) {
            val current = tokens[idx]
            if (idx > 0) {
                val prev = tokens[idx - 1]
                val prevIsNumOrRightParen = prev.toDoubleOrNull() != null || prev == ")" || prev == "PI" || prev == "E"
                val currIsFuncOrLeftParenOrConst = current in listOf("sin", "cos", "tan", "sqrt", "ln", "log", "PI", "E", "(")
                if (prevIsNumOrRightParen && currIsFuncOrLeftParenOrConst) {
                    withImplicitMul.add("*")
                }
            }
            withImplicitMul.add(current)
        }

        return withImplicitMul
    }

    private fun precedence(op: String): Int {
        return when (op) {
            "+", "-" -> 1
            "*", "/", "%" -> 2
            "^" -> 3
            "neg" -> 4
            else -> 0
        }
    }

    private fun isRightAssociative(op: String): Boolean = op == "^" || op == "neg"

    private fun isFunction(token: String): Boolean {
        return token in listOf("sin", "cos", "tan", "sqrt", "ln", "log")
    }

    private fun toRpn(tokens: List<String>): List<String> {
        val output = mutableListOf<String>()
        val opStack = ArrayDeque<String>()

        for (token in tokens) {
            val num = token.toDoubleOrNull()
            if (num != null) {
                output.add(token)
            } else if (token == "PI") {
                output.add(Math.PI.toString())
            } else if (token == "E") {
                output.add(Math.E.toString())
            } else if (isFunction(token)) {
                opStack.push(token)
            } else if (token == "(") {
                opStack.push(token)
            } else if (token == ")") {
                while (opStack.isNotEmpty() && opStack.peek() != "(") {
                    output.add(opStack.pop())
                }
                if (opStack.isNotEmpty() && opStack.peek() == "(") {
                    opStack.pop() // discard '('
                }
                if (opStack.isNotEmpty() && isFunction(opStack.peek() ?: "")) {
                    output.add(opStack.pop())
                }
            } else {
                // Operator
                while (opStack.isNotEmpty() && opStack.peek() != "(") {
                    val top = opStack.peek() ?: ""
                    val p1 = precedence(token)
                    val p2 = precedence(top)
                    if ((!isRightAssociative(token) && p1 <= p2) || (isRightAssociative(token) && p1 < p2)) {
                        output.add(opStack.pop())
                    } else {
                        break
                    }
                }
                opStack.push(token)
            }
        }

        while (opStack.isNotEmpty()) {
            val top = opStack.pop()
            if (top != "(" && top != ")") {
                output.add(top)
            }
        }

        return output
    }

    private fun evaluateRpn(rpn: List<String>): Double? {
        val stack = ArrayDeque<Double>()

        for (token in rpn) {
            val num = token.toDoubleOrNull()
            if (num != null) {
                stack.push(num)
            } else if (token == "neg") {
                if (stack.isEmpty()) return null
                stack.push(-stack.pop())
            } else if (isFunction(token)) {
                if (stack.isEmpty()) return null
                val arg = stack.pop()
                val result = when (token) {
                    "sin" -> {
                        val angle = if (isDegreeMode) Math.toRadians(arg) else arg
                        sin(angle)
                    }
                    "cos" -> {
                        val angle = if (isDegreeMode) Math.toRadians(arg) else arg
                        cos(angle)
                    }
                    "tan" -> {
                        val angle = if (isDegreeMode) Math.toRadians(arg) else arg
                        tan(angle)
                    }
                    "sqrt" -> if (arg >= 0) sqrt(arg) else return null
                    "ln" -> if (arg > 0) ln(arg) else return null
                    "log" -> if (arg > 0) log10(arg) else return null
                    else -> return null
                }
                stack.push(result)
            } else {
                // Binary operator
                if (stack.size < 2) return null
                val b = stack.pop()
                val a = stack.pop()
                val res = when (token) {
                    "+" -> a + b
                    "-" -> a - b
                    "*" -> a * b
                    "/" -> if (b != 0.0) a / b else return null
                    "%" -> a % b
                    "^" -> a.pow(b)
                    else -> return null
                }
                stack.push(res)
            }
        }

        return if (stack.size == 1) stack.pop() else null
    }

    fun formatResult(value: Double): String {
        if (value.isNaN() || value.isInfinite()) return "Error"

        // If integer value
        if (value == value.toLong().toDouble() && abs(value) < 1e15) {
            return String.format("%,d", value.toLong())
        }

        // Floating point formatting with max 6 decimal places, removing trailing zeros
        val str = String.format("%.6f", value).trimEnd('0').trimEnd('.')
        return str
    }
}
