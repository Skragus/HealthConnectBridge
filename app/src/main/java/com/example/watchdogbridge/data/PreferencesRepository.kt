package com.example.watchdogbridge.data

import android.content.Context
import android.content.SharedPreferences
import java.util.UUID
import com.example.healthconnectbridge.BuildConfig

class PreferencesRepository(context: Context) {

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "watchdog_bridge_prefs"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_SETUP_COMPLETED = "setup_completed"
        private const val KEY_LAST_SYNC_RUN = "last_sync_run"
    }

    fun getDeviceId(): String {
        val existingId = sharedPreferences.getString(KEY_DEVICE_ID, null)
        if (existingId != null) {
            return existingId
        }
        val newId = UUID.randomUUID().toString()
        sharedPreferences.edit().putString(KEY_DEVICE_ID, newId).apply()
        return newId
    }

    fun isSetupCompleted(): Boolean {
        return sharedPreferences.getBoolean(KEY_SETUP_COMPLETED, false)
    }

    fun setSetupCompleted(completed: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_SETUP_COMPLETED, completed).apply()
    }

    fun getLookbackDays(): Int = sharedPreferences.getInt("lookback_days", 7).coerceIn(1, 30)

    fun setLookbackDays(days: Int) {
        require(days in 1..30)
        sharedPreferences.edit().putInt("lookback_days", days).apply()
    }

    fun getApiKey(): String = sharedPreferences.getString("api_key", null) ?: BuildConfig.API_KEY

    fun setApiKey(key: String) {
        sharedPreferences.edit().putString("api_key", key.trim()).apply()
    }

    fun setLastSyncRun(timestamp: Long) {
        sharedPreferences.edit().putLong(KEY_LAST_SYNC_RUN, timestamp).apply()
    }

    fun getLastSyncRun(): Long = sharedPreferences.getLong(KEY_LAST_SYNC_RUN, 0L)
}
