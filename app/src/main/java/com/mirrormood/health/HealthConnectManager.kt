package com.mirrormood.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import java.time.temporal.ChronoUnit

class HealthConnectManager(private val context: Context) {

    private val healthConnectClient by lazy { HealthConnectClient.getOrCreate(context) }

    val permissions = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class)
    )

    fun isAvailable(): Boolean {
        return HealthConnectClient.getSdkStatus(context, "com.google.android.apps.healthdata") == HealthConnectClient.SDK_AVAILABLE
    }

    suspend fun hasAllPermissions(): Boolean {
        if (!isAvailable()) return false
        val granted = healthConnectClient.permissionController.getGrantedPermissions()
        return granted.containsAll(permissions)
    }

    suspend fun readSleepDuration(startTime: Instant, endTime: Instant): Long {
        if (!hasAllPermissions()) return 0L
        return try {
            val request = ReadRecordsRequest(
                recordType = SleepSessionRecord::class,
                timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
            )
            val response = healthConnectClient.readRecords(request)
            response.records.sumOf { record ->
                record.endTime.epochSecond - record.startTime.epochSecond
            }
        } catch (e: Exception) {
            0L
        }
    }

    suspend fun readSteps(startTime: Instant, endTime: Instant): Long {
        if (!hasAllPermissions()) return 0L
        return try {
            val request = ReadRecordsRequest(
                recordType = StepsRecord::class,
                timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
            )
            val response = healthConnectClient.readRecords(request)
            response.records.sumOf { it.count }
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * Read sleep session durations (in hours) for the given time range.
     */
    suspend fun readSleepDurations(startTime: Instant, endTime: Instant): List<Float> {
        if (!hasAllPermissions()) return emptyList()
        return try {
            val request = ReadRecordsRequest(
                recordType = SleepSessionRecord::class,
                timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
            )
            val response = healthConnectClient.readRecords(request)
            response.records.map { session ->
                val duration = java.time.Duration.between(session.startTime, session.endTime)
                duration.toMinutes() / 60f
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Read total sleep hours for the given time range.
     */
    suspend fun readSleepHours(startTime: Instant, endTime: Instant): Float {
        return readSleepDurations(startTime, endTime).sum()
    }

    /**
     * Calculate sleep quality score (0-100) based on recent sleep data.
     */
    suspend fun calculateSleepQuality(days: Int = 7): SleepQualityResult {
        val endTime = Instant.now()
        val startTime = endTime.minus(days.toLong(), ChronoUnit.DAYS)
        val durations = readSleepDurations(startTime, endTime)
        return SleepQualityResult.calculate(durations)
    }

    /**
     * Read average resting heart rate for the given time range.
     */
    suspend fun readAverageHeartRate(startTime: Instant, endTime: Instant): Long {
        if (!hasAllPermissions()) return 0L
        return try {
            val response = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    recordType = HeartRateRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                )
            )
            val allSamples = response.records.flatMap { record ->
                record.samples.map { it.beatsPerMinute }
            }
            if (allSamples.isEmpty()) 0L else allSamples.average().toLong()
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * Get today's health snapshot for the dashboard card.
     */
    suspend fun getTodaySnapshot(): HealthSnapshot {
        val endTime = Instant.now()
        val startOfDay = endTime.truncatedTo(ChronoUnit.DAYS)

        val steps = readSteps(startOfDay, endTime)
        val sleepHours = readSleepHours(
            endTime.minus(24, ChronoUnit.HOURS), endTime
        )
        val sleepQuality = calculateSleepQuality()

        return HealthSnapshot(
            steps = steps,
            sleepHours = sleepHours,
            sleepQualityScore = sleepQuality.score,
            sleepRecommendation = sleepQuality.recommendation
        )
    }
}

/**
 * Compact snapshot of today's health data for the dashboard card.
 */
data class HealthSnapshot(
    val steps: Long,
    val sleepHours: Float,
    val sleepQualityScore: Int,
    val sleepRecommendation: String
)
