package com.example.watchdogbridge.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/** Upgrade tombstone: old persisted jobs never read Health Connect or write hashes. */
class HealthConnectIntradayWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        WorkerUtil.reconcile(applicationContext)
        return Result.success()
    }
}
