package com.sigil.client

import android.app.Activity
import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NfcAdapter
import android.nfc.NdefRecord
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.sigil.client.data.SessionStore
import com.sigil.client.sync.SyncWorker
import java.text.DateFormat
import java.util.Date

class MainActivity : Activity() {
    private lateinit var sessionStore: SessionStore
    private lateinit var statusView: TextView
    private lateinit var sessionView: TextView
    private lateinit var outboxView: TextView
    private lateinit var subjectField: EditText
    private lateinit var chapterField: EditText
    private lateinit var notesField: EditText
    private var lastNfcTapMillis = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sessionStore = SessionStore(applicationContext)
        setContentView(buildContentView())
        handleNfcIntent(intent)
        refreshStatus()
        SyncWorker.enqueue(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (handleNfcIntent(intent)) refreshStatus()
    }

    private fun buildContentView(): View {
        val scrollView = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(28), dp(24), dp(32))
        }
        scrollView.addView(content)

        content.addView(label("SIGIL"))
        content.addView(title("Study session"))
        content.addView(body("Tap the NFC tag once to start. Tap it again to finish and queue the completed session."))

        statusView = body("")
        statusView.setPadding(0, dp(22), 0, dp(4))
        content.addView(statusView)

        sessionView = body("")
        content.addView(sessionView)

        subjectField = field("Subject (optional)")
        chapterField = field("Chapter (optional)")
        notesField = field("Notes (optional)")
        content.addView(subjectField)
        content.addView(chapterField)
        content.addView(notesField)

        val syncButton = Button(this).apply {
            text = "Sync pending sessions"
            setOnClickListener {
                SyncWorker.enqueue(this@MainActivity)
                setStatus("Sync queued.")
            }
        }
        content.addView(syncButton, marginParams(top = 20))

        outboxView = body("")
        outboxView.setPadding(0, dp(20), 0, 0)
        content.addView(outboxView)

        val apiView = body("API: " + BuildConfig.API_BASE_URL)
        apiView.setTextSize(12f)
        apiView.setTextColor(0xff666660.toInt())
        apiView.setPadding(0, dp(12), 0, 0)
        content.addView(apiView)

        return scrollView
    }

    private fun handleNfcIntent(intent: Intent?): Boolean {
        intent ?: return false
        val isCustomUri = intent.action == Intent.ACTION_VIEW &&
            intent.data?.scheme == "sigil" &&
            intent.data?.host == "tap"
        val isNdefTag = intent.action == NfcAdapter.ACTION_NDEF_DISCOVERED &&
            containsSigilRecord(intent)

        if (!isCustomUri && !isNdefTag) return false

        val now = System.currentTimeMillis()
        if (now - lastNfcTapMillis < 1_500) return true
        lastNfcTapMillis = now
        toggleSession()
        return true
    }

    private fun containsSigilRecord(intent: Intent): Boolean {
        val messages = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableArrayExtra(
                NfcAdapter.EXTRA_NDEF_MESSAGES,
                NdefMessage::class.java
            ) ?: emptyArray()
        } else {
            @Suppress("DEPRECATION")
            (intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)
                ?.filterIsInstance<NdefMessage>()
                ?.toTypedArray()
                ?: emptyArray())
        }

        return messages.any { message ->
            message.records.any { record ->
                record.toUri()?.toString() == "sigil://tap" ||
                    String(record.payload, Charsets.UTF_8).contains("SIGIL_TAP")
            }
        }
    }

    private fun toggleSession() {
        if (sessionStore.activeStartedAtMillis() == null) {
            sessionStore.startSession()
            setStatus("Session started. Tap the tag again when you finish.")
        } else {
            val completed = sessionStore.completeSession(
                subject = subjectField.text.toString(),
                chapter = chapterField.text.toString(),
                notes = notesField.text.toString()
            )
            if (completed == null) {
                setStatus("Could not finish the session. Please try again.")
            } else {
                SyncWorker.enqueue(this)
                setStatus("Session saved locally. Sync will retry automatically.")
                Toast.makeText(this, "Session complete", Toast.LENGTH_SHORT).show()
            }
        }
        refreshStatus()
    }

    private fun refreshStatus() {
        val activeStartedAt = sessionStore.activeStartedAtMillis()
        if (activeStartedAt == null) {
            sessionView.text = "No active session."
        } else {
            val started = DateFormat.getDateTimeInstance(
                DateFormat.MEDIUM,
                DateFormat.SHORT
            ).format(Date(activeStartedAt))
            sessionView.text = "Active since $started"
        }
        outboxView.text = sessionStore.pendingSessions().size.toString() +
            " completed session(s) waiting to sync."
    }

    private fun setStatus(message: String) {
        statusView.text = message
        Handler(Looper.getMainLooper()).postDelayed({ refreshStatus() }, 100)
    }

    private fun label(text: String) = TextView(this).apply {
        this.text = text
        textSize = 12f
        setTextColor(0xff3f5d4b.toInt())
        gravity = Gravity.START
    }

    private fun title(text: String) = TextView(this).apply {
        this.text = text
        textSize = 34f
        setTextColor(0xff1b1b1a.toInt())
        setPadding(0, dp(6), 0, dp(4))
    }

    private fun body(text: String) = TextView(this).apply {
        this.text = text
        textSize = 16f
        setTextColor(0xff555550.toInt())
    }

    private fun field(hintText: String) = EditText(this).apply {
        hint = hintText
        textSize = 16f
        setSingleLine(false)
        minLines = if (hintText == "Notes (optional)") 3 else 1
        gravity = Gravity.TOP
        layoutParams = marginParams(top = 10)
    }

    private fun marginParams(top: Int): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = dp(top)
        }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
