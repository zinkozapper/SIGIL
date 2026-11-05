package com.sigil.client.network

import android.util.Log
import com.sigil.client.data.PendingSession
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class ApiClient(private val baseUrl: String) {
    private companion object {
        const val TAG = "SigilSync"
    }

    sealed interface SubmitResult {
        data object Success : SubmitResult
        data class RetryableFailure(val statusCode: Int? = null) : SubmitResult
        data class PermanentFailure(val statusCode: Int) : SubmitResult
    }

    fun submit(session: PendingSession): SubmitResult {
        val endpoint = baseUrl.trimEnd('/') + "/sessions"
        val payload = session.toRequestJson()
        Log.i(
            TAG,
            "POST $endpoint session=${session.clientSessionId} " +
                "fields=${payload.keys().asSequence().joinToString(",")}"
        )
        val connection = try {
            (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10_000
                readTimeout = 10_000
                doOutput = true
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Content-Type", "application/json")
            }
        } catch (error: IOException) {
            Log.e(TAG, "Could not open API connection session=${session.clientSessionId}", error)
            return SubmitResult.RetryableFailure()
        }

        return try {
            connection.outputStream.use { output ->
                output.write(payload.toString().toByteArray(Charsets.UTF_8))
            }

            val code = connection.responseCode
            val requestId = connection.getHeaderField("X-Request-Id")
            val responseBody = (if (code >= 400) connection.errorStream else connection.inputStream)
                ?.bufferedReader()
                ?.use { it.readText() }
                .orEmpty()
            if (code in 200..299) {
                Log.i(TAG, "API accepted session=${session.clientSessionId} status=$code request_id=$requestId")
                SubmitResult.Success
            } else {
                Log.e(TAG, "API rejected session=${session.clientSessionId} status=$code request_id=$requestId body=${responseBody.take(1000)}")
                when (code) {
                    408, 425, 429 -> SubmitResult.RetryableFailure(code)
                    in 500..599 -> SubmitResult.RetryableFailure(code)
                    else -> SubmitResult.PermanentFailure(code)
                }
            }
        } catch (error: IOException) {
            Log.e(TAG, "API I/O failure session=${session.clientSessionId}", error)
            SubmitResult.RetryableFailure()
        } finally {
            connection.disconnect()
        }
    }
}
