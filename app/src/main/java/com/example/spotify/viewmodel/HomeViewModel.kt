package com.example.spotify.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.spotify.music.MusicRepository
import com.example.spotify.music.MusicTrack
import com.example.spotify.music.RecentTracksStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel @JvmOverloads constructor(
    application: Application,
    private val musicRepository: MusicRepository = MusicRepository()
) : AndroidViewModel(application) {

    private val _offlineTracks = MutableStateFlow<List<MusicTrack>>(emptyList())
    val offlineTracks = _offlineTracks.asStateFlow()

    private val _onlineHighlights = MutableStateFlow<List<MusicTrack>>(emptyList())
    val onlineHighlights = _onlineHighlights.asStateFlow()

    private val _recentTrackIds = MutableStateFlow(RecentTracksStore.getRecentIds(application))
    private val _recentCache = MutableStateFlow<Map<String, MusicTrack>>(emptyMap())

    val recentTracks: StateFlow<List<MusicTrack>> = combine(
        _offlineTracks,
        _onlineHighlights,
        _recentTrackIds,
        _recentCache
    ) { offlineTracks, onlineHighlights, recentIds, recentCache ->
        val catalog = linkedMapOf<String, MusicTrack>()
        (offlineTracks + onlineHighlights + recentCache.values).forEach { track ->
            catalog[track.id] = track
        }

        val resolvedRecentTracks = recentIds.mapNotNull(catalog::get)
        if (resolvedRecentTracks.isNotEmpty()) {
            resolvedRecentTracks
        } else {
            (offlineTracks + onlineHighlights)
                .distinctBy(MusicTrack::id)
                .take(DEFAULT_RECENT_COUNT)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = emptyList()
    )

    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    fun loadOfflineTracks() {
        viewModelScope.launch {
            _loading.value = true
            runCatching {
                musicRepository.loadOfflineTracks(getApplication())
            }.onSuccess { tracks ->
                _offlineTracks.value = tracks
                _error.value = null
            }.onFailure { error ->
                _error.value = error.localizedMessage ?: "Failed to load offline tracks."
            }
            _loading.value = false
        }
    }

    fun loadOnlineHighlights(query: String = DEFAULT_HIGHLIGHT_QUERY) {
        viewModelScope.launch {
            musicRepository.getFeaturedSongs(query)
                .onSuccess { tracks -> _onlineHighlights.value = tracks.take(DEFAULT_HIGHLIGHT_COUNT) }
                .onFailure { error ->
                    _error.value = error.localizedMessage ?: "Failed to load online highlights."
                }
        }
    }

    fun cacheRecentTrack(track: MusicTrack?) {
        track ?: return
        _recentCache.value = _recentCache.value + (track.id to track)
        refreshRecentTracks()
    }

    fun refreshRecentTracks() {
        _recentTrackIds.value = RecentTracksStore.getRecentIds(getApplication())
    }

    fun clearError() {
        _error.value = null
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val DEFAULT_RECENT_COUNT = 8
        const val DEFAULT_HIGHLIGHT_COUNT = 8
        const val DEFAULT_HIGHLIGHT_QUERY = "top hits"
    }
}
