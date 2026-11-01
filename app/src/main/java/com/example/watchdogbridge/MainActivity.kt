package com.example.watchdogbridge

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.lifecycleScope
import androidx.work.Data
import androidx.work.WorkManager
import com.example.watchdogbridge.data.HealthConnectRepository
import com.example.watchdogbridge.data.PreferencesRepository
import com.example.watchdogbridge.ui.theme.WatchdogBridgeTheme
import com.example.watchdogbridge.worker.WorkerUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    private val health by lazy { HealthConnectRepository(applicationContext) }
    private val prefs by lazy { PreferencesRepository(applicationContext) }
    private var status by mutableStateOf("Historical sync runs every 6 hours")
    private val permissions = registerForActivityResult(PermissionController.createRequestPermissionResultContract()) {
        status = "Permission selection saved"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var lookback by remember { mutableStateOf(prefs.getLookbackDays().toString()) }
            var key by remember { mutableStateOf(prefs.getApiKey()) }
            var start by remember { mutableStateOf(LocalDate.now().minusDays(6).toString()) }
            var end by remember { mutableStateOf(LocalDate.now().toString()) }
            WatchdogBridgeTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("WatchdogBridge", style = MaterialTheme.typography.headlineMedium)
                        Text("One historical Health Connect sync. Original record times are preserved.")
                        Text(status)
                        Button(onClick = { lifecycleScope.launch { permissions.launch(health.getPermissions()) } }) { Text("Health Connect Permissions") }
                        OutlinedTextField(value = lookback, onValueChange = { lookback = it }, label = { Text("Lookback days (1–30, includes today)") })
                        OutlinedTextField(value = key, onValueChange = { key = it }, label = { Text("API key") }, visualTransformation = PasswordVisualTransformation())
                        Button(onClick = {
                            val days = lookback.toIntOrNull()
                            if (days == null || days !in 1..30) status = "Choose 1–30 lookback days"
                            else {
                                prefs.setLookbackDays(days)
                                prefs.setApiKey(key)
                                WorkerUtil.scheduleWorkers(applicationContext)
                                status = "Settings saved"
                            }
                        }) { Text("Save Settings") }
                        Button(onClick = { enqueue(Data.EMPTY) }) { Text("Sync History Now") }
                        Button(onClick = { showStatus() }) { Text("Check Sync Status") }
                        HorizontalDivider()
                        Text("Custom Historical Backfill", style = MaterialTheme.typography.titleMedium)
                        OutlinedTextField(value = start, onValueChange = { start = it }, label = { Text("Start YYYY-MM-DD") })
                        OutlinedTextField(value = end, onValueChange = { end = it }, label = { Text("End YYYY-MM-DD") })
                        Button(onClick = {
                            try {
                                val first = LocalDate.parse(start)
                                val last = LocalDate.parse(end)
                                require(first <= last && last <= LocalDate.now())
                                enqueue(Data.Builder().putString("start_date", start).putString("end_date", end).build())
                            } catch (_: Exception) { status = "Enter a valid date range ending today or earlier" }
                        }) { Text("Sync Date Range") }
                    }
                }
            }
        }
    }

    private fun enqueue(data: Data) {
        lifecycleScope.launch {
            if (prefs.getApiKey().isBlank()) { status = "Save your API key before syncing"; return@launch }
            val id = WorkerUtil.enqueueManual(applicationContext, data)
            WorkManager.getInstance(applicationContext).getWorkInfoByIdLiveData(id).observe(this@MainActivity) {
                if (it != null) status = "Historical sync: ${it.state}"
            }
        }
    }

    private fun showStatus() {
        lifecycleScope.launch {
            val infos = withContext(Dispatchers.IO) {
                WorkManager.getInstance(applicationContext).getWorkInfosForUniqueWork(WorkerUtil.PERIODIC_NAME).get()
            }
            val active = infos.firstOrNull { !it.state.isFinished }
            status = "Historical worker: ${active?.state ?: "Not scheduled"}\nLookback: ${prefs.getLookbackDays()} days\nLast successful sync: ${prefs.getLastSyncRun().takeIf { it > 0 }?.let { java.time.Instant.ofEpochMilli(it) } ?: "Never"}"
        }
    }
}
