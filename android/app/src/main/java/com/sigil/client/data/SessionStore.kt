package com.sigil.client.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class SessionStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun activeStartedAtMillis(): Long? {
        if (!preferences.contains(ACTIVE_STARTED_AT)) return null
        return preferences.getLong(ACTIVE_STARTED_AT, 0L)
    }

    @Synchronized
    fun startSession(startedAtMillis: Long = System.currentTimeMillis()) {
        preferences.edit()
            .putLong(ACTIVE_STARTED_AT, startedAtMillis)
            .apply()
    }

    @Synchronized
    fun completeSession(
        subject: String?,
        chapter: String?,
        notes: String?,
        endedAtMillis: Long = System.currentTimeMillis()
    ): PendingSession? {
        val startedAtMillis = activeStartedAtMillis() ?: return null
        val session = PendingSession(
            clientSessionId = UUID.randomUUID().toString(),
            startedAtMillis = startedAtMillis,
            endedAtMillis = endedAtMillis,
            subject = subject.clean(),
            chapter = chapter.clean(),
            notes = notes.clean()
        )

        val updatedOutbox = pendingSessions().toMutableList()
        updatedOutbox += session
        savePendingSessions(updatedOutbox)
        preferences.edit().remove(ACTIVE_STARTED_AT).apply()
        return session
    }

    @Synchronized
    fun pendingSessions(): List<PendingSession> {
        val stored = preferences.getString(OUTBOX, "[]") ?: "[]"
        val json = JSONArray(stored)

        return buildList {
            for (index in 0 until json.length()) {
                val item = json.getJSONObject(index)
                add(
                    PendingSession(
                        clientSessionId = item.getString("client_session_id"),
                        startedAtMillis = item.getLong("started_at_millis"),
                        endedAtMillis = item.getLong("ended_at_millis"),
                        subject = item.optionalString("subject"),
                        chapter = item.optionalString("chapter"),
                        notes = item.optionalString("notes")
                    )
                )
            }
        }
    }

    @Synchronized
    fun removePending(clientSessionId: String) {
        savePendingSessions(
            pendingSessions().filterNot { it.clientSessionId == clientSessionId }
        )
    }

    private fun savePendingSessions(sessions: List<PendingSession>) {
        val json = JSONArray()
        sessions.forEach { session ->
            json.put(JSONObject().apply {
                put("client_session_id", session.clientSessionId)
                put("started_at_millis", session.startedAtMillis)
                put("ended_at_millis", session.endedAtMillis)
                putOptional("subject", session.subject)
                putOptional("chapter", session.chapter)
                putOptional("notes", session.notes)
            })
        }
        preferences.edit().putString(OUTBOX, json.toString()).apply()
    }

    private fun JSONObject.optionalString(key: String): String? {
        return if (has(key) && !isNull(key)) getString(key) else null
    }

    private fun JSONObject.putOptional(key: String, value: String?) {
        if (!value.isNullOrBlank()) put(key, value)
    }

    private fun String?.clean(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

    private companion object {
        const val PREFERENCES_NAME = "sigil_client"
        const val ACTIVE_STARTED_AT = "active_started_at"
        const val OUTBOX = "completed_session_outbox"
    }
}
