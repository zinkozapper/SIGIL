package com.sigil.client.data

import org.json.JSONObject
import java.time.Instant
import java.time.format.DateTimeFormatter

data class PendingSession(
    val clientSessionId: String,
    val startedAtMillis: Long,
    val endedAtMillis: Long,
    val subject: String?,
    val chapter: String?,
    val notes: String?
) {
    fun toRequestJson(): JSONObject = JSONObject().apply {
        put("client_session_id", clientSessionId)
        put("started_at", DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(startedAtMillis)))
        put("ended_at", DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(endedAtMillis)))
        putOptional("subject", subject)
        putOptional("chapter", chapter)
        putOptional("notes", notes)
        put("metadata", JSONObject().apply {
            put("source", "sigil-android")
            put("trigger", "nfc")
        })
    }

    private fun JSONObject.putOptional(key: String, value: String?) {
        if (!value.isNullOrBlank()) put(key, value)
    }
}
