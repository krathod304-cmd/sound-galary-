package com.example.spotify.ui

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.spotify.databinding.ItemRecentTrackBinding
import com.example.spotify.music.MusicTrack

class RecentTracksAdapter(
    private val onTrackClick: (MusicTrack) -> Unit
) : RecyclerView.Adapter<RecentTracksAdapter.RecentTrackViewHolder>() {

    private val items = mutableListOf<MusicTrack>()

    @SuppressLint("NotifyDataSetChanged")
    fun submitList(tracks: List<MusicTrack>) {
        items.clear()
        items.addAll(tracks)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecentTrackViewHolder {
        val binding = ItemRecentTrackBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return RecentTrackViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RecentTrackViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class RecentTrackViewHolder(
        private val binding: ItemRecentTrackBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(track: MusicTrack) {
            bindTrackArtwork(
                artworkView = binding.recentTrackArtworkImage,
                fallbackView = binding.recentTrackInitialText,
                track = track
            )
            binding.recentTrackTitleText.text = track.title
            binding.recentTrackArtistText.text = track.artist
            binding.root.setOnClickListener { onTrackClick(track) }
        }
    }
}
