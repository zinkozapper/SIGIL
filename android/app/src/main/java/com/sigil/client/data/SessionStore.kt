package com.sigil.client.data

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class SessionStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val contentResolver = context.contentResolver

    private fun bootCount(): Int = Settings.Global.getInt(contentResolver, Settings.Global.BOOT_COUNT, -1)

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
    fun startSession(startedAtMillis: Long = System.currentTimeMillis(), trigger: String = "nfc") {
        preferences.edit()
            .putLong(ACTIVE_STARTED_AT, startedAtMillis)
            .putLong(ACTIVE_STARTED_ELAPSED, SystemClock.elapsedRealtime())
            .putInt(ACTIVE_STARTED_BOOT, bootCount())
            .putString(START_TRIGGER, trigger)
            .remove(STOPPED_AT)
            .remove(STOPPED_WALL_AT)
            .remove(PAUSED_AT)
            .remove(PAUSED_ELAPSED)
            .remove(PAUSED_BOOT)
            .remove(PAUSED_DURATION)
            .apply()
    }

    @Synchronized
    fun cancelSession() {
        preferences.edit()
            .remove(ACTIVE_STARTED_AT)
            .remove(ACTIVE_STARTED_ELAPSED)
            .remove(ACTIVE_STARTED_BOOT)
            .remove(START_TRIGGER)
            .remove(STOPPED_AT)
            .remove(STOPPED_WALL_AT)
            .remove(PAUSED_AT)
            .remove(PAUSED_ELAPSED)
            .remove(PAUSED_BOOT)
            .remove(PAUSED_DURATION)
            .apply()
    }

    @Synchronized
    fun isPaused(): Boolean = preferences.contains(PAUSED_AT)

    @Synchronized
    fun togglePause() {
        if (activeStartedAtMillis() == null || stoppedAtMillis() != null) return
        val editor = preferences.edit()
        if (isPaused()) {
            editor.putLong(PAUSED_DURATION, pausedDurationMillis())
                .remove(PAUSED_AT)
                .remove(PAUSED_ELAPSED)
                .remove(PAUSED_BOOT)
        } else {
            editor.putLong(PAUSED_AT, System.currentTimeMillis())
                .putLong(PAUSED_ELAPSED, SystemClock.elapsedRealtime())
                .putInt(PAUSED_BOOT, bootCount())
        }
        editor.apply()
    }

    private fun pausedDurationMillis(): Long {
        val total = preferences.getLong(PAUSED_DURATION, 0L)
        if (!isPaused()) return total
        return total + elapsedSince(
            preferences.getLong(PAUSED_AT, 0L),
            preferences.getLong(PAUSED_ELAPSED, -1L),
            preferences.getInt(PAUSED_BOOT, -1),
            System.currentTimeMillis(), SystemClock.elapsedRealtime(), bootCount()
        )
    }

    @Synchronized
    fun elapsedSessionMillis(): Long? {
        val started = activeStartedAtMillis() ?: return null
        val stopped = stoppedAtMillis()
        val total = if (stopped != null) stopped - started else elapsedSince(
            started, preferences.getLong(ACTIVE_STARTED_ELAPSED, -1L),
            preferences.getInt(ACTIVE_STARTED_BOOT, -1),
            System.currentTimeMillis(), SystemClock.elapsedRealtime(), bootCount()
        )
        return (total - pausedDurationMillis()).coerceAtLeast(0)
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
        val elapsed = elapsedSince(
            startedAtMillis, preferences.getLong(ACTIVE_STARTED_ELAPSED, -1L),
            preferences.getInt(ACTIVE_STARTED_BOOT, -1),
            stoppedAtMillis, SystemClock.elapsedRealtime(), bootCount()
        )
        val monotonicEnd = startedAtMillis + elapsed
        preferences.edit()
            .putLong(STOPPED_AT, maxOf(startedAtMillis, monotonicEnd))
            .putLong(PAUSED_DURATION, pausedDurationMillis().coerceAtMost(maxOf(0L, monotonicEnd - startedAtMillis)))
            .remove(PAUSED_AT)
            .remove(PAUSED_ELAPSED)
            .remove(PAUSED_BOOT)
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
            pausedDurationMillis = preferences.getLong(PAUSED_DURATION, 0L),
            trigger = preferences.getString(START_TRIGGER, "nfc") ?: "nfc",
            notes = notes.clean()
        )

        val updatedOutbox = pendingSessions().toMutableList()
        updatedOutbox += session
        savePendingSessions(updatedOutbox)
        cancelSession()
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
                    pausedDurationMillis = item.optLong("paused_duration_millis", 0L),
                    trigger = item.optString("trigger", "nfc"),
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
                put("paused_duration_millis", session.pausedDurationMillis)
                put("trigger", session.trigger)
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
        const val ACTIVE_STARTED_BOOT = "active_started_boot"
        const val START_TRIGGER = "start_trigger"
        const val STOPPED_AT = "stopped_at"
        const val PAUSED_AT = "paused_at"
        const val PAUSED_ELAPSED = "paused_elapsed"
        const val PAUSED_BOOT = "paused_boot"
        const val PAUSED_DURATION = "paused_duration_millis"
        const val STOPPED_WALL_AT = "stopped_wall_at"
        const val OUTBOX = "completed_session_outbox"
    }
}

internal fun elapsedSince(
    wallStart: Long, elapsedStart: Long, startBoot: Int,
    wallNow: Long, elapsedNow: Long, currentBoot: Int
): Long {
    val duration = if (startBoot >= 0 && startBoot == currentBoot && elapsedStart >= 0 && elapsedNow >= elapsedStart) {
        elapsedNow - elapsedStart
    } else {
        wallNow - wallStart
    }
    return duration.coerceAtLeast(0)
}
