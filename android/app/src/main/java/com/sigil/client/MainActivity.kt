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
import android.text.InputType
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
import java.net.URI
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var sessionStore: SessionStore
    private lateinit var mainLayout: LinearLayout
    private lateinit var statusText: TextView
    private lateinit var timerText: TextView
    private lateinit var cancelButton: Button
    private lateinit var surveyContainer: LinearLayout
    private lateinit var subjectField: EditText
    private lateinit var chapterField: EditText
    private lateinit var notesField: EditText
    private lateinit var outboxView: TextView
    private var lastNfcTapMillis = 0L
    private val timerHandler = Handler(Looper.getMainLooper())
    private val timerUpdate = object : Runnable {
        override fun run() {
            updateTimer()
            timerHandler.postDelayed(this, 1_000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sessionStore = SessionStore(applicationContext)
        setContentView(buildContentView())
        handleNfcIntent(intent)
        updateUI()
        SyncWorker.enqueue(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (handleNfcIntent(intent)) updateUI()
    }

    override fun onResume() {
        super.onResume()
        updateUI()
        timerHandler.post(timerUpdate)
    }

    override fun onPause() {
        timerHandler.removeCallbacks(timerUpdate)
        super.onPause()
    }

    private fun buildContentView(): View {
        val scrollView = ScrollView(this)
        mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(40), dp(24), dp(40))
        }
        scrollView.addView(mainLayout)

        statusText = TextView(this).apply {
            textSize = 34f
            gravity = Gravity.CENTER
            setPadding(0, dp(20), 0, dp(20))
        }
        mainLayout.addView(statusText)

        timerText = TextView(this).apply {
            textSize = 24f
            gravity = Gravity.CENTER
        }
        mainLayout.addView(timerText)

        cancelButton = Button(this).apply {
            text = "Cancel"
            setOnClickListener {
                sessionStore.cancelSession()
                updateUI()
            }
        }
        mainLayout.addView(cancelButton)

        surveyContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        
        subjectField = field("Subject (optional)")
        chapterField = field("Chapter (optional)")
        notesField = field("Notes (optional)")
        
        surveyContainer.addView(subjectField)
        surveyContainer.addView(chapterField)
        surveyContainer.addView(notesField)

        val submitButton = Button(this).apply {
            text = "Submit"
            setOnClickListener {
                val completed = sessionStore.completeSession(
                    subject = subjectField.text.toString(),
                    chapter = chapterField.text.toString(),
                    notes = notesField.text.toString()
                )
                if (completed == null) {
                    Toast.makeText(this@MainActivity, "No active session to complete.", Toast.LENGTH_SHORT).show()
                } else {
                    SyncWorker.enqueue(this@MainActivity)
                    val intent = Intent(this@MainActivity, SuccessActivity::class.java).apply {
                        putExtra("session_id", completed.clientSessionId)
                    }
                    startActivity(intent)
                }
                updateUI()
            }
        }
        surveyContainer.addView(submitButton, marginParams(top = 20))
        mainLayout.addView(surveyContainer)

        val savedServer = sessionStore.apiBaseUrl()?.let { URI(it) }
        val serverField = field("Server IP or URL").apply {
            setSingleLine(true)
            setText(savedServer?.let { "${it.scheme}://${it.host}" }.orEmpty())
        }
        val portField = field("Port").apply {
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(savedServer?.port?.takeIf { it != -1 }?.toString().orEmpty())
        }
        val serverRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(serverField, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2f))
            addView(portField, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        mainLayout.addView(serverRow, marginParams(top = 30))
        mainLayout.addView(Button(this).apply {
            text = "Save sync address"
            setOnClickListener {
                val url = normalizeServerUrl(serverField.text.toString(), portField.text.toString())
                if (url == null) {
                    portField.error = "Enter a valid address and port (1–65535)"
                } else {
                    sessionStore.setApiBaseUrl(url)
                    SyncWorker.enqueue(this@MainActivity)
                    Toast.makeText(this@MainActivity, "Sync address saved.", Toast.LENGTH_SHORT).show()
                }
            }
        })

        val syncButton = Button(this).apply {
            text = "Sync pending sessions"
            setOnClickListener {
                SyncWorker.enqueue(this@MainActivity)
                startActivity(Intent(this@MainActivity, SuccessActivity::class.java))
            }
        }
        mainLayout.addView(syncButton, marginParams(top = 40))

        outboxView = TextView(this).apply {
            textSize = 14f
            setTextColor(0xff666660.toInt())
            gravity = Gravity.CENTER
            setPadding(0, dp(20), 0, 0)
        }
        mainLayout.addView(outboxView)

        return scrollView
    }

    private fun updateUI() {
        val activeStartedAt = sessionStore.activeStartedAtMillis()
        val stoppedAt = sessionStore.stoppedAtMillis()

        val isStarted = activeStartedAt != null && stoppedAt == null
        cancelButton.visibility = if (isStarted) View.VISIBLE else View.GONE
        updateTimer()

        if (activeStartedAt == null) {
            mainLayout.setBackgroundColor(0xffFAFAFA.toInt())
            statusText.text = "Tap your SIGIL tag to start a session"
            statusText.setTextColor(0xff333333.toInt())
            surveyContainer.visibility = View.GONE
        } else if (isStarted) {
            mainLayout.setBackgroundColor(0xffE8F5E9.toInt()) // Light Green
            statusText.text = "Session Started"
            statusText.setTextColor(0xff2E7D32.toInt()) // Dark Green
            surveyContainer.visibility = View.GONE
        } else {
            mainLayout.setBackgroundColor(0xffFFEBEE.toInt()) // Light Red
            statusText.text = "Session stopped"
            statusText.setTextColor(0xffC62828.toInt()) // Dark Red
            surveyContainer.visibility = View.VISIBLE
        }

        outboxView.text = sessionStore.pendingSessions().size.toString() +
            " completed session(s) waiting to sync."
    }

    private fun updateTimer() {
        val elapsed = sessionStore.elapsedSessionMillis()
        timerText.visibility = if (elapsed == null) View.GONE else View.VISIBLE
        if (elapsed != null) {
            val seconds = elapsed / 1_000
            timerText.text = String.format(Locale.getDefault(), "%02d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
        }
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

    private fun toggleSession() {
        val activeStartedAt = sessionStore.activeStartedAtMillis()
        val stoppedAt = sessionStore.stoppedAtMillis()

        if (activeStartedAt == null || sessionStore.stoppedSessionExpired()) {
            sessionStore.startSession()
            subjectField.text.clear()
            chapterField.text.clear()
            notesField.text.clear()
            Toast.makeText(this, "Session started", Toast.LENGTH_SHORT).show()
        } else if (stoppedAt == null) {
            sessionStore.markStoppedAt()
            Toast.makeText(this, "Session stopped", Toast.LENGTH_SHORT).show()
        } else {
            val completed = sessionStore.completeSession(
                subject = subjectField.text.toString(),
                chapter = chapterField.text.toString(),
                notes = notesField.text.toString()
            )
            if (completed == null) {
                Toast.makeText(this, "Could not complete session", Toast.LENGTH_SHORT).show()
            } else {
                SyncWorker.enqueue(this)
                val intent = Intent(this, SuccessActivity::class.java).apply {
                    putExtra("session_id", completed.clientSessionId)
                }
                startActivity(intent)
            }
        }
        updateUI()
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
