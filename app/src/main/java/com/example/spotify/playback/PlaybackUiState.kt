package com.example.spotify.playback

import androidx.media3.common.Player
import com.example.spotify.music.MusicTrack

data class PlaybackUiState(
    val queue: List<MusicTrack> = emptyList(),
    val currentTrack: MusicTrack? = null,
    val currentIndex: Int = -1,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val hasPrevious: Boolean = false,
    val hasNext: Boolean = false,
    val isShuffleOn: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF
)
