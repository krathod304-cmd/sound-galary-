package com.example.spotify.ui

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.example.spotify.R
import com.example.spotify.databinding.ItemOnlineTrackBinding
import com.example.spotify.music.MusicTrack

class OnlineTracksAdapter(
    private val onTrackClick: (MusicTrack, Int) -> Unit,
    private val onLongClick: (MusicTrack) -> Unit,
    private val onLikeClick: (MusicTrack) -> Unit,
    private val onDownloadClick: (MusicTrack) -> Unit
) : RecyclerView.Adapter<OnlineTracksAdapter.OnlineTrackViewHolder>() {

    private val items = mutableListOf<MusicTrack>()
    private var currentPlayingTrackId: String? = null
    private var isPlaying: Boolean = false
    private var likedTrackIds: Set<String> = emptySet()

    @SuppressLint("NotifyDataSetChanged")
    fun submitList(tracks: List<MusicTrack>) {
        items.clear()
        items.addAll(tracks)
        notifyDataSetChanged()
    }

    @SuppressLint("NotifyDataSetChanged")
    fun updatePlayback(trackId: String?, playing: Boolean) {
        currentPlayingTrackId = trackId
        isPlaying = playing
        notifyDataSetChanged()
    }

    @SuppressLint("NotifyDataSetChanged")
    fun updateLikedSongs(tracks: List<MusicTrack>) {
        likedTrackIds = tracks.map { track -> track.saavnId ?: track.id }.toSet()
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OnlineTrackViewHolder {
        val binding = ItemOnlineTrackBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return OnlineTrackViewHolder(binding)
    }

    override fun onBindViewHolder(holder: OnlineTrackViewHolder, position: Int) {
        holder.bind(items[position], position)
    }

    override fun getItemCount(): Int = items.size

    inner class OnlineTrackViewHolder(
        private val binding: ItemOnlineTrackBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(track: MusicTrack, position: Int) {
            val isCurrentTrack = track.id == currentPlayingTrackId
            val isLiked = likedTrackIds.contains(track.saavnId ?: track.id)
            bindTrackArtwork(binding.onlineTrackArtworkImage, binding.onlineTrackInitialText, track)
            binding.onlineTrackTitleText.text = track.title
            binding.onlineTrackMetaText.text = listOfNotNull(
                track.artistName ?: track.artist,
                track.album.takeIf { it.isNotBlank() }
            ).joinToString(binding.root.context.getString(R.string.bullet_separator))
            binding.onlineTrackStatsText.text = track.durationMs.toPlaybackTime()
            binding.downloadedBadgeText.isVisible = track.isDownloaded
            binding.downloadButton.isEnabled = !track.isDownloaded
            binding.downloadButton.alpha = if (track.isDownloaded) 0.4f else 1f
            binding.likeButton.setImageResource(
                if (isLiked) R.drawable.ic_favorite else R.drawable.ic_favorite_border
            )
            binding.playButton.setImageResource(
                if (isCurrentTrack && isPlaying) R.drawable.ic_pause_light else R.drawable.ic_play_light
            )

            binding.root.setOnClickListener { onTrackClick(track, position) }
            binding.root.setOnLongClickListener {
                onLongClick(track)
                true
            }
            binding.playButton.setOnClickListener { onTrackClick(track, position) }
            binding.likeButton.setOnClickListener { onLikeClick(track) }
            binding.downloadButton.setOnClickListener { onDownloadClick(track) }
        }
    }
}
