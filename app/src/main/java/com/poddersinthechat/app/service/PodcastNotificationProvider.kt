package com.poddersinthechat.app.service

import android.content.Context
import android.os.Bundle
import androidx.media3.common.Player
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.poddersinthechat.app.R

class PodcastNotificationProvider(
    private val context: Context
) : DefaultMediaNotificationProvider(
    context,
    { PodcastPlaybackService.NOTIFICATION_ID },
    PodcastPlaybackService.NOTIFICATION_CHANNEL_ID,
    R.string.playback_notification_channel_name
) {

    override fun getMediaButtons(
        session: MediaSession,
        playerCommands: Player.Commands,
        customLayout: ImmutableList<CommandButton>,
        showPauseButton: Boolean
    ): ImmutableList<CommandButton> {
        val buttons = ImmutableList.builder<CommandButton>()

        // 1. Skip Back (-10s) - In compact view slot 0
        val seekBackBundle = Bundle().apply {
            putInt(COMMAND_KEY_COMPACT_VIEW_INDEX, 0)
        }
        buttons.add(
            CommandButton.Builder(CommandButton.ICON_SKIP_BACK_10)
                .setPlayerCommand(Player.COMMAND_SEEK_BACK)
                .setCustomIconResId(R.drawable.ic_replay_10)
                .setDisplayName("Rewind 10s")
                .setExtras(seekBackBundle)
                .build()
        )

        // 2. Play / Pause - In compact view slot 1
        val playPauseBundle = Bundle().apply {
            putInt(COMMAND_KEY_COMPACT_VIEW_INDEX, 1)
        }
        val icon = if (showPauseButton) CommandButton.ICON_PAUSE else CommandButton.ICON_PLAY
        val label = if (showPauseButton) "Pause" else "Play"
        buttons.add(
            CommandButton.Builder(icon)
                .setPlayerCommand(Player.COMMAND_PLAY_PAUSE)
                .setDisplayName(label)
                .setExtras(playPauseBundle)
                .build()
        )

        // 3. Skip Forward (+30s) - In compact view slot 2
        val seekForwardBundle = Bundle().apply {
            putInt(COMMAND_KEY_COMPACT_VIEW_INDEX, 2)
        }
        buttons.add(
            CommandButton.Builder(CommandButton.ICON_SKIP_FORWARD_30)
                .setPlayerCommand(Player.COMMAND_SEEK_FORWARD)
                .setCustomIconResId(R.drawable.ic_forward_30)
                .setDisplayName("Skip 30s")
                .setExtras(seekForwardBundle)
                .build()
        )

        return buttons.build()
    }
}
