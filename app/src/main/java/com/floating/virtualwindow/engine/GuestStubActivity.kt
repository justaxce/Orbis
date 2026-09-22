package com.floating.virtualwindow.engine

import android.app.Activity
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import com.floating.virtualwindow.R

class GuestStubActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val targetPackage = intent.getStringExtra("EXTRA_TARGET_PACKAGE") ?: "Guest App"

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(getColor(R.color.surface))
            setPadding(40, 40, 40, 40)
        }

        val tvTitle = TextView(this).apply {
            text = "Virtual Environment: $targetPackage"
            setTextColor(getColor(R.color.text_primary))
            textSize = 18f
            setPadding(0, 0, 0, 20)
        }

        val tvDesc = TextView(this).apply {
            text = "This guest activity is running inside your Virtual Display container on display #${display?.displayId ?: 0}."
            setTextColor(getColor(R.color.text_secondary))
            textSize = 13f
        }

        layout.addView(tvTitle)
        layout.addView(tvDesc)
        setContentView(layout)
    }
}
