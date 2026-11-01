package com.example.watchdogbridge

import android.app.Application
import com.example.watchdogbridge.network.NetworkClient
import com.example.watchdogbridge.worker.WorkerUtil

class BridgeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        NetworkClient.initialize(this)
        WorkerUtil.scheduleWorkers(this)
    }
}
