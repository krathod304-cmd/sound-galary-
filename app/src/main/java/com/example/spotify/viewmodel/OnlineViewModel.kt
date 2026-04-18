package com.example.spotify.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.spotify.music.FirestoreRepository
import com.example.spotify.music.MusicRepository
import com.example.spotify.music.MusicTrack
import com.example.spotify.music.Playlist
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class OnlineViewModel(
    private val musicRepository: MusicRepository = MusicRepository(),
    private val firestoreRepository: FirestoreRepository = FirestoreRepository(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : ViewModel() {

    private val userId: String? = auth.currentUser?.uid

    private val _featuredTracks = MutableStateFlow<List<MusicTrack>>(emptyList())
    val featuredTracks = _featuredTracks.asStateFlow()

    private val _searchResults = MutableStateFlow<List<MusicTrack>>(emptyList())
    val searchResults = _searchResults.asStateFlow()

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState = _uiState.asStateFlow()

    private val _likedSongs = MutableStateFlow<List<MusicTrack>>(emptyList())
    val likedSongs = _likedSongs.asStateFlow()

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists = _playlists.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages = _messages.asSharedFlow()

    init {
        observeLibrary()
        loadFeaturedSongs()
    }

    fun loadFeaturedSongs(query: String = DEFAULT_FEATURED_QUERY) {
        viewModelScope.launch {
            musicRepository.getFeaturedSongs(query)
                .onSuccess { tracks -> _featuredTracks.value = tracks }
                .onFailure { error -> emitMessage(error.localizedMessage ?: "Failed to load highlights.") }
        }
    }

    fun searchSongs(query: String) {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isBlank()) {
            _searchResults.value = emptyList()
            _uiState.value = UiState.Idle
            return
        }

        viewModelScope.launch {
            _uiState.value = UiState.Loading
            musicRepository.searchOnlineSongs(normalizedQuery)
                .onSuccess { tracks ->
                    _searchResults.value = tracks
                    _uiState.value = UiState.Success
                }
                .onFailure { error ->
                    _searchResults.value = emptyList()
                    _uiState.value = UiState.Error(
                        error.localizedMessage ?: "Failed to search songs."
                    )
                }
        }
    }

    fun likeSong(track: MusicTrack) {
        val resolvedUserId = userId ?: return emitMessage("Please sign in to like songs.")
        viewModelScope.launch {
            runCatching {
                firestoreRepository.likeSong(resolvedUserId, track)
            }.onFailure { error ->
                emitMessage(error.localizedMessage ?: "Failed to like the song.")
            }
        }
    }

    fun unlikeSong(saavnId: String) {
        val resolvedUserId = userId ?: return emitMessage("Please sign in to manage likes.")
        viewModelScope.launch {
            runCatching {
                firestoreRepository.unlikeSong(resolvedUserId, saavnId)
            }.onFailure { error ->
                emitMessage(error.localizedMessage ?: "Failed to remove the song from likes.")
            }
        }
    }

    fun checkIsLiked(saavnId: String): Flow<Boolean> {
        return likedSongs
            .map { songs -> songs.any { track -> track.saavnId == saavnId || track.id == saavnId } }
            .distinctUntilChanged()
    }

    fun createPlaylist(name: String) {
        val resolvedUserId = userId ?: return emitMessage("Please sign in to create playlists.")
        val normalizedName = name.trim()
        if (normalizedName.isBlank()) {
            emitMessage("Playlist name cannot be empty.")
            return
        }

        viewModelScope.launch {
            runCatching {
                firestoreRepository.createPlaylist(resolvedUserId, normalizedName)
            }.onFailure { error ->
                emitMessage(error.localizedMessage ?: "Failed to create playlist.")
            }
        }
    }

    fun addToPlaylist(playlistId: String, track: MusicTrack) {
        val resolvedUserId = userId ?: return emitMessage("Please sign in to manage playlists.")
        viewModelScope.launch {
            runCatching {
                firestoreRepository.addToPlaylist(resolvedUserId, playlistId, track)
            }.onFailure { error ->
                emitMessage(error.localizedMessage ?: "Failed to add the song to the playlist.")
            }
        }
    }

    fun removeFromPlaylist(playlistId: String, saavnId: String) {
        val resolvedUserId = userId ?: return emitMessage("Please sign in to manage playlists.")
        viewModelScope.launch {
            runCatching {
                firestoreRepository.removeFromPlaylist(resolvedUserId, playlistId, saavnId)
            }.onFailure { error ->
                emitMessage(error.localizedMessage ?: "Failed to remove the song from the playlist.")
            }
        }
    }

    fun getPlaylistSongs(playlistId: String): Flow<List<MusicTrack>> {
        val resolvedUserId = userId ?: return flowOf(emptyList())
        return firestoreRepository.getPlaylistSongs(resolvedUserId, playlistId)
    }

    private fun observeLibrary() {
        val resolvedUserId = userId ?: return

        viewModelScope.launch {
            firestoreRepository.getLikedSongs(resolvedUserId).collect { songs ->
                _likedSongs.value = songs
            }
        }

        viewModelScope.launch {
            firestoreRepository.getPlaylists(resolvedUserId).collect { playlists ->
                _playlists.value = playlists
            }
        }
    }

    private fun emitMessage(message: String) {
        _messages.tryEmit(message)
    }

    private companion object {
        const val DEFAULT_FEATURED_QUERY = "new releases"
    }
}
