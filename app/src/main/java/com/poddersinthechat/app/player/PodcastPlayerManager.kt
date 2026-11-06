package com.poddersinthechat.app.player

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.poddersinthechat.app.MainActivity
import com.poddersinthechat.app.R
import com.poddersinthechat.app.data.model.Episode
import com.poddersinthechat.app.data.repository.FeedRepository
import com.poddersinthechat.app.service.PodcastPlaybackService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class PodcastPlayerManager(
    private val context: Context,
    private val feedRepository: FeedRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Base ExoPlayer configured with WakeMode and AudioAttributes
    private val basePlayer: ExoPlayer = ExoPlayer.Builder(context)
        .setSeekBackIncrementMs(10_000)
        .setSeekForwardIncrementMs(30_000)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                .setUsage(C.USAGE_MEDIA)
                .build(),
            /* handleAudioFocus = */ true
        )
        .setHandleAudioBecomingNoisy(true)
        .setWakeMode(C.WAKE_MODE_NETWORK)
        .build().apply {
            playWhenReady = true
        }

    // ForwardingPlayer that suppresses SEEK_TO_PREVIOUS and SEEK_TO_NEXT
    // so Android 13+ System Media Controls replaces the "previous track" button with Rewind and Fast Forward
    val player: Player = object : ForwardingPlayer(basePlayer) {
        override fun getAvailableCommands(): Player.Commands {
            return super.getAvailableCommands().buildUpon()
                .remove(Player.COMMAND_SEEK_TO_PREVIOUS)
                .remove(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                .remove(Player.COMMAND_SEEK_TO_NEXT)
                .remove(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                .add(Player.COMMAND_SEEK_BACK)
                .add(Player.COMMAND_SEEK_FORWARD)
                .add(Player.COMMAND_PLAY_PAUSE)
                .add(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
                .build()
        }

        override fun isCommandAvailable(command: Int): Boolean {
            return when (command) {
                Player.COMMAND_SEEK_TO_PREVIOUS,
                Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_NEXT,
                Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> false
                Player.COMMAND_SEEK_BACK,
                Player.COMMAND_SEEK_FORWARD,
                Player.COMMAND_PLAY_PAUSE,
                Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM -> true
                else -> super.isCommandAvailable(command)
            }
        }

        override fun seekBack() {
            basePlayer.seekBack()
        }

        override fun seekForward() {
            basePlayer.seekForward()
        }
    }

    val customActionRewind = SessionCommand(CUSTOM_ACTION_REWIND_10, Bundle.EMPTY)
    val customActionForward = SessionCommand(CUSTOM_ACTION_FORWARD_30, Bundle.EMPTY)

    private val rewindCustomButton = CommandButton.Builder()
        .setSessionCommand(customActionRewind)
        .setIconResId(R.drawable.ic_replay_10)
        .setCustomIconResId(R.drawable.ic_replay_10)
        .setDisplayName("Rewind 10s")
        .setEnabled(true)
        .build()

    private val forwardCustomButton = CommandButton.Builder()
        .setSessionCommand(customActionForward)
        .setIconResId(R.drawable.ic_forward_30)
        .setCustomIconResId(R.drawable.ic_forward_30)
        .setDisplayName("Forward 30s")
        .setEnabled(true)
        .build()

    // MediaSession linked to the ForwardingPlayer with open-app pending intent and custom layout
    val mediaSession: MediaSession = run {
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val sessionActivity = PendingIntent.getActivity(
            context,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        MediaSession.Builder(context, player)
            .setSessionActivity(sessionActivity)
            .setCustomLayout(listOf(rewindCustomButton, forwardCustomButton))
            .setCallback(object : MediaSession.Callback {
                override fun onConnect(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo
                ): MediaSession.ConnectionResult {
                    val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                        .add(customActionRewind)
                        .add(customActionForward)
                        .build()

                    val playerCommands = MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon()
                        .remove(Player.COMMAND_SEEK_TO_PREVIOUS)
                        .remove(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                        .remove(Player.COMMAND_SEEK_TO_NEXT)
                        .remove(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                        .add(Player.COMMAND_SEEK_BACK)
                        .add(Player.COMMAND_SEEK_FORWARD)
                        .add(Player.COMMAND_PLAY_PAUSE)
                        .add(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
                        .build()

                    return MediaSession.ConnectionResult.accept(
                        sessionCommands,
                        playerCommands
                    )
                }

                override fun onCustomCommand(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    customCommand: SessionCommand,
                    args: Bundle
                ): ListenableFuture<SessionResult> {
                    when (customCommand.customAction) {
                        CUSTOM_ACTION_REWIND_10 -> {
                            skipBackward(10)
                        }
                        CUSTOM_ACTION_FORWARD_30 -> {
                            skipForward(30)
                        }
                    }
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
            })
            .build().apply {
                setCustomLayout(listOf(rewindCustomButton, forwardCustomButton))
            }
    }

    private val _currentEpisode = MutableStateFlow<Episode?>(null)
    val currentEpisode: StateFlow<Episode?> = _currentEpisode.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _playbackPositionMs = MutableStateFlow(0L)
    val playbackPositionMs: StateFlow<Long> = _playbackPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private var progressTrackingJob: Job? = null

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
                if (isPlaying) {
                    startProgressTracker()
                    PodcastPlaybackService.start(context)
                } else {
                    stopProgressTracker()
                    persistCurrentProgress()
                }
                PodcastPlaybackService.update(context)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                _isLoading.value = (playbackState == Player.STATE_BUFFERING)
                if (playbackState == Player.STATE_READY) {
                    val dur = player.duration
                    if (dur > 0) {
                        _durationMs.value = dur
                    }
                } else if (playbackState == Player.STATE_ENDED) {
                    handlePlaybackEnded()
                }
                PodcastPlaybackService.update(context)
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                _isLoading.value = false
                _isPlaying.value = false
                PodcastPlaybackService.update(context)
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                _playbackPositionMs.value = player.currentPosition
                persistCurrentProgress()
                PodcastPlaybackService.update(context)
            }
        })
    }

    fun playEpisode(episode: Episode) {
        val current = _currentEpisode.value
        if (current?.id == episode.id) {
            togglePlayPause()
            return
        }

        // Save progress for previous episode before switching
        persistCurrentProgress()

        _currentEpisode.value = episode
        _durationMs.value = if (episode.totalDurationMs > 0) episode.totalDurationMs else (episode.durationSeconds * 1000L)
        _playbackPositionMs.value = episode.playbackPositionMs

        // Clear NEW badge once played
        if (episode.isNew) {
            scope.launch(Dispatchers.IO) {
                feedRepository.clearNewBadge(episode.id)
            }
        }

        val metadata = MediaMetadata.Builder()
            .setTitle(episode.title)
            .setArtist(episode.feedTitle)
            .setDisplayTitle(episode.title)
            .setArtworkUri(if (!episode.feedImageUrl.isNullOrEmpty()) Uri.parse(episode.feedImageUrl) else null)
            .build()

        val mediaItem = MediaItem.Builder()
            .setMediaId(episode.id)
            .setUri(episode.audioUrl)
            .setMediaMetadata(metadata)
            .build()

        player.setMediaItem(mediaItem)

        // Resume from saved position if not already completed and has reasonable start position
        val resumePos = if (episode.isCompleted) 0L else episode.playbackPositionMs
        if (resumePos > 0L) {
            player.seekTo(resumePos)
        } else {
            player.seekTo(0L)
        }

        // Ensure playback service is running for notification and background execution
        PodcastPlaybackService.start(context)

        player.prepare()
        player.play()
        PodcastPlaybackService.update(context)
    }

    fun togglePlayPause() {
        if (player.isPlaying) {
            pause()
        } else {
            resume()
        }
    }

    fun pause() {
        player.pause()
        persistCurrentProgress()
        PodcastPlaybackService.update(context)
    }

    fun resume() {
        if (player.playbackState == Player.STATE_IDLE && _currentEpisode.value != null) {
            player.prepare()
        }
        PodcastPlaybackService.start(context)
        player.play()
        PodcastPlaybackService.update(context)
    }

    fun seekTo(positionMs: Long) {
        val safePos = positionMs.coerceIn(0L, _durationMs.value.coerceAtLeast(1L))
        player.seekTo(safePos)
        _playbackPositionMs.value = safePos
        persistCurrentProgress()
        PodcastPlaybackService.update(context)
    }

    fun skipForward(seconds: Int = 30) {
        val target = player.currentPosition + (seconds * 1000L)
        seekTo(target)
    }

    fun skipBackward(seconds: Int = 10) {
        val target = (player.currentPosition - (seconds * 1000L)).coerceAtLeast(0L)
        seekTo(target)
    }

    fun setPlaybackSpeed(speed: Float) {
        _playbackSpeed.value = speed
        player.playbackParameters = PlaybackParameters(speed)
    }

    fun cyclePlaybackSpeed() {
        val speeds = listOf(1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
        val current = _playbackSpeed.value
        val nextIdx = (speeds.indexOfFirst { it == current } + 1) % speeds.size
        setPlaybackSpeed(speeds[nextIdx])
    }

    private fun startProgressTracker() {
        stopProgressTracker()
        progressTrackingJob = scope.launch {
            while (isActive) {
                val currentPos = player.currentPosition
                val dur = player.duration
                if (dur > 0) {
                    _durationMs.value = dur
                }
                _playbackPositionMs.value = currentPos

                // Persist progress every 2 seconds to SQLite
                persistCurrentProgress()
                delay(1000L)
            }
        }
    }

    private fun stopProgressTracker() {
        progressTrackingJob?.cancel()
        progressTrackingJob = null
    }

    private fun persistCurrentProgress() {
        val episode = _currentEpisode.value ?: return
        val pos = player.currentPosition
        val dur = if (player.duration > 0) player.duration else _durationMs.value
        val isCompleted = dur > 0 && pos >= (dur * 0.95f)

        scope.launch(Dispatchers.IO) {
            feedRepository.updatePlaybackProgress(
                episodeId = episode.id,
                positionMs = pos,
                totalDurationMs = dur,
                isCompleted = isCompleted
            )
        }
    }

    private fun handlePlaybackEnded() {
        val episode = _currentEpisode.value ?: return
        val dur = _durationMs.value
        scope.launch(Dispatchers.IO) {
            feedRepository.updatePlaybackProgress(
                episodeId = episode.id,
                positionMs = dur,
                totalDurationMs = dur,
                isCompleted = true
            )
        }
        _playbackPositionMs.value = dur
        _isPlaying.value = false
    }

    fun release() {
        stopProgressTracker()
        persistCurrentProgress()
        mediaSession.release()
        basePlayer.release()
        PodcastPlaybackService.stop(context)
    }

    companion object {
        const val CUSTOM_ACTION_REWIND_10 = "com.poddersinthechat.app.action.REWIND_10"
        const val CUSTOM_ACTION_FORWARD_30 = "com.poddersinthechat.app.action.FORWARD_30"
    }
}
