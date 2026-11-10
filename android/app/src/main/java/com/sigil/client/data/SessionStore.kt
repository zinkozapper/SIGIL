package com.sigil.client.data

import android.content.Context
import android.os.SystemClock
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class SessionStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun apiBaseUrl(): String? = preferences.getString("api_base_url", null)

    fun setApiBaseUrl(url: String) {
        preferences.edit().putString("api_base_url", url).apply()
    }

    @Synchronized
    fun activeStartedAtMillis(): Long? {
        if (!preferences.contains(ACTIVE_STARTED_AT)) return null
        return preferences.getLong(ACTIVE_STARTED_AT, 0L)
    }

    @Synchronized
    fun startSession(startedAtMillis: Long = System.currentTimeMillis()) {
        preferences.edit()
            .putLong(ACTIVE_STARTED_AT, startedAtMillis)
            .putLong(ACTIVE_STARTED_ELAPSED, SystemClock.elapsedRealtime())
            .remove(STOPPED_AT)
            .remove(STOPPED_WALL_AT)
            .apply()
    }

    @Synchronized
    fun cancelSession() {
        preferences.edit()
            .remove(ACTIVE_STARTED_AT)
            .remove(ACTIVE_STARTED_ELAPSED)
            .remove(STOPPED_AT)
            .remove(STOPPED_WALL_AT)
            .apply()
    }

    @Synchronized
    fun elapsedSessionMillis(): Long? {
        val started = activeStartedAtMillis() ?: return null
        val stopped = stoppedAtMillis()
        if (stopped != null) return (stopped - started).coerceAtLeast(0)
        val elapsedStart = preferences.getLong(ACTIVE_STARTED_ELAPSED, -1L)
        val elapsed = SystemClock.elapsedRealtime() - elapsedStart
        return if (elapsedStart >= 0 && elapsed >= 0) elapsed else (System.currentTimeMillis() - started).coerceAtLeast(0)
    }

    @Synchronized
    fun stoppedSessionExpired(now: Long = System.currentTimeMillis()): Boolean {
        val stopped = stoppedAtMillis() ?: return false
        val stoppedWall = preferences.getLong(STOPPED_WALL_AT, stopped)
        return now - stoppedWall >= 10 * 60 * 1000L
    }

    @Synchronized
    fun stoppedAtMillis(): Long? {
        if (!preferences.contains(STOPPED_AT)) return null
        return preferences.getLong(STOPPED_AT, 0L)
    }

    @Synchronized
    fun markStoppedAt(stoppedAtMillis: Long = System.currentTimeMillis()) {
        val startedAtMillis = activeStartedAtMillis() ?: return
        if (this.stoppedAtMillis() != null) return
        val startedElapsed = preferences.getLong(ACTIVE_STARTED_ELAPSED, -1L)
        val elapsed = if (startedElapsed >= 0) SystemClock.elapsedRealtime() - startedElapsed else -1L
        val monotonicEnd = if (elapsed >= 0) startedAtMillis + elapsed else stoppedAtMillis
        preferences.edit()
            .putLong(STOPPED_AT, maxOf(startedAtMillis, monotonicEnd))
            .putLong(STOPPED_WALL_AT, stoppedAtMillis)
            .apply()
    }

    @Synchronized
    fun completeSession(
        subject: String?,
        chapter: String?,
        notes: String?,
        endedAtMillis: Long? = null
    ): PendingSession? {
        val startedAtMillis = activeStartedAtMillis() ?: return null
        val endedAt = maxOf(
            startedAtMillis,
            endedAtMillis ?: stoppedAtMillis() ?: System.currentTimeMillis()
        )
        val session = PendingSession(
            clientSessionId = UUID.randomUUID().toString(),
            startedAtMillis = startedAtMillis,
            endedAtMillis = endedAt,
            subject = subject.clean(),
            chapter = chapter.clean(),
            notes = notes.clean()
        )

        val updatedOutbox = pendingSessions().toMutableList()
        updatedOutbox += session
        savePendingSessions(updatedOutbox)
        preferences.edit()
            .remove(ACTIVE_STARTED_AT)
            .remove(ACTIVE_STARTED_ELAPSED)
            .remove(STOPPED_AT)
            .remove(STOPPED_WALL_AT)
            .apply()
        return session
    }

    @Synchronized
    fun pendingSessions(): List<PendingSession> {
        val stored = preferences.getString(OUTBOX, "[]") ?: "[]"
        val json = JSONArray(stored)

        var repairedClockSkew = false
        val sessions = buildList {
            for (index in 0 until json.length()) {
                val item = json.getJSONObject(index)
                val session = PendingSession(
                    clientSessionId = item.getString("client_session_id"),
                    startedAtMillis = item.getLong("started_at_millis"),
                    endedAtMillis = item.getLong("ended_at_millis"),
                    subject = item.optionalString("subject"),
                    chapter = item.optionalString("chapter"),
                    notes = item.optionalString("notes")
                )
                if (session.endedAtMillis < session.startedAtMillis) {
                    // shortcut: old clock-skewed entries become zero-duration; their actual end time is unrecoverable.
                    repairedClockSkew = true
                    Log.w(TAG, "Repairing reversed timestamps session=${session.clientSessionId}")
                    add(session.copy(endedAtMillis = session.startedAtMillis))
                } else {
                    add(session)
                }
            }
        }
        if (repairedClockSkew) savePendingSessions(sessions)
        return sessions
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
        const val TAG = "SigilSync"
        const val PREFERENCES_NAME = "sigil_client"
        const val ACTIVE_STARTED_AT = "active_started_at"
        const val ACTIVE_STARTED_ELAPSED = "active_started_elapsed"
        const val STOPPED_AT = "stopped_at"
        const val STOPPED_WALL_AT = "stopped_wall_at"
        const val OUTBOX = "completed_session_outbox"
    }
}
