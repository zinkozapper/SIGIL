package com.sigil.client

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.widget.TextView
import android.widget.LinearLayout
import android.view.View
import android.widget.Button
import android.content.Intent

class SuccessActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(0xffF5F5F5.toInt())
        }

        val text = TextView(this).apply {
            text = "Request submitted"
            textSize = 28f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 20)
        }
        layout.addView(text)

        val backButton = Button(this).apply {
            text = "Back"
            setOnClickListener {
                finish()
            }
        }
        layout.addView(backButton)

        setContentView(layout)
    }
}
