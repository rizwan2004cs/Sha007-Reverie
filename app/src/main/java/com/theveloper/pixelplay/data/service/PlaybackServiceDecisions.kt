package com.theveloper.pixelplay.data.service

import androidx.media3.common.Player

internal object PlaybackServiceDecisions {

    fun isPlaybackOngoing(player: Player?): Boolean {
        if (player == null) return false
        if (player.mediaItemCount <= 0) return false
        if (player.playbackState == Player.STATE_IDLE || player.playbackState == Player.STATE_ENDED) {
            return false
        }
        return player.playWhenReady || player.isPlaying
    }

    fun shouldKeepServiceAfterTaskRemoved(
        allowBackgroundPlayback: Boolean,
        player: Player?,
        hasPendingReconnectResume: Boolean,
    ): Boolean {
        if (!allowBackgroundPlayback) return false
        if (hasPendingReconnectResume) return true
        return isPlaybackOngoing(player)
    }

    fun shouldRunInForeground(
        startInForegroundRequired: Boolean,
        player: Player?,
        hasTemporaryForegroundLease: Boolean,
    ): Boolean {
        if (!startInForegroundRequired) return false
        return hasTemporaryForegroundLease || isPlaybackOngoing(player)
    }

    fun shouldReleaseTemporaryForeground(
        player: Player?,
        hasPendingReconnectResume: Boolean,
    ): Boolean {
        return !isPlaybackOngoing(player) && !hasPendingReconnectResume
    }
}
