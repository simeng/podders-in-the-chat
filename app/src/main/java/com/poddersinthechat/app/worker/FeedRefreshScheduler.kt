package com.poddersinthechat.app.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Schedules the daily background feed refresh to run at around 12:00 (noon).
 *
 * The first run is delayed until the next noon, then repeats every 24 hours,
 * which keeps it anchored to noon (modulo daylight-saving shifts). Safe to
 * call repeatedly: the unique work name + KEEP policy makes rescheduling a
 * no-op for an already scheduled refresh.
 */
object FeedRefreshScheduler {

    private const val WORK_NAME = "podders_daily_feed_refresh"
    const val REFRESH_HOUR_OF_DAY = 12

    fun scheduleDailyRefresh(context: Context) {
        val now = System.currentTimeMillis()
        val nextRun = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, REFRESH_HOUR_OF_DAY)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= now) {
                add(Calendar.DAY_OF_MONTH, 1) // noon already passed today → tomorrow
            }
        }.timeInMillis

        val request = PeriodicWorkRequestBuilder<FeedRefreshWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(nextRun - now, TimeUnit.MILLISECONDS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
