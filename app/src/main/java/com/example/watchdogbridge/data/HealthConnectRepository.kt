package com.example.watchdogbridge.data

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import java.util.TreeMap
import java.lang.reflect.Type
import java.time.Instant

class HealthConnectRepository(private val context: Context) {

    private val TAG = "HealthConnectRepo"
    
    private val gson = GsonBuilder()
        .registerTypeAdapter(Instant::class.java, object : JsonSerializer<Instant> {
            override fun serialize(src: Instant?, typeOfSrc: Type?, context: JsonSerializationContext?): JsonElement {
                return JsonPrimitive(src?.toString())
            }
        })
        .create()
    
    private val healthConnectClient by lazy { 
        try {
            HealthConnectClient.getOrCreate(context)
        } catch (e: Exception) {
            Log.e(TAG, "Health Connect not available", e)
            throw e
        }
    }

    private val recordTypes = listOf(
        StepsRecord::class,
        SleepSessionRecord::class,
        HeartRateRecord::class,
        RestingHeartRateRecord::class,
        ExerciseSessionRecord::class,
        WeightRecord::class,
        BodyFatRecord::class,
        NutritionRecord::class,
        ActiveCaloriesBurnedRecord::class,
        TotalCaloriesBurnedRecord::class,
        DistanceRecord::class,
        HydrationRecord::class,
        BloodPressureRecord::class,
        Vo2MaxRecord::class,
        BasalMetabolicRateRecord::class,
        BodyWaterMassRecord::class,
        BoneMassRecord::class,
        LeanBodyMassRecord::class,
        FloorsClimbedRecord::class,
        ElevationGainedRecord::class,
        OxygenSaturationRecord::class,
        RespiratoryRateRecord::class,
        HeartRateVariabilityRmssdRecord::class,
        BodyTemperatureRecord::class,
        HeightRecord::class,
        BloodGlucoseRecord::class
    )

    suspend fun getPermissions(): Set<String> {
        val permissions = recordTypes.map { HealthPermission.getReadPermission(it) }.toMutableSet()
        permissions.add(HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY)
        if (Build.VERSION.SDK_INT >= 34) {
            permissions.add("android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND")
        }
        return permissions
    }

    suspend fun hasPermissions(): Boolean {
        return try {
            val permissions = getPermissions()
            val granted = healthConnectClient.permissionController.getGrantedPermissions()
            granted.containsAll(permissions)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking permissions", e)
            false
        }
    }

    data class ReadResult(val rawJson: String, val complete: Boolean)

    suspend fun readRawData(startTime: Long, endTime: Long): String = readSnapshot(startTime, endTime).rawJson

    suspend fun readSnapshot(startTime: Long, endTime: Long): ReadResult {
        val rawData = sortedMapOf<String, Any>()
        val statuses = sortedMapOf<String, Any>()
        val filter = TimeRangeFilter.between(Instant.ofEpochMilli(startTime), Instant.ofEpochMilli(endTime))
        var complete = true
        for (type in recordTypes) {
            val name = type.simpleName ?: error("Missing record type name")
            try {
                delay(800)
                val result = readAllPages { token ->
                    val response = healthConnectClient.readRecords(
                        ReadRecordsRequest(recordType = type, timeRangeFilter = filter,
                            pageSize = 1000, pageToken = token)
                    )
                    RecordPage(response.records, response.pageToken)
                }
                // Stable ordering avoids hash churn from provider page order.
                rawData[name] = result.records.sortedBy { it.metadata.id }
                statuses[name] = mapOf("complete" to true, "pages" to result.pages, "count" to result.records.size)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                complete = false
                val code = if (error is SecurityException) "permission_denied" else "read_failed"
                // No exception text/record contents: provider errors can contain personal data.
                Log.w(TAG, "Health Connect read failed type=$name error=$code")
                statuses[name] = mapOf("complete" to false, "error" to code)
                if (error.message?.contains("quota", ignoreCase = true) == true) delay(40000)
            }
        }
        rawData["_sync"] = mapOf("version" to 1, "start_time" to Instant.ofEpochMilli(startTime).toString(),
            "end_time" to Instant.ofEpochMilli(endTime).toString(), "types" to statuses)
        return ReadResult(gson.toJson(rawData), complete)
    }
}
