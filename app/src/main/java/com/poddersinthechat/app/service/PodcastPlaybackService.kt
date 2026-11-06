package com.poddersinthechat.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.MediaStyleNotificationHelper
import coil.Coil
import coil.request.ImageRequest
import com.poddersinthechat.app.MainActivity
import com.poddersinthechat.app.PoddersApp
import com.poddersinthechat.app.R

class PodcastPlaybackService : MediaSessionService() {

    private var currentArtworkBitmap: Bitmap? = null
    private var currentArtworkUrl: String? = null

    override fun onCreate() {
        super.onCreate()
        activeServiceInstance = this
        createNotificationChannel()

        try {
            val session = PoddersApp.instance.playerManager.mediaSession
            if (!isSessionAdded(session)) {
                addSession(session)
            }
        } catch (_: Exception) {}
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return try {
            PoddersApp.instance.playerManager.mediaSession
        } catch (_: Exception) {
            null
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val playerManager = try {
            PoddersApp.instance.playerManager
        } catch (_: Exception) {
            null
        }

        if (playerManager != null) {
            val session = playerManager.mediaSession
            if (!isSessionAdded(session)) {
                addSession(session)
            }

            when (intent?.action) {
                ACTION_REWIND -> {
                    playerManager.skipBackward(15)
                    updateNotification()
                }
                ACTION_TOGGLE -> {
                    playerManager.togglePlayPause()
                    updateNotification()
                }
                ACTION_FORWARD -> {
                    playerManager.skipForward(30)
                    updateNotification()
                }
                ACTION_STOP -> {
                    playerManager.pause()
                    ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return START_NOT_STICKY
                }
                else -> {
                    updateNotification()
                }
            }
        }

        return super.onStartCommand(intent, flags, startId)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = try {
            PoddersApp.instance.playerManager.player
        } catch (_: Exception) {
            null
        }

        if (player == null || !player.playWhenReady || player.mediaItemCount == 0 || player.playbackState == Player.STATE_ENDED) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onDestroy() {
        activeServiceInstance = null
        super.onDestroy()
    }

    fun updateNotification() {
        val playerManager = try {
            PoddersApp.instance.playerManager
        } catch (_: Exception) {
            null
        } ?: return

        val episode = playerManager.currentEpisode.value ?: return
        val player = playerManager.player
        val isPlaying = player.isPlaying

        // Fetch artwork bitmap if needed
        val artworkUrl = episode.feedImageUrl
        if (!artworkUrl.isNullOrEmpty() && artworkUrl != currentArtworkUrl) {
            currentArtworkUrl = artworkUrl
            val imageRequest = ImageRequest.Builder(this)
                .data(artworkUrl)
                .allowHardware(false)
                .target { drawable ->
                    currentArtworkBitmap = (drawable as? BitmapDrawable)?.bitmap
                    // Re-post notification with loaded artwork
                    postNotification(episode.title, episode.feedTitle, isPlaying, playerManager.mediaSession)
                }
                .build()
            Coil.imageLoader(this).enqueue(imageRequest)
        }

        postNotification(episode.title, episode.feedTitle, isPlaying, playerManager.mediaSession)
    }

    private fun postNotification(
        episodeTitle: String,
        feedTitle: String,
        isPlaying: Boolean,
        mediaSession: MediaSession
    ) {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val rewindIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, PodcastPlaybackService::class.java).apply { action = ACTION_REWIND },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val toggleIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, PodcastPlaybackService::class.java).apply { action = ACTION_TOGGLE },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val forwardIntent = PendingIntent.getService(
            this,
            3,
            Intent(this, PodcastPlaybackService::class.java).apply { action = ACTION_FORWARD },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val rewindAction = NotificationCompat.Action.Builder(
            R.drawable.ic_replay_15,
            "Rewind 15s",
            rewindIntent
        ).build()

        val playPauseAction = NotificationCompat.Action.Builder(
            if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow,
            if (isPlaying) "Pause" else "Play",
            toggleIntent
        ).build()

        val forwardAction = NotificationCompat.Action.Builder(
            R.drawable.ic_forward_30,
            "Skip 30s",
            forwardIntent
        ).build()

        // MediaStyle linking the media session token directly
        // Actions in compact view: 0 (Rewind 15s), 1 (Play/Pause), 2 (Skip 30s)
        val mediaStyle = MediaStyleNotificationHelper.MediaStyle(mediaSession)
            .setShowActionsInCompactView(0, 1, 2)

        val builder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setStyle(mediaStyle)
            .setContentTitle(episodeTitle)
            .setContentText(feedTitle)
            .setSubText(feedTitle)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(contentIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(isPlaying)
            .addAction(rewindAction)
            .addAction(playPauseAction)
            .addAction(forwardAction)

        currentArtworkBitmap?.let {
            builder.setLargeIcon(it)
        }

        val notification = builder.build()

        if (isPlaying) {
            val foregroundServiceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            } else {
                0
            }
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, foregroundServiceType)
        } else {
            val notificationManager = NotificationManagerCompat.from(this)
            try {
                notificationManager.notify(NOTIFICATION_ID, notification)
            } catch (_: Exception) {}
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
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

        const val ACTION_REWIND = "com.poddersinthechat.app.action.REWIND"
        const val ACTION_TOGGLE = "com.poddersinthechat.app.action.TOGGLE"
        const val ACTION_FORWARD = "com.poddersinthechat.app.action.FORWARD"
        const val ACTION_STOP = "com.poddersinthechat.app.action.STOP"
        const val ACTION_UPDATE = "com.poddersinthechat.app.action.UPDATE"

        @Volatile
        var activeServiceInstance: PodcastPlaybackService? = null
            private set

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

        fun update(context: Context) {
            val service = activeServiceInstance
            if (service != null) {
                service.updateNotification()
            } else {
                val intent = Intent(context, PodcastPlaybackService::class.java).apply {
                    action = ACTION_UPDATE
                }
                try {
                    ContextCompat.startForegroundService(context, intent)
                } catch (_: Exception) {
                    try {
                        context.startService(intent)
                    } catch (_: Exception) {}
                }
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
