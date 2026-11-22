package com.poddersinthechat.app

import android.app.Application
import com.poddersinthechat.app.data.SettingsStore
import com.poddersinthechat.app.data.db.AppDatabase
import com.poddersinthechat.app.data.repository.DownloadRepository
import com.poddersinthechat.app.data.repository.FeedRepository
import com.poddersinthechat.app.data.repository.RecommendationsRepository
import com.poddersinthechat.app.player.PodcastPlayerManager
import com.poddersinthechat.app.worker.FeedRefreshScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PoddersApp : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var feedRepository: FeedRepository
        private set

    lateinit var recommendationsRepository: RecommendationsRepository
        private set

    lateinit var settingsStore: SettingsStore
        private set

    lateinit var downloadRepository: DownloadRepository
        private set

    lateinit var playerManager: PodcastPlayerManager
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        instance = this

        database = AppDatabase.getInstance(this)
        feedRepository = FeedRepository(database.podcastDao(), this)
        recommendationsRepository = RecommendationsRepository(this)
        settingsStore = SettingsStore(this)
        downloadRepository = DownloadRepository(this, database.downloadDao(), settingsStore)
        playerManager = PodcastPlayerManager(this, feedRepository, settingsStore, downloadRepository)

        // Daily background feed refresh — schedule/hour/disabled per user settings
        FeedRefreshScheduler.applySettings(this, settingsStore)

        // Remove offline downloads past their retention period
        // (no-op when the user chose "never" auto-delete)
        appScope.launch(Dispatchers.IO) {
            val removed = downloadRepository.cleanupExpired()
            if (removed > 0) {
                android.util.Log.i(
                    "PoddersApp",
                    "Removed $removed expired offline download(s)"
                )
            }
        }
    }

    companion object {
        lateinit var instance: PoddersApp
            private set
    }
}
