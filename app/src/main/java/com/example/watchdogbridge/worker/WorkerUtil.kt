package com.example.watchdogbridge.worker

import android.content.Context
import androidx.work.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit

object WorkerUtil {
    const val PERIODIC_NAME = "HealthConnectDailySync"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val scheduleMutex = Mutex()
    val retiredNames = listOf("HealthConnectIntradaySync")
    val retiredTags = listOf("intraday_sync", "manual_intraday_sync")

    fun scheduleWorkers(context: Context) {
        scope.launch { reconcile(context.applicationContext) }
    }

    suspend fun reconcile(context: Context) = scheduleMutex.withLock {
        withContext(Dispatchers.IO) {
            val manager = WorkManager.getInstance(context)
            retiredNames.forEach { manager.cancelUniqueWork(it).result.get() }
            retiredTags.forEach { manager.cancelAllWorkByTag(it).result.get() }
            val request = PeriodicWorkRequestBuilder<HealthConnectDailyWorker>(6, TimeUnit.HOURS,
                30, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
                .addTag("daily_sync")
                .build()
            // Stable name and UPDATE preserve one periodic job across upgrades/restarts.
            manager.enqueueUniquePeriodicWork(PERIODIC_NAME, ExistingPeriodicWorkPolicy.UPDATE, request).result.get()
        }
    }

    suspend fun enqueueManual(context: Context, data: Data = Data.EMPTY): java.util.UUID = scheduleMutex.withLock {
        withContext(Dispatchers.IO) {
            val manager = WorkManager.getInstance(context)
            val key = java.util.UUID.nameUUIDFromBytes(data.keyValueMap.toSortedMap().toString().toByteArray())
            val name = "HealthConnectManualSync:$key"
            val pending = manager.getWorkInfosForUniqueWork(name).get().firstOrNull { !it.state.isFinished }
            if (pending != null) return@withContext pending.id
            val request = OneTimeWorkRequestBuilder<HealthConnectDailyWorker>()
                .setInputData(data).addTag("historical_manual_sync").build()
            manager.enqueueUniqueWork(name, ExistingWorkPolicy.KEEP, request).result.get()
            request.id
        }
    }
}
