package com.floating.virtualwindow.tools.calc

data class CalcHistoryItem(
    val expression: String,
    val result: String,
    val timestamp: Long = System.currentTimeMillis()
)
