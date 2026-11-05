package com.sigil.client

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.TextView
import android.widget.LinearLayout
import android.view.View
import com.sigil.client.data.SessionStore

class SuccessActivity : Activity() {
    private lateinit var sessionStore: SessionStore
    private lateinit var statusText: TextView
    private lateinit var debugText: TextView
    private var targetSessionId: String? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sessionStore = SessionStore(applicationContext)
        targetSessionId = intent.getStringExtra("session_id")

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(0xffF5F5F5.toInt())
            setPadding(dp(32), dp(32), dp(32), dp(32))
        }

        val title = TextView(this).apply {
            text = "Request submitted"
            textSize = 28f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(24))
        }
        layout.addView(title)

        statusText = TextView(this).apply {
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(40))
        }
        layout.addView(statusText)

        val debugLabel = TextView(this).apply {
            text = "DEBUG INFO"
            textSize = 12f
            setTextColor(0xff999999.toInt())
            gravity = Gravity.START
        }
        layout.addView(debugLabel)

        debugText = TextView(this).apply {
            textSize = 12f
            setTextColor(0xff666666.toInt())
            gravity = Gravity.START
            setPadding(0, dp(8), 0, 0)
        }
        layout.addView(debugText)

        setContentView(layout)
        
        startPolling()
    }

    private fun startPolling() {
        handler.post(object : Runnable {
            override fun run() {
                updateStatus()
                handler.postDelayed(this, 2000)
            }
        })
    }

    private fun updateStatus() {
        val pending = sessionStore.pendingSessions()
        val isSynced = targetSessionId == null || pending.none { it.clientSessionId == targetSessionId }
        
        statusText.text = if (isSynced) {
            "✅ Session Synced"
        } else {
            "⏳ Syncing..."
        }
        statusText.setTextColor(if (isSynced) 0xff2E7D32.toInt() else 0xffEF6C00.toInt())

        val debugInfo = buildString {
            appendLine("Target ID: ${targetSessionId ?: "N/A"}")
            appendLine("Pending Count: ${pending.size}")
            appendLine("API: ${com.sigil.client.BuildConfig.API_BASE_URL}")
            appendLine("Status: ${if (isSynced) "Synced" else "Pending"}")
        }
        debugText.text = debugInfo
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}
