package com.example.spotify

import android.Manifest
import android.app.DownloadManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.SeekBar
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.Player
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.spotify.databinding.ActivityOnlineBinding
import com.example.spotify.music.DownloadedRemoteTracksStore
import com.example.spotify.music.MusicTrack
import com.example.spotify.music.Playlist
import com.example.spotify.playback.PlaybackManager
import com.example.spotify.playback.PlaybackUiState
import com.example.spotify.ui.OnlineHighlightAdapter
import com.example.spotify.ui.OnlineTracksAdapter
import com.example.spotify.ui.PlaylistAdapter
import com.example.spotify.ui.bindTrackArtwork
import com.example.spotify.ui.toPlaybackTime
import com.example.spotify.viewmodel.OnlineViewModel
import com.example.spotify.viewmodel.UiState
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class OnlineActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnlineBinding
    private val onlineViewModel: OnlineViewModel by viewModels()

    private val featuredAdapter = OnlineHighlightAdapter(
        onTrackClick = { track, _ -> playTrack(track, featuredTracks) },
        onLongClick = ::showTrackActions,
        onLikeClick = ::toggleLike
    )
    private val searchResultsAdapter = OnlineTracksAdapter(
        onTrackClick = { track, _ -> playTrack(track, searchResults) },
        onLongClick = ::showTrackActions,
        onLikeClick = ::toggleLike,
        onDownloadClick = ::downloadTrack
    )
    private val likedSongsAdapter = OnlineTracksAdapter(
        onTrackClick = { track, _ -> playTrack(track, likedSongs) },
        onLongClick = ::showTrackActions,
        onLikeClick = ::toggleLike,
        onDownloadClick = ::downloadTrack
    )
    private val playlistAdapter = PlaylistAdapter(::openPlaylist)

    private var featuredTracks: List<MusicTrack> = emptyList()
    private var searchResults: List<MusicTrack> = emptyList()
    private var likedSongs: List<MusicTrack> = emptyList()
    private var playlists: List<Playlist> = emptyList()
    private var playbackState: PlaybackUiState = PlaybackUiState()
    private var downloadedTrackIds: Set<String> = emptySet()
    private var currentQuery: String = ""
    private var isSeeking: Boolean = false
    private var searchJob: Job? = null

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            showMessage(getString(R.string.notification_permission_denied_message))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnlineBinding.inflate(layoutInflater)
        setContentView(binding.root)

        downloadedTrackIds = DownloadedRemoteTracksStore.getDownloadedIds(this)
        PlaybackManager.initialize(this)
        setupToolbar()
        setupSearch()
        setupLists()
        setupPlayerControls()
        observeViewModel()
        observePlayback()
        updateNetworkState()
    }

    private fun setupToolbar() {
        binding.backButton.setOnClickListener { finish() }
        binding.createPlaylistButton.setOnClickListener { showCreatePlaylistDialog() }
    }

    private fun setupSearch() {
        binding.onlineSearchView.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                scheduleSearch(query.orEmpty())
                binding.onlineSearchView.clearFocus()
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                scheduleSearch(newText.orEmpty())
                return true
            }
        })
    }

    private fun setupLists() {
        binding.recentlyAddedRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@OnlineActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = featuredAdapter
        }
        binding.onlineTracksRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@OnlineActivity)
            adapter = searchResultsAdapter
        }
        binding.likedSongsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@OnlineActivity)
            adapter = likedSongsAdapter
        }
        binding.playlistsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@OnlineActivity)
            adapter = playlistAdapter
        }
    }

    private fun setupPlayerControls() {
        binding.playerToggleButton.setOnClickListener {
            requestNotificationPermissionIfNeeded()
            PlaybackManager.togglePlayback(this)
        }
        binding.playerNextButton.setOnClickListener {
            requestNotificationPermissionIfNeeded()
            PlaybackManager.skipNext()
        }
        binding.playerPreviousButton.setOnClickListener {
            requestNotificationPermissionIfNeeded()
            PlaybackManager.skipPrevious()
        }
        binding.playerShuffleButton.setOnClickListener {
            PlaybackManager.toggleShuffle()
        }
        binding.playerRepeatButton.setOnClickListener {
            PlaybackManager.toggleRepeat()
        }
        binding.playerLikeButton.setOnClickListener {
            playbackState.currentTrack?.let(::toggleLike)
        }
        binding.playerSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) = Unit

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                isSeeking = true
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val duration = playbackState.durationMs
                val progress = seekBar?.progress ?: 0
                if (duration > 0L) {
                    PlaybackManager.seekTo((duration * progress) / 1000L)
                }
                isSeeking = false
            }
        })
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    onlineViewModel.featuredTracks.collect { tracks ->
                        featuredTracks = applyDownloadedState(tracks)
                        featuredAdapter.submitList(featuredTracks)
                        binding.recentlyAddedRecyclerView.isVisible = featuredTracks.isNotEmpty()
                    }
                }

                launch {
                    onlineViewModel.searchResults.collect { tracks ->
                        searchResults = applyDownloadedState(tracks)
                        searchResultsAdapter.submitList(searchResults)
                        updateSearchEmptyState()
                    }
                }

                launch {
                    onlineViewModel.uiState.collect { state ->
                        binding.loadingView.root.isVisible = state is UiState.Loading
                        when (state) {
                            UiState.Idle -> {
                                binding.onlineStatusText.text = getString(R.string.online_badge)
                                updateSearchEmptyState()
                            }

                            UiState.Loading -> Unit

                            UiState.Success -> {
                                binding.onlineStatusText.text = getString(
                                    R.string.online_count_badge,
                                    searchResults.size
                                )
                                updateSearchEmptyState()
                            }

                            is UiState.Error -> {
                                binding.onlineStatusText.text = getString(R.string.online_badge)
                                updateSearchEmptyState()
                                showMessage(state.message)
                            }
                        }
                    }
                }

                launch {
                    onlineViewModel.likedSongs.collect { tracks ->
                        likedSongs = applyDownloadedState(tracks)
                        featuredAdapter.updateLikedSongs(likedSongs)
                        searchResultsAdapter.updateLikedSongs(likedSongs)
                        likedSongsAdapter.updateLikedSongs(likedSongs)
                        likedSongsAdapter.submitList(likedSongs)
                        binding.likedSongsEmptyText.isVisible = likedSongs.isEmpty()
                        binding.likedSongsRecyclerView.isVisible = likedSongs.isNotEmpty()
                        updatePlayer(playbackState)
                    }
                }

                launch {
                    onlineViewModel.playlists.collect { items ->
                        playlists = items
                        playlistAdapter.submitList(items)
                        binding.playlistsEmptyText.isVisible = items.isEmpty()
                        binding.playlistsRecyclerView.isVisible = items.isNotEmpty()
                    }
                }

                launch {
                    onlineViewModel.messages.collect { message ->
                        showMessage(message)
                    }
                }
            }
        }
    }

    private fun observePlayback() {
        PlaybackManager.uiState.observe(this) { state ->
            playbackState = state
            searchResultsAdapter.updatePlayback(state.currentTrack?.id, state.isPlaying)
            likedSongsAdapter.updatePlayback(state.currentTrack?.id, state.isPlaying)
            updatePlayer(state)
        }
        PlaybackManager.playbackMessage.observe(this) { message ->
            if (message.isNullOrBlank()) return@observe
            showMessage(message)
            PlaybackManager.clearPlaybackMessage()
        }
    }

    private fun scheduleSearch(query: String) {
        currentQuery = query.trim()
        updateNetworkState()
        searchJob?.cancel()

        if (currentQuery.isBlank()) {
            onlineViewModel.searchSongs("")
            return
        }

        if (!isNetworkAvailable()) {
            updateSearchEmptyState()
            return
        }

        searchJob = lifecycleScope.launch {
            delay(500L)
            onlineViewModel.searchSongs(currentQuery)
        }
    }

    private fun updateSearchEmptyState() {
        val shouldShowEmpty = currentQuery.isNotBlank() && searchResults.isEmpty()
        binding.onlineEmptyText.isVisible = shouldShowEmpty
        binding.onlineTracksRecyclerView.isVisible = searchResults.isNotEmpty()
    }

    private fun toggleLike(track: MusicTrack) {
        val saavnId = track.saavnId ?: track.id
        val isLiked = likedSongs.any { likedTrack -> (likedTrack.saavnId ?: likedTrack.id) == saavnId }
        if (isLiked) {
            onlineViewModel.unlikeSong(saavnId)
        } else {
            onlineViewModel.likeSong(track)
        }
    }

    private fun playTrack(track: MusicTrack, preferredQueue: List<MusicTrack>) {
        val queue = preferredQueue.takeIf { tracks -> tracks.any { candidate -> candidate.id == track.id } }
            ?: return

        requestNotificationPermissionIfNeeded()
        val currentTrack = playbackState.currentTrack
        if (currentTrack?.id == track.id) {
            PlaybackManager.togglePlayback(this)
            return
        }

        val startIndex = queue.indexOfFirst { candidate -> candidate.id == track.id }.coerceAtLeast(0)
        PlaybackManager.playTracks(this, queue, startIndex)
    }

    private fun showTrackActions(track: MusicTrack) {
        val isLiked = likedSongs.any { likedTrack -> (likedTrack.saavnId ?: likedTrack.id) == (track.saavnId ?: track.id) }
        val labels = listOf(
            if (isLiked) getString(R.string.online_option_unlike) else getString(R.string.online_option_like),
            getString(R.string.online_option_add_to_playlist),
            getString(R.string.online_option_download)
        )

        val listView = android.widget.ListView(this).apply {
            adapter = ArrayAdapter(context, android.R.layout.simple_list_item_1, labels)
            dividerHeight = 0
        }

        val dialog = BottomSheetDialog(this)
        dialog.setContentView(listView)
        listView.setOnItemClickListener { _, _, position, _ ->
            dialog.dismiss()
            when (position) {
                0 -> toggleLike(track)
                1 -> showPlaylistPicker(track)
                2 -> downloadTrack(track)
            }
        }
        dialog.show()
    }

    private fun showCreatePlaylistDialog() {
        val input = EditText(this).apply {
            hint = getString(R.string.online_playlist_prompt)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.online_create_playlist)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                onlineViewModel.createPlaylist(input.text?.toString().orEmpty())
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showPlaylistPicker(track: MusicTrack) {
        if (playlists.isEmpty()) {
            showCreatePlaylistDialog()
            return
        }

        val names = playlists.map(Playlist::name).toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.online_playlists_title)
            .setItems(names) { _, which ->
                onlineViewModel.addToPlaylist(playlists[which].id, track)
                showMessage(getString(R.string.online_added_to_playlist))
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun openPlaylist(playlist: Playlist) {
        lifecycleScope.launch {
            val songs = onlineViewModel.getPlaylistSongs(playlist.id).first()
            if (songs.isEmpty()) {
                showMessage(getString(R.string.online_playlist_songs_empty))
                return@launch
            }

            val labels = songs.map { track -> "${track.title} • ${track.artistName ?: track.artist}" }
                .toTypedArray()
            MaterialAlertDialogBuilder(this@OnlineActivity)
                .setTitle(getString(R.string.online_playlist_dialog_title, playlist.name))
                .setItems(labels) { _, which ->
                    playTrack(songs[which], songs)
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    private fun downloadTrack(track: MusicTrack) {
        if (track.isDownloaded) {
            showMessage(getString(R.string.online_downloaded_message))
            return
        }

        val request = DownloadManager.Request((track.streamUrl ?: track.playbackUri.toString()).toUri())
            .setTitle(track.title)
            .setDescription(getString(R.string.online_download_description, track.artistName ?: track.artist))
            .setMimeType("audio/mpeg")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
            .setDestinationInExternalPublicDir(
                Environment.DIRECTORY_DOWNLOADS,
                "TheSonicGallery/${buildDownloadFileName(track)}"
            )

        runCatching {
            val manager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            manager.enqueue(request)
        }.onSuccess {
            DownloadedRemoteTracksStore.markDownloaded(applicationContext, track.id)
            downloadedTrackIds = downloadedTrackIds + track.id
            featuredTracks = featuredTracks.mapDownloaded(track.id)
            searchResults = searchResults.mapDownloaded(track.id)
            likedSongs = likedSongs.mapDownloaded(track.id)
            featuredAdapter.submitList(featuredTracks)
            searchResultsAdapter.submitList(searchResults)
            likedSongsAdapter.submitList(likedSongs)
            showMessage(getString(R.string.online_download_started, track.title))
        }.onFailure { error ->
            showMessage(error.localizedMessage ?: getString(R.string.error_generic))
        }
    }

    private fun buildDownloadFileName(track: MusicTrack): String {
        val title = track.title.replace(INVALID_FILE_CHARS, "_").trim().ifBlank { "track" }
        val artist = (track.artistName ?: track.artist).replace(INVALID_FILE_CHARS, "_").trim()
            .ifBlank { "artist" }
        return "${artist}_${title}.mp3"
    }

    private fun updatePlayer(state: PlaybackUiState) {
        val track = state.currentTrack
        val activeTint = ContextCompat.getColor(this, R.color.spotify_green)
        val inactiveTint = ContextCompat.getColor(this, R.color.spotify_text_primary)
        binding.playerShuffleButton.imageTintList = ColorStateList.valueOf(
            if (state.isShuffleOn) activeTint else inactiveTint
        )
        binding.playerRepeatButton.setImageResource(
            if (state.repeatMode == Player.REPEAT_MODE_ONE) {
                R.drawable.ic_repeat_one
            } else {
                R.drawable.ic_repeat
            }
        )
        binding.playerRepeatButton.imageTintList = ColorStateList.valueOf(
            if (state.repeatMode == Player.REPEAT_MODE_OFF) inactiveTint else activeTint
        )

        binding.onlinePlayerCard.isVisible = track != null
        if (track == null) return

        val isLiked = likedSongs.any { likedTrack -> (likedTrack.saavnId ?: likedTrack.id) == (track.saavnId ?: track.id) }
        bindTrackArtwork(binding.playerArtworkImage, binding.playerInitialText, track)
        binding.playerTitleText.text = track.title
        binding.playerArtistText.text = track.artistName ?: track.artist
        binding.playerCurrentTimeText.text = state.positionMs.toPlaybackTime()
        binding.playerDurationText.text = state.durationMs.toPlaybackTime()
        binding.playerQueueInfoText.text = getString(
            R.string.player_queue_format,
            state.currentIndex + 1,
            state.queue.size,
            getString(R.string.player_device_label)
        )
        binding.playerLikeButton.setImageResource(
            if (isLiked) R.drawable.ic_favorite else R.drawable.ic_favorite_border
        )
        binding.playerToggleButton.setImageResource(
            if (state.isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        )
        binding.playerPreviousButton.isEnabled = state.hasPrevious
        binding.playerNextButton.isEnabled = state.hasNext
        binding.playerPreviousButton.alpha = if (state.hasPrevious) 1f else 0.4f
        binding.playerNextButton.alpha = if (state.hasNext) 1f else 0.4f

        if (!isSeeking) {
            val progress = if (state.durationMs > 0L) {
                ((state.positionMs * 1000L) / state.durationMs).toInt().coerceIn(0, 1000)
            } else {
                0
            }
            binding.playerSeekBar.progress = progress
        }
    }

    private fun updateNetworkState() {
        val offline = !isNetworkAvailable()
        binding.networkStateText.isVisible = offline
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun applyDownloadedState(tracks: List<MusicTrack>): List<MusicTrack> {
        return tracks.map { track ->
            track.copy(isDownloaded = downloadedTrackIds.contains(track.id))
        }
    }

    private fun List<MusicTrack>.mapDownloaded(trackId: String): List<MusicTrack> {
        return map { track ->
            if (track.id == trackId) track.copy(isDownloaded = true) else track
        }
    }

    private fun showMessage(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }

    private companion object {
        val INVALID_FILE_CHARS = Regex("[\\\\/:*?\"<>|]")
    }
}
