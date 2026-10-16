package com.example.watchdogbridge

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.work.*
import androidx.work.testing.WorkManagerTestInitHelper
import com.example.watchdogbridge.worker.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ScheduleMigrationTest {
    @Test fun upgradingCancelsOldJobsAndLeavesExactlyOnePeriodicSync() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        val manager = WorkManager.getInstance(context)
        val old = PeriodicWorkRequestBuilder<HealthConnectIntradayWorker>(1, TimeUnit.HOURS)
            .addTag("intraday_sync").build()
        manager.enqueueUniquePeriodicWork("HealthConnectIntradaySync", ExistingPeriodicWorkPolicy.KEEP, old).result.get()
        val manual = OneTimeWorkRequestBuilder<HealthConnectIntradayWorker>().addTag("manual_intraday_sync")
            .setInitialDelay(1, TimeUnit.DAYS).build()
        manager.enqueue(manual).result.get()
        WorkerUtil.reconcile(context)
        WorkerUtil.reconcile(context)
        assertTrue(manager.getWorkInfosForUniqueWork("HealthConnectIntradaySync").get().all { it.state.isFinished })
        assertEquals(WorkInfo.State.CANCELLED, manager.getWorkInfoById(manual.id).get()!!.state)
        val active = manager.getWorkInfosForUniqueWork(WorkerUtil.PERIODIC_NAME).get().filter { !it.state.isFinished }
        assertEquals(1, active.size)
    }
}
