package com.example.watchdogbridge.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.watchdogbridge.data.HealthConnectRepository
import com.example.watchdogbridge.data.PreferencesRepository
import com.example.watchdogbridge.data.local.AppDatabase
import com.example.watchdogbridge.data.local.DailySyncState
import com.example.watchdogbridge.data.model.DailyIngestRequest
import com.example.watchdogbridge.data.model.Source
import com.example.watchdogbridge.network.NetworkClient
import com.example.watchdogbridge.util.DataHasher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** One implementation for periodic history, manual sync, and custom backfill. */
class HealthConnectDailyWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    companion object {
        // Serializes manual/backfill requests with periodic work in this app process.
        private val syncMutex = Mutex()
    }

    override suspend fun doWork(): Result = syncMutex.withLock {
        val prefs = PreferencesRepository(applicationContext)
        val dao = AppDatabase.getDatabase(applicationContext).dailySyncStateDao()
        val repo = HealthConnectRepository(applicationContext)
        NetworkClient.initialize(applicationContext)
        if (prefs.getApiKey().isBlank()) return@withLock Result.retry()
        try {
            if (!repo.hasPermissions()) return@withLock Result.retry()
            val today = LocalDate.now()
            val end = inputData.getString("end_date")?.let(LocalDate::parse) ?: today
            val start = inputData.getString("start_date")?.let(LocalDate::parse)
                ?: today.minusDays(inputData.getInt("days", prefs.getLookbackDays() - 1).toLong())
            if (start > end || end > today || ChronoUnit.DAYS.between(start, end) > 3660) {
                return@withLock Result.failure()
            }
            val zone = ZoneId.systemDefault()
            var needsRetry = false
            // Most recent day first; a failed older backfill does not starve today.
            for (offset in 0..ChronoUnit.DAYS.between(start, end)) {
                val day = end.minusDays(offset)
                val dayString = day.toString()
                val read = repo.readSnapshot(day.atStartOfDay(zone).toInstant().toEpochMilli(),
                    day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli())
                val request = DailyIngestRequest(date = dayString, rawJson = read.rawJson,
                    source = Source(deviceId = prefs.getDeviceId(), collectedAt = Instant.now().toString()))
                val hash = DataHasher.computeHash(request)
                val previous = dao.getSyncState(dayString)
                if (read.complete && previous?.dataHash == hash) continue
                val response = NetworkClient.api.postDaily(request)
                // Partial records are durable server-side, but must not acknowledge a
                // complete local snapshot. Retry failed pages/types on the next run.
                if (response.isSuccessful && read.complete) {
                    dao.insertOrUpdate(DailySyncState(date = dayString, dataHash = hash,
                        lastSyncedAt = System.currentTimeMillis()))
                } else {
                    needsRetry = true
                    Log.w("HistoricalSync", "Snapshot needs retry status=${response.code()} complete=${read.complete}")
                }
                delay(1000)
            }
            if (!needsRetry) prefs.setLastSyncRun(System.currentTimeMillis())
            if (needsRetry) Result.retry() else Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.w("HistoricalSync", "Sync failed error_type=${error.javaClass.simpleName}")
            Result.retry()
        }
    }
}
