package com.poddersinthechat.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.poddersinthechat.app.PoddersApp
import com.poddersinthechat.app.R

class PodcastPlaybackService : MediaSessionService() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        val notificationProvider = PodcastNotificationProvider(this)
        setMediaNotificationProvider(notificationProvider)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return try {
            PoddersApp.instance.playerManager.mediaSession
        } catch (_: Exception) {
            null
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = try {
            PoddersApp.instance.playerManager.player
        } catch (_: Exception) {
            null
        }

        if (player == null || !player.playWhenReady || player.mediaItemCount == 0 || player.playbackState == Player.STATE_ENDED) {
            stopSelf()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.playback_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.playback_notification_channel_description)
                setShowBadge(false)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "podders_playback_channel"
        const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            val intent = Intent(context, PodcastPlaybackService::class.java)
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (_: Exception) {
                try {
                    context.startService(intent)
                } catch (_: Exception) {}
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, PodcastPlaybackService::class.java)
            try {
                context.stopService(intent)
            } catch (_: Exception) {}
        }
    }
}
