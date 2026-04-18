package com.example.spotify.ui

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.example.spotify.R
import com.example.spotify.databinding.ItemOnlineHighlightBinding
import com.example.spotify.music.MusicTrack

class OnlineHighlightAdapter(
    private val onTrackClick: (MusicTrack, Int) -> Unit,
    private val onLongClick: ((MusicTrack) -> Unit)? = null,
    private val onLikeClick: ((MusicTrack) -> Unit)? = null
) : RecyclerView.Adapter<OnlineHighlightAdapter.OnlineHighlightViewHolder>() {

    private val items = mutableListOf<MusicTrack>()
    private var likedTrackIds: Set<String> = emptySet()

    @SuppressLint("NotifyDataSetChanged")
    fun submitList(tracks: List<MusicTrack>) {
        items.clear()
        items.addAll(tracks)
        notifyDataSetChanged()
    }

    @SuppressLint("NotifyDataSetChanged")
    fun updateLikedSongs(tracks: List<MusicTrack>) {
        likedTrackIds = tracks.map { track -> track.saavnId ?: track.id }.toSet()
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OnlineHighlightViewHolder {
        val binding = ItemOnlineHighlightBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return OnlineHighlightViewHolder(binding)
    }

    override fun onBindViewHolder(holder: OnlineHighlightViewHolder, position: Int) {
        holder.bind(items[position], position)
    }

    override fun getItemCount(): Int = items.size

    inner class OnlineHighlightViewHolder(
        private val binding: ItemOnlineHighlightBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(track: MusicTrack, position: Int) {
            val isLiked = likedTrackIds.contains(track.saavnId ?: track.id)
            bindTrackArtwork(binding.highlightArtworkImage, binding.highlightInitialText, track)
            binding.highlightTitleText.text = track.title
            binding.highlightMetaText.text = listOfNotNull(
                track.artistName ?: track.artist,
                track.durationMs.takeIf { it > 0L }?.toPlaybackTime()
            ).joinToString(binding.root.context.getString(R.string.bullet_separator))
            binding.highlightAlbumText.text = track.album
            binding.highlightLikeButton.isVisible = onLikeClick != null
            binding.highlightLikeButton.setImageResource(
                if (isLiked) R.drawable.ic_favorite else R.drawable.ic_favorite_border
            )

            binding.root.setOnClickListener { onTrackClick(track, position) }
            binding.root.setOnLongClickListener {
                onLongClick?.invoke(track)
                onLongClick != null
            }
            binding.highlightLikeButton.setOnClickListener { onLikeClick?.invoke(track) }
        }
    }
}
