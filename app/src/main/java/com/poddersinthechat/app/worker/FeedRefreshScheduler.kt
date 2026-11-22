package com.poddersinthechat.app.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.poddersinthechat.app.data.SettingsStore
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Schedules the daily background feed refresh.
 *
 * Whether it runs and at what hour are user settings (see [SettingsStore]);
 * [applySettings] is the single entry point that reconciles WorkManager with
 * those settings — call it on app start and whenever the user changes them.
 * With auto-refresh disabled the periodic work is cancelled entirely and
 * feeds only update via the manual refresh button.
 */
object FeedRefreshScheduler {

    private const val WORK_NAME = "podders_daily_feed_refresh"
    const val DEFAULT_REFRESH_HOUR = 12

    /**
     * Schedules (or reschedules) the daily refresh to run at
     * [hourOfDay]:00. The first run is delayed until the next occurrence of
     * that hour, then repeats every 24 hours, keeping it anchored to the
     * chosen hour (modulo daylight-saving shifts).
     */
    fun scheduleDailyRefresh(context: Context, hourOfDay: Int = DEFAULT_REFRESH_HOUR) {
        val now = System.currentTimeMillis()
        val nextRun = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hourOfDay.coerceIn(0, 23))
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= now) {
                add(Calendar.DAY_OF_MONTH, 1) // that hour already passed today → tomorrow
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

        // UPDATE: changing the hour in settings replaces the scheduled run
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun cancelDailyRefresh(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    /** Reconciles the scheduled work with the user's settings. */
    fun applySettings(context: Context, enabled: Boolean, hourOfDay: Int) {
        if (enabled) {
            scheduleDailyRefresh(context, hourOfDay)
        } else {
            cancelDailyRefresh(context)
        }
    }

    fun applySettings(context: Context, settings: SettingsStore) {
        applySettings(context, settings.autoRefreshEnabled.value, settings.refreshHourOfDay.value)
    }
}
