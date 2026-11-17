package com.poddersinthechat.app

import android.app.Application
import com.poddersinthechat.app.data.db.AppDatabase
import com.poddersinthechat.app.data.repository.FeedRepository
import com.poddersinthechat.app.data.repository.RecommendationsRepository
import com.poddersinthechat.app.player.PodcastPlayerManager
import com.poddersinthechat.app.worker.FeedRefreshScheduler

class PoddersApp : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var feedRepository: FeedRepository
        private set

    lateinit var recommendationsRepository: RecommendationsRepository
        private set

    lateinit var playerManager: PodcastPlayerManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        database = AppDatabase.getInstance(this)
        feedRepository = FeedRepository(database.podcastDao(), this)
        recommendationsRepository = RecommendationsRepository(this)
        playerManager = PodcastPlayerManager(this, feedRepository)

        // Refresh all subscribed feeds in the background once a day (~12:00)
        FeedRefreshScheduler.scheduleDailyRefresh(this)
    }

    companion object {
        lateinit var instance: PoddersApp
            private set
    }
}
