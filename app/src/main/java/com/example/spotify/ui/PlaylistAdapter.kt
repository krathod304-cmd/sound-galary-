package com.example.spotify.ui

import android.annotation.SuppressLint
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.example.spotify.databinding.ItemPlaylistBinding
import com.example.spotify.music.Playlist

class PlaylistAdapter(
    private val onPlaylistClick: (Playlist) -> Unit
) : RecyclerView.Adapter<PlaylistAdapter.PlaylistViewHolder>() {

    private val items = mutableListOf<Playlist>()

    @SuppressLint("NotifyDataSetChanged")
    fun submitList(playlists: List<Playlist>) {
        items.clear()
        items.addAll(playlists)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlaylistViewHolder {
        val binding = ItemPlaylistBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return PlaylistViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PlaylistViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class PlaylistViewHolder(
        private val binding: ItemPlaylistBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(playlist: Playlist) {
            binding.playlistTitleText.text = playlist.name
            binding.playlistMetaText.text = if (playlist.songCount == 1) {
                "1 song"
            } else {
                "${playlist.songCount} songs"
            }
            binding.playlistInitialText.text = playlist.name.firstOrNull()?.uppercase() ?: "P"

            if (!playlist.coverUrl.isNullOrBlank()) {
                binding.playlistArtworkImage.isVisible = true
                binding.playlistInitialText.isVisible = true
                Glide.with(binding.playlistArtworkImage)
                    .load(playlist.coverUrl)
                    .centerCrop()
                    .listener(object : RequestListener<Drawable> {
                        override fun onLoadFailed(
                            e: GlideException?,
                            model: Any?,
                            target: Target<Drawable>,
                            isFirstResource: Boolean
                        ): Boolean {
                            binding.playlistArtworkImage.setImageDrawable(null)
                            binding.playlistArtworkImage.isVisible = false
                            binding.playlistInitialText.isVisible = true
                            return false
                        }

                        override fun onResourceReady(
                            resource: Drawable,
                            model: Any,
                            target: Target<Drawable>?,
                            dataSource: DataSource,
                            isFirstResource: Boolean
                        ): Boolean {
                            binding.playlistArtworkImage.isVisible = true
                            binding.playlistInitialText.isVisible = false
                            return false
                        }
                    })
                    .into(binding.playlistArtworkImage)
            } else {
                Glide.with(binding.playlistArtworkImage).clear(binding.playlistArtworkImage)
                binding.playlistArtworkImage.setImageDrawable(null)
                binding.playlistArtworkImage.isVisible = false
                binding.playlistInitialText.isVisible = true
            }

            binding.root.setOnClickListener { onPlaylistClick(playlist) }
        }
    }
}
