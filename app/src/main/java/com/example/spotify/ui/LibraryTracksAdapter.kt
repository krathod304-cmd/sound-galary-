package com.example.spotify.ui

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.spotify.R
import com.example.spotify.databinding.ItemLibraryTrackBinding
import com.example.spotify.music.MusicTrack

class LibraryTracksAdapter(
    private val onTrackClick: (MusicTrack) -> Unit
) : RecyclerView.Adapter<LibraryTracksAdapter.LibraryTrackViewHolder>() {

    private val items = mutableListOf<MusicTrack>()
    private var currentPlayingTrackId: String? = null
    private var isPlaying: Boolean = false

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

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LibraryTrackViewHolder {
        val binding = ItemLibraryTrackBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return LibraryTrackViewHolder(binding)
    }

    override fun onBindViewHolder(holder: LibraryTrackViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class LibraryTrackViewHolder(
        private val binding: ItemLibraryTrackBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(track: MusicTrack) {
            val isCurrentTrack = track.id == currentPlayingTrackId
            bindTrackArtwork(
                artworkView = binding.libraryTrackArtworkImage,
                fallbackView = binding.libraryTrackInitialText,
                track = track
            )
            binding.libraryTrackTitleText.text = track.title
            binding.libraryTrackMetaText.text = buildString {
                append(track.artist)
                if (track.album.isNotBlank()) {
                    append(" • ")
                    append(track.album)
                }
            }
            binding.libraryTrackDurationText.text = track.durationMs.toPlaybackTime()
            binding.libraryTrackPlayButton.setImageResource(
                if (isCurrentTrack && isPlaying) R.drawable.ic_pause_light else R.drawable.ic_play_light
            )

            binding.root.setOnClickListener { onTrackClick(track) }
            binding.libraryTrackPlayButton.setOnClickListener { onTrackClick(track) }
        }
    }
}
