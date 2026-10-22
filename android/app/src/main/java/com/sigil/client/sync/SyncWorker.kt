package com.sigil.client.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.sigil.client.BuildConfig
import com.sigil.client.data.SessionStore
import com.sigil.client.network.ApiClient

class SyncWorker(
    appContext: Context,
    workerParameters: WorkerParameters
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        val store = SessionStore(applicationContext)
        val apiClient = ApiClient(BuildConfig.API_BASE_URL)

        for (session in store.pendingSessions()) {
            when (apiClient.submit(session)) {
                ApiClient.SubmitResult.Success -> store.removePending(session.clientSessionId)
                is ApiClient.SubmitResult.RetryableFailure -> return Result.retry()
                is ApiClient.SubmitResult.PermanentFailure -> return Result.failure()
            }
        }

        return Result.success()
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "sigil-session-sync"

        fun enqueue(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request
            )
        }
    }
}
