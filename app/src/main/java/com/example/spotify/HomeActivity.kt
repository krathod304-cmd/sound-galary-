package com.example.spotify

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.widget.SeekBar
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.Player
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.spotify.auth.AuthRepository
import com.example.spotify.auth.AuthSessionRoute
import com.example.spotify.auth.IntentKeys
import com.example.spotify.auth.RegistrationDraft
import com.example.spotify.auth.UserProfile
import com.example.spotify.databinding.ActivityHomeBinding
import com.example.spotify.music.MusicTrack
import com.example.spotify.playback.PlaybackManager
import com.example.spotify.playback.PlaybackUiState
import com.example.spotify.ui.LibraryTracksAdapter
import com.example.spotify.ui.OnlineHighlightAdapter
import com.example.spotify.ui.RecentTracksAdapter
import com.example.spotify.ui.bindTrackArtwork
import com.example.spotify.ui.toPlaybackTime
import com.example.spotify.viewmodel.HomeViewModel
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding

    private val authRepository = AuthRepository()
    private val homeViewModel: HomeViewModel by viewModels()

    private val recentTracksAdapter = RecentTracksAdapter { track ->
        playTrack(track, recentTracks)
    }
    private val onlineHighlightsAdapter = OnlineHighlightAdapter(
        onTrackClick = { track, _ -> playTrack(track, onlineHighlights) }
    )
    private val libraryTracksAdapter = LibraryTracksAdapter { track ->
        playTrack(track, downloadedTracks)
    }
    private val searchTracksAdapter = LibraryTracksAdapter { track ->
        playTrack(track, visibleSearchTracks)
    }

    private var recentTracks: List<MusicTrack> = emptyList()
    private var downloadedTracks: List<MusicTrack> = emptyList()
    private var onlineHighlights: List<MusicTrack> = emptyList()
    private var visibleSearchTracks: List<MusicTrack> = emptyList()
    private var loadedProfile: UserProfile? = null
    private var playbackState: PlaybackUiState = PlaybackUiState()
    private var hasAudioPermission: Boolean = false
    private var isSeeking: Boolean = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasAudioPermission = granted
        if (granted) {
            homeViewModel.loadOfflineTracks()
        } else {
            renderLibraryState()
            showMessage(getString(R.string.permission_denied_message))
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            showMessage(getString(R.string.notification_permission_denied_message))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        PlaybackManager.initialize(this)
        setupLists()
        setupActions()
        observePlayback()
        observeHomeState()
        loadProfile()
        syncAudioPermissionState()

        if (hasAudioPermission) {
            homeViewModel.loadOfflineTracks()
        } else {
            renderLibraryState()
        }
        homeViewModel.loadOnlineHighlights()

        binding.bottomNavigationView.selectedItemId = R.id.nav_home
        showScreen(R.id.nav_home)
    }

    private fun setupLists() {
        binding.dashboardView.recentTracksRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@HomeActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = recentTracksAdapter
        }
        binding.dashboardView.onlineHighlightsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@HomeActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = onlineHighlightsAdapter
        }
        binding.dashboardView.libraryTracksRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@HomeActivity)
            adapter = libraryTracksAdapter
        }
        binding.searchView.searchResultsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@HomeActivity)
            adapter = searchTracksAdapter
        }
    }

    private fun setupActions() {
        binding.offlineModeButton.setOnClickListener {
            binding.bottomNavigationView.selectedItemId = R.id.nav_home
            showScreen(R.id.nav_home)
        }
        binding.onlineModeButton.setOnClickListener {
            startActivity(Intent(this, OnlineActivity::class.java))
        }

        binding.bottomNavigationView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    showScreen(R.id.nav_home)
                    true
                }

                R.id.nav_search -> {
                    startActivity(Intent(this, OnlineActivity::class.java))
                    false
                }

                R.id.nav_player -> {
                    showScreen(R.id.nav_player)
                    true
                }

                R.id.nav_profile -> {
                    showScreen(R.id.nav_profile)
                    true
                }

                else -> false
            }
        }

        binding.miniPlayerCard.setOnClickListener {
            openPlayerScreen()
        }
        binding.miniPlayerToggleButton.setOnClickListener {
            requestNotificationPermissionIfNeeded()
            PlaybackManager.togglePlayback(this)
        }
        binding.miniPlayerNextButton.setOnClickListener {
            requestNotificationPermissionIfNeeded()
            PlaybackManager.skipNext()
        }

        binding.playerView.playerToggleButton.setOnClickListener {
            requestNotificationPermissionIfNeeded()
            PlaybackManager.togglePlayback(this)
        }
        binding.playerView.playerNextButton.setOnClickListener {
            requestNotificationPermissionIfNeeded()
            PlaybackManager.skipNext()
        }
        binding.playerView.playerPreviousButton.setOnClickListener {
            requestNotificationPermissionIfNeeded()
            PlaybackManager.skipPrevious()
        }
        binding.playerView.playerShuffleButton.setOnClickListener {
            PlaybackManager.toggleShuffle()
        }
        binding.playerView.playerRepeatButton.setOnClickListener {
            PlaybackManager.toggleRepeat()
        }
        binding.playerView.playerLikeButton.isVisible = false
        binding.playerView.playerSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) = Unit

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                isSeeking = true
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val duration = playbackState.durationMs
                val progress = seekBar?.progress ?: 0
                if (duration > 0L) {
                    val target = (duration * progress) / 1000L
                    PlaybackManager.seekTo(target)
                }
                isSeeking = false
            }
        })

        binding.searchView.searchEditText.doAfterTextChanged { text ->
            filterSearchResults(text?.toString().orEmpty())
        }
        setupSearchCategory(binding.searchView.categoryPopCard)
        setupSearchCategory(binding.searchView.categoryRockCard)
        setupSearchCategory(binding.searchView.categoryFocusCard)
        setupSearchCategory(binding.searchView.categoryJazzCard)
        setupSearchCategory(binding.searchView.categoryIndieCard)
        setupSearchCategory(binding.searchView.categorySleepCard)

        binding.dashboardView.homePrimaryActionButton.setOnClickListener {
            if (hasAudioPermission) {
                homeViewModel.loadOfflineTracks()
            } else {
                permissionLauncher.launch(audioPermission())
            }
        }
        binding.dashboardView.homeSecondaryActionButton.setOnClickListener {
            homeViewModel.loadOfflineTracks()
        }

        binding.profileView.profileSignOutButton.setOnClickListener {
            authRepository.signOut()
            PlaybackManager.playerOrNull()?.pause()
            startActivity(Intent(this, LoginActivity::class.java))
            finishAffinity()
        }
        binding.profileView.localOnlySwitch.setOnClickListener {
            binding.profileView.localOnlySwitch.isChecked = true
            showMessage(getString(R.string.profile_mode_value))
        }
        binding.profileView.notificationSwitch.setOnClickListener {
            binding.profileView.notificationSwitch.isChecked = true
            showMessage(getString(R.string.profile_notification_value))
        }
    }

    private fun observeHomeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    homeViewModel.offlineTracks.collect { tracks ->
                        downloadedTracks = tracks
                        binding.dashboardView.libraryCountText.text =
                            getString(R.string.offline_library_count, tracks.size)
                        renderLibraryState()
                        filterSearchResults(binding.searchView.searchEditText.text?.toString().orEmpty())
                    }
                }

                launch {
                    homeViewModel.onlineHighlights.collect { tracks ->
                        onlineHighlights = tracks
                        onlineHighlightsAdapter.submitList(tracks)
                        binding.dashboardView.onlineHighlightsEmptyText.isVisible = tracks.isEmpty()
                        binding.dashboardView.onlineHighlightsRecyclerView.isVisible = tracks.isNotEmpty()
                    }
                }

                launch {
                    homeViewModel.recentTracks.collect { tracks ->
                        recentTracks = tracks
                        recentTracksAdapter.submitList(tracks)
                        binding.dashboardView.recentEmptyText.isVisible = tracks.isEmpty()
                        binding.dashboardView.recentTracksRecyclerView.isVisible = tracks.isNotEmpty()
                    }
                }

                launch {
                    homeViewModel.loading.collect { isLoading ->
                        setLoading(isLoading)
                    }
                }

                launch {
                    homeViewModel.error.collect { message ->
                        if (message.isNullOrBlank()) return@collect
                        showMessage(message)
                        homeViewModel.clearError()
                    }
                }
            }
        }
    }

    private fun observePlayback() {
        PlaybackManager.uiState.observe(this) { state ->
            playbackState = state
            homeViewModel.cacheRecentTrack(state.currentTrack)
            libraryTracksAdapter.updatePlayback(state.currentTrack?.id, state.isPlaying)
            searchTracksAdapter.updatePlayback(state.currentTrack?.id, state.isPlaying)
            updateMiniPlayer(state)
            updatePlayerScreen(state)
        }
        PlaybackManager.playbackMessage.observe(this) { message ->
            if (message.isNullOrBlank()) return@observe
            showMessage(message)
            PlaybackManager.clearPlaybackMessage()
        }
    }

    private fun loadProfile() {
        authRepository.resolveCurrentSession(
            onResult = { route ->
                when (route) {
                    is AuthSessionRoute.GoHome -> {
                        loadedProfile = route.profile
                        renderProfile()
                        route.notice?.takeIf { it.isNotBlank() }?.let(::showMessage)
                    }

                    is AuthSessionRoute.CompleteRegistration -> {
                        openRegistration(route.draft, route.notice)
                    }
                }
            },
            onError = {
                renderProfile()
            }
        )
    }

    private fun renderProfile() {
        val currentUser = authRepository.currentUser()
        val name = loadedProfile?.name
            ?.takeIf { it.isNotBlank() }
            ?: currentUser?.displayName
            ?: getString(R.string.app_name)
        val email = loadedProfile?.email
            ?.takeIf { it.isNotBlank() }
            ?: currentUser?.email
            ?: getString(R.string.home_missing_value)
        val phone = loadedProfile?.phone
            ?.takeIf { it.isNotBlank() }
            ?: currentUser?.phoneNumber
            ?: getString(R.string.home_missing_value)

        val firstName = name.substringBefore(" ").ifBlank { name }
        binding.headerGreetingText.text = getString(
            R.string.home_greeting_format,
            getString(R.string.home_greeting_prefix),
            firstName
        )
        binding.profileView.profileNameText.text = name
        binding.profileView.profileEmailText.text = email
        binding.profileView.profilePhoneText.text = phone
    }

    private fun syncAudioPermissionState() {
        hasAudioPermission = ContextCompat.checkSelfPermission(
            this,
            audioPermission()
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun renderLibraryState() {
        val emptyCard = binding.dashboardView.homeEmptyStateCard
        when {
            !hasAudioPermission -> {
                emptyCard.isVisible = true
                binding.dashboardView.homeEmptyTitleText.text = getString(R.string.offline_permission_title)
                binding.dashboardView.homeEmptyBodyText.text = getString(R.string.offline_permission_body)
                binding.dashboardView.homePrimaryActionButton.text = getString(R.string.offline_permission_action)
                binding.dashboardView.homeSecondaryActionButton.isVisible = false
                libraryTracksAdapter.submitList(emptyList())
                binding.searchView.searchEmptyText.isVisible = true
                binding.searchView.searchResultsRecyclerView.isVisible = false
            }

            downloadedTracks.isEmpty() -> {
                emptyCard.isVisible = true
                binding.dashboardView.homeEmptyTitleText.text = getString(R.string.offline_empty_title)
                binding.dashboardView.homeEmptyBodyText.text = getString(R.string.offline_empty_body)
                binding.dashboardView.homePrimaryActionButton.text = getString(R.string.offline_refresh_action)
                binding.dashboardView.homeSecondaryActionButton.isVisible = false
                libraryTracksAdapter.submitList(emptyList())
                binding.searchView.searchEmptyText.isVisible = true
                binding.searchView.searchResultsRecyclerView.isVisible = false
            }

            else -> {
                emptyCard.isVisible = false
                libraryTracksAdapter.submitList(downloadedTracks)
                binding.searchView.searchResultsRecyclerView.isVisible = visibleSearchTracks.isNotEmpty()
            }
        }
    }

    private fun filterSearchResults(query: String) {
        val normalizedQuery = query.trim().lowercase()
        visibleSearchTracks = if (normalizedQuery.isBlank()) {
            downloadedTracks
        } else {
            downloadedTracks.filter { track ->
                track.title.lowercase().contains(normalizedQuery) ||
                    track.artist.lowercase().contains(normalizedQuery)
            }
        }

        searchTracksAdapter.submitList(visibleSearchTracks)
        binding.searchView.searchEmptyText.isVisible = hasAudioPermission && visibleSearchTracks.isEmpty()
        binding.searchView.searchResultsRecyclerView.isVisible = visibleSearchTracks.isNotEmpty()
    }

    private fun updateMiniPlayer(state: PlaybackUiState) {
        val track = state.currentTrack
        binding.miniPlayerCard.isVisible = track != null
        if (track == null) return

        bindTrackArtwork(
            artworkView = binding.miniPlayerArtworkImage,
            fallbackView = binding.miniPlayerInitialText,
            track = track
        )
        binding.miniPlayerTitleText.text = track.title
        binding.miniPlayerArtistText.text = track.artistName ?: track.artist
        binding.miniPlayerToggleButton.setImageResource(
            if (state.isPlaying) R.drawable.ic_pause_light else R.drawable.ic_play_light
        )
        binding.miniPlayerToggleButton.contentDescription = getString(
            if (state.isPlaying) R.string.offline_pause_track else R.string.offline_play_track
        )
        binding.miniPlayerNextButton.isEnabled = state.hasNext
        binding.miniPlayerNextButton.alpha = if (state.hasNext) 1f else 0.4f
    }

    private fun updatePlayerScreen(state: PlaybackUiState) {
        val track = state.currentTrack
        val activeTint = ContextCompat.getColor(this, R.color.spotify_green)
        val inactiveTint = ContextCompat.getColor(this, R.color.spotify_text_primary)
        binding.playerView.playerShuffleButton.imageTintList = ColorStateList.valueOf(
            if (state.isShuffleOn) activeTint else inactiveTint
        )
        binding.playerView.playerRepeatButton.setImageResource(
            if (state.repeatMode == Player.REPEAT_MODE_ONE) {
                R.drawable.ic_repeat_one
            } else {
                R.drawable.ic_repeat
            }
        )
        binding.playerView.playerRepeatButton.imageTintList = ColorStateList.valueOf(
            if (state.repeatMode == Player.REPEAT_MODE_OFF) inactiveTint else activeTint
        )

        if (track == null) {
            bindTrackArtwork(
                artworkView = binding.playerView.playerArtworkImage,
                fallbackView = binding.playerView.playerInitialText,
                track = null
            )
            binding.playerView.playerTrackTitleText.text = getString(R.string.player_idle_title)
            binding.playerView.playerArtistText.text = getString(R.string.player_idle_subtitle)
            binding.playerView.playerCurrentTimeText.text = 0L.toPlaybackTime()
            binding.playerView.playerDurationText.text = 0L.toPlaybackTime()
            binding.playerView.playerQueueInfoText.text = getString(R.string.player_device_label)
            binding.playerView.playerToggleButton.setImageResource(R.drawable.ic_play)
            binding.playerView.playerPreviousButton.isEnabled = false
            binding.playerView.playerNextButton.isEnabled = false
            if (!isSeeking) {
                binding.playerView.playerSeekBar.progress = 0
            }
            return
        }

        bindTrackArtwork(
            artworkView = binding.playerView.playerArtworkImage,
            fallbackView = binding.playerView.playerInitialText,
            track = track
        )
        binding.playerView.playerTrackTitleText.text = track.title
        binding.playerView.playerArtistText.text = track.artistName ?: track.artist
        binding.playerView.playerCurrentTimeText.text = state.positionMs.toPlaybackTime()
        binding.playerView.playerDurationText.text = state.durationMs.toPlaybackTime()
        binding.playerView.playerQueueInfoText.text = getString(
            R.string.player_queue_format,
            state.currentIndex + 1,
            state.queue.size,
            getString(R.string.player_device_label)
        )
        binding.playerView.playerToggleButton.setImageResource(
            if (state.isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        )
        binding.playerView.playerToggleButton.contentDescription = getString(
            if (state.isPlaying) R.string.offline_pause_track else R.string.offline_play_track
        )
        binding.playerView.playerPreviousButton.isEnabled = state.hasPrevious
        binding.playerView.playerNextButton.isEnabled = state.hasNext
        binding.playerView.playerPreviousButton.alpha = if (state.hasPrevious) 1f else 0.4f
        binding.playerView.playerNextButton.alpha = if (state.hasNext) 1f else 0.4f

        if (!isSeeking) {
            val progress = if (state.durationMs > 0L) {
                ((state.positionMs * 1000L) / state.durationMs).toInt().coerceIn(0, 1000)
            } else {
                0
            }
            binding.playerView.playerSeekBar.progress = progress
        }
    }

    private fun playTrack(track: MusicTrack, preferredQueue: List<MusicTrack>) {
        val queue = preferredQueue.takeIf { it.any { item -> item.id == track.id } } ?: return
        requestNotificationPermissionIfNeeded()

        val currentTrack = playbackState.currentTrack
        if (currentTrack?.id == track.id) {
            PlaybackManager.togglePlayback(this)
            openPlayerScreen()
            return
        }

        val startIndex = queue.indexOfFirst { item -> item.id == track.id }.coerceAtLeast(0)
        if (PlaybackManager.playTracks(this, queue, startIndex).isSuccess) {
            openPlayerScreen()
        }
    }

    private fun showScreen(destinationId: Int) {
        binding.dashboardView.root.isVisible = destinationId == R.id.nav_home
        binding.searchView.root.isVisible = false
        binding.playerView.root.isVisible = destinationId == R.id.nav_player
        binding.profileView.root.isVisible = destinationId == R.id.nav_profile
    }

    private fun openPlayerScreen() {
        binding.bottomNavigationView.selectedItemId = R.id.nav_player
        showScreen(R.id.nav_player)
    }

    private fun setupSearchCategory(view: android.view.View) {
        view.setOnClickListener {
            binding.searchView.searchEditText.setText((view as? android.widget.TextView)?.text)
        }
    }

    private fun audioPermission(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
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

    private fun openRegistration(draft: RegistrationDraft, notice: String?) {
        startActivity(
            Intent(this, RegistrationActivity::class.java).apply {
                putExtra(IntentKeys.EXTRA_PROVIDER, draft.provider.wireName)
                putExtra(IntentKeys.EXTRA_UID, draft.uid)
                putExtra(IntentKeys.EXTRA_NAME, draft.name)
                putExtra(IntentKeys.EXTRA_EMAIL, draft.email)
                putExtra(IntentKeys.EXTRA_PHONE, draft.phone)
                putExtra(IntentKeys.EXTRA_NOTICE, notice)
            }
        )
        finish()
    }

    private fun setLoading(isLoading: Boolean) {
        binding.loadingView.root.isVisible = isLoading
    }

    private fun showMessage(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }
}
