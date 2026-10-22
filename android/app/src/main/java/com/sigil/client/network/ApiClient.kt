package com.sigil.client.network

import com.sigil.client.data.PendingSession
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class ApiClient(private val baseUrl: String) {
    sealed interface SubmitResult {
        data object Success : SubmitResult
        data class RetryableFailure(val statusCode: Int? = null) : SubmitResult
        data class PermanentFailure(val statusCode: Int) : SubmitResult
    }

    fun submit(session: PendingSession): SubmitResult {
        val connection = try {
            (URL(baseUrl.trimEnd('/') + "/sessions").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10_000
                readTimeout = 10_000
                doOutput = true
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Content-Type", "application/json")
            }
        } catch (_: IOException) {
            return SubmitResult.RetryableFailure()
        }

        return try {
            connection.outputStream.use { output ->
                output.write(session.toRequestJson().toString().toByteArray(Charsets.UTF_8))
            }

            when (val code = connection.responseCode) {
                in 200..299 -> SubmitResult.Success
                408, 425, 429 -> SubmitResult.RetryableFailure(code)
                in 500..599 -> SubmitResult.RetryableFailure(code)
                else -> SubmitResult.PermanentFailure(code)
            }
        } catch (_: IOException) {
            SubmitResult.RetryableFailure()
        } finally {
            connection.disconnect()
        }
    }
}
