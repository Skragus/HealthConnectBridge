package com.example.watchdogbridge.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.watchdogbridge.worker.WorkerUtil
import kotlinx.coroutines.*

/** Old MacroDroid actions now reconcile the SAME historical schedule. */
class WorkerRestartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { WorkerUtil.reconcile(context.applicationContext) } finally { pending.finish() }
        }
    }
}
