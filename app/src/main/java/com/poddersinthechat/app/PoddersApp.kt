package com.poddersinthechat.app

import android.app.Application
import com.poddersinthechat.app.data.db.AppDatabase
import com.poddersinthechat.app.data.repository.FeedRepository
import com.poddersinthechat.app.player.PodcastPlayerManager

class PoddersApp : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var feedRepository: FeedRepository
        private set

    lateinit var playerManager: PodcastPlayerManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        database = AppDatabase.getInstance(this)
        feedRepository = FeedRepository(database.podcastDao())
        playerManager = PodcastPlayerManager(this, feedRepository)
    }

    companion object {
        lateinit var instance: PoddersApp
            private set
    }
}
