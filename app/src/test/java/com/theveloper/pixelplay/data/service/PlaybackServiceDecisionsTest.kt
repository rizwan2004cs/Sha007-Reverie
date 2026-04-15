package com.theveloper.pixelplay.data.service

import androidx.media3.common.Player
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlaybackServiceDecisionsTest {

    @Test
    fun `buffering playback with queued media counts as ongoing`() {
        val player = player(
            mediaItemCount = 5,
            playbackState = Player.STATE_BUFFERING,
            playWhenReady = true,
            isPlaying = false
        )

        assertTrue(PlaybackServiceDecisions.isPlaybackOngoing(player))
    }

    @Test
    fun `pending headset reconnect keeps service alive after task removal`() {
        val pausedPlayer = player(
            mediaItemCount = 1,
            playbackState = Player.STATE_READY,
            playWhenReady = false,
            isPlaying = false
        )

        assertTrue(
            PlaybackServiceDecisions.shouldKeepServiceAfterTaskRemoved(
                allowBackgroundPlayback = true,
                player = pausedPlayer,
                hasPendingReconnectResume = true
            )
        )
    }

    @Test
    fun `temporary foreground lease keeps notification eligible for foreground`() {
        val idlePlayer = player(
            mediaItemCount = 0,
            playbackState = Player.STATE_IDLE,
            playWhenReady = false,
            isPlaying = false
        )

        assertTrue(
            PlaybackServiceDecisions.shouldRunInForeground(
                startInForegroundRequired = true,
                player = idlePlayer,
                hasTemporaryForegroundLease = true
            )
        )
    }

    @Test
    fun `inactive player releases temporary foreground when no reconnect is pending`() {
        val pausedPlayer = player(
            mediaItemCount = 1,
            playbackState = Player.STATE_READY,
            playWhenReady = false,
            isPlaying = false
        )

        assertTrue(
            PlaybackServiceDecisions.shouldReleaseTemporaryForeground(
                player = pausedPlayer,
                hasPendingReconnectResume = false
            )
        )
        assertFalse(
            PlaybackServiceDecisions.shouldReleaseTemporaryForeground(
                player = pausedPlayer,
                hasPendingReconnectResume = true
            )
        )
    }

    private fun player(
        mediaItemCount: Int,
        playbackState: Int,
        playWhenReady: Boolean,
        isPlaying: Boolean
    ): Player {
        return mockk(relaxed = true) {
            every { this@mockk.mediaItemCount } returns mediaItemCount
            every { this@mockk.playbackState } returns playbackState
            every { this@mockk.playWhenReady } returns playWhenReady
            every { this@mockk.isPlaying } returns isPlaying
        }
    }
}
