package com.example.spotify.playback

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.spotify.music.MusicRepository
import com.example.spotify.music.MusicTrack
import com.example.spotify.music.RecentTracksStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object PlaybackManager {

    private var appContext: Context? = null
    private var exoPlayer: ExoPlayer? = null
    private var queue: List<MusicTrack> = emptyList()
    private val repository = MusicRepository()
    private val playbackScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var lastRetriedSaavnId: String? = null

    private val handler = Handler(Looper.getMainLooper())
    private val _uiState = MutableLiveData(PlaybackUiState())
    val uiState: LiveData<PlaybackUiState> = _uiState
    private val _playbackMessage = MutableLiveData<String?>(null)
    val playbackMessage: LiveData<String?> = _playbackMessage

    private val progressUpdater = object : Runnable {
        override fun run() {
            publishState()
            handler.postDelayed(this, PROGRESS_UPDATE_MS)
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            handlePlaybackError(error)
        }

        override fun onEvents(player: Player, events: Player.Events) {
            markCurrentTrackAsRecent()
            publishState()
        }
    }

    fun initialize(context: Context): ExoPlayer {
        appContext = context.applicationContext
        return exoPlayer ?: ExoPlayer.Builder(context.applicationContext).build().also { player ->
            exoPlayer = player
            player.repeatMode = Player.REPEAT_MODE_OFF
            player.addListener(playerListener)
            handler.removeCallbacks(progressUpdater)
            handler.post(progressUpdater)
            publishState()
        }
    }

    fun playerOrNull(): ExoPlayer? = exoPlayer

    fun playTracks(context: Context, tracks: List<MusicTrack>, startIndex: Int): Result<Unit> {
        initialize(context)
        return playQueue(tracks, startIndex)
    }

    fun playLocalTrack(context: Context, track: MusicTrack, tracks: List<MusicTrack>): Result<Unit> {
        initialize(context)
        val startIndex = tracks.indexOfFirst { candidate -> candidate.id == track.id }.coerceAtLeast(0)
        return playQueue(tracks, startIndex)
    }

    fun playOnlineTrack(track: MusicTrack): Result<Unit> {
        return playQueue(listOf(track), 0)
    }

    fun playQueue(tracks: List<MusicTrack>, startIndex: Int): Result<Unit> {
        val player = exoPlayer ?: appContext?.let(::initialize) ?: run {
            emitPlaybackMessage("Player is not ready yet.")
            return Result.failure(IllegalStateException("Player is not ready yet."))
        }
        if (tracks.isEmpty() || startIndex !in tracks.indices) {
            emitPlaybackMessage("No playable track was selected.")
            return Result.failure(IllegalArgumentException("No playable track was selected."))
        }

        return runCatching {
            queue = tracks
            val mediaItems = tracks.map(::createMediaItem)
            player.clearMediaItems()
            player.addMediaItems(mediaItems)
            player.seekTo(startIndex, 0L)
            player.prepare()
            player.playWhenReady = true
            ensureServiceRunning()
            lastRetriedSaavnId = null
            markCurrentTrackAsRecent()
            publishState()
        }.onFailure { error ->
            player.stop()
            queue = emptyList()
            emitPlaybackMessage(mapPlaybackError(error))
            publishState()
        }
    }

    fun togglePlayback(context: Context? = null): Result<Unit> {
        val player = exoPlayer ?: run {
            emitPlaybackMessage("Player is not ready yet.")
            return Result.failure(IllegalStateException("Player is not ready yet."))
        }

        return runCatching {
            if (context != null) {
                initialize(context)
            }
            if (player.isPlaying) {
                player.pause()
            } else {
                ensureServiceRunning()
                player.play()
            }
            publishState()
        }.onFailure { error ->
            emitPlaybackMessage(mapPlaybackError(error))
            publishState()
        }
    }

    fun seekTo(positionMs: Long) {
        exoPlayer?.seekTo(positionMs.coerceAtLeast(0L))
        publishState()
    }

    fun skipNext(): Result<Unit> {
        val player = exoPlayer ?: run {
            emitPlaybackMessage("Player is not ready yet.")
            return Result.failure(IllegalStateException("Player is not ready yet."))
        }

        return runCatching {
            player.seekToNextMediaItem()
            player.playWhenReady = true
            lastRetriedSaavnId = null
            markCurrentTrackAsRecent()
            publishState()
        }.onFailure { error ->
            emitPlaybackMessage(mapPlaybackError(error))
            publishState()
        }
    }

    fun skipPrevious(): Result<Unit> {
        val player = exoPlayer ?: run {
            emitPlaybackMessage("Player is not ready yet.")
            return Result.failure(IllegalStateException("Player is not ready yet."))
        }

        return runCatching {
            player.seekToPreviousMediaItem()
            player.playWhenReady = true
            lastRetriedSaavnId = null
            markCurrentTrackAsRecent()
            publishState()
        }.onFailure { error ->
            emitPlaybackMessage(mapPlaybackError(error))
            publishState()
        }
    }

    fun toggleShuffle() {
        exoPlayer?.let { player ->
            player.shuffleModeEnabled = !player.shuffleModeEnabled
            publishState()
        }
    }

    fun toggleRepeat() {
        exoPlayer?.let { player ->
            player.repeatMode = when (player.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ONE
                Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ALL
                else -> Player.REPEAT_MODE_OFF
            }
            publishState()
        }
    }

    fun clearPlaybackMessage() {
        _playbackMessage.postValue(null)
    }

    fun currentQueue(): List<MusicTrack> = queue

    private fun createMediaItem(track: MusicTrack): MediaItem {
        val playbackUri = track.streamUrl
            ?.takeIf { it.isNotBlank() }
            ?.let(Uri::parse)
            ?: track.playbackUri

        return MediaItem.Builder()
            .setMediaId(track.id)
            .setUri(playbackUri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.artistName ?: track.artist)
                    .setAlbumTitle(track.album)
                    .setArtworkUri((track.imageUrl ?: track.coverUrl)?.let(Uri::parse))
                    .build()
            )
            .build()
    }

    private fun ensureServiceRunning() {
        val context = appContext ?: return
        runCatching {
            ContextCompat.startForegroundService(
                context,
                Intent(context, PlaybackService::class.java)
            )
        }.onFailure { error ->
            emitPlaybackMessage(
                when (error) {
                    is SecurityException -> "Playback started, but notification controls are blocked. Allow notifications and try again."
                    else -> "Playback started, but background controls could not start."
                }
            )
        }
    }

    private fun handlePlaybackError(error: PlaybackException) {
        val currentTrack = _uiState.value?.currentTrack
        val saavnId = currentTrack?.saavnId
        if (currentTrack?.isOnline == true && !saavnId.isNullOrBlank() && saavnId != lastRetriedSaavnId) {
            lastRetriedSaavnId = saavnId
            playbackScope.launch {
                repository.getSongById(saavnId)
                    .onSuccess { refreshedTrack ->
                        val refreshedIndex = queue.indexOfFirst { track -> track.id == currentTrack.id }
                        if (refreshedIndex >= 0) {
                            queue = queue.toMutableList().apply { set(refreshedIndex, refreshedTrack) }
                            playQueue(queue, refreshedIndex)
                            emitPlaybackMessage("Stream refreshed.")
                        } else {
                            skipOrStop(mapPlaybackError(error))
                        }
                    }
                    .onFailure {
                        skipOrStop(mapPlaybackError(error))
                    }
            }
            return
        }

        skipOrStop(mapPlaybackError(error))
    }

    private fun skipOrStop(message: String) {
        emitPlaybackMessage(message)
        appContext?.let { context ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }

        val player = exoPlayer ?: return
        if (player.hasNextMediaItem()) {
            player.seekToNextMediaItem()
            player.prepare()
            player.playWhenReady = true
        } else {
            player.pause()
        }
        publishState()
    }

    private fun emitPlaybackMessage(message: String) {
        _playbackMessage.postValue(message)
    }

    private fun mapPlaybackError(error: Throwable): String {
        return when (error) {
            is PlaybackException -> "Playback failed for this stream."
            is SecurityException -> "Playback was blocked by a device permission or foreground service restriction."
            else -> error.localizedMessage ?: "Playback failed for this track."
        }
    }

    private fun publishState() {
        val player = exoPlayer
        if (player == null || queue.isEmpty()) {
            _uiState.postValue(PlaybackUiState())
            return
        }

        val index = player.currentMediaItemIndex
            .takeIf { it in queue.indices }
            ?: queue.indexOfFirst { track -> track.id == player.currentMediaItem?.mediaId }

        val currentTrack = queue.getOrNull(index)
        val duration = when {
            player.duration > 0L -> player.duration
            currentTrack != null -> currentTrack.durationMs
            else -> 0L
        }

        _uiState.postValue(
            PlaybackUiState(
                queue = queue,
                currentTrack = currentTrack,
                currentIndex = index,
                isPlaying = player.isPlaying,
                isBuffering = player.playbackState == Player.STATE_BUFFERING,
                positionMs = player.currentPosition.coerceAtLeast(0L),
                durationMs = duration.coerceAtLeast(0L),
                hasPrevious = player.hasPreviousMediaItem(),
                hasNext = player.hasNextMediaItem(),
                isShuffleOn = player.shuffleModeEnabled,
                repeatMode = player.repeatMode
            )
        )
    }

    private fun markCurrentTrackAsRecent() {
        val context = appContext ?: return
        val currentId = exoPlayer?.currentMediaItem?.mediaId ?: return
        RecentTracksStore.markPlayed(context, currentId)
    }

    private const val PROGRESS_UPDATE_MS = 500L
}
