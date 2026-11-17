package com.poddersinthechat.app.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.poddersinthechat.app.PoddersApp

/**
 * Refreshes all subscribed RSS feeds in the background.
 *
 * Runs once a day at around 12:00 (see [FeedRefreshScheduler]) when the
 * device has a network connection. Feeds that can't be reached keep their
 * last cached episodes, so a flaky single feed never wipes the list.
 */
class FeedRefreshWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): ListenableWorker.Result {
        val repository = (applicationContext as PoddersApp).feedRepository
        val result = repository.refreshAllFeeds()
        return when {
            result.isSuccess -> ListenableWorker.Result.success()
            runAttemptCount < MAX_ATTEMPTS -> ListenableWorker.Result.retry()
            else -> ListenableWorker.Result.failure()
        }
    }

    private companion object {
        const val MAX_ATTEMPTS = 3
    }
}
