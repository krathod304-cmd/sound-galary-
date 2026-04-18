package com.example.spotify.ui

import android.graphics.drawable.Drawable
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.example.spotify.music.MusicTrack

fun bindTrackArtwork(
    artworkView: ImageView,
    fallbackView: TextView,
    track: MusicTrack?
) {
    val albumArt = track?.albumArt
    val artworkUrl = track?.imageUrl ?: track?.coverUrl
    fallbackView.text = track?.title?.firstOrNull()?.uppercase() ?: "?"

    when {
        !artworkUrl.isNullOrBlank() -> {
            fallbackView.isVisible = true
            artworkView.isVisible = true
            Glide.with(artworkView)
                .load(artworkUrl)
                .centerCrop()
                .listener(object : RequestListener<Drawable> {
                    override fun onLoadFailed(
                        e: GlideException?,
                        model: Any?,
                        target: Target<Drawable>,
                        isFirstResource: Boolean
                    ): Boolean {
                        artworkView.setImageDrawable(null)
                        artworkView.isVisible = false
                        fallbackView.isVisible = true
                        return false
                    }

                    override fun onResourceReady(
                        resource: Drawable,
                        model: Any,
                        target: Target<Drawable>?,
                        dataSource: DataSource,
                        isFirstResource: Boolean
                    ): Boolean {
                        artworkView.isVisible = true
                        fallbackView.isVisible = false
                        return false
                    }
                })
                .into(artworkView)
        }

        albumArt != null -> {
            Glide.with(artworkView).clear(artworkView)
            artworkView.setImageBitmap(albumArt)
            artworkView.isVisible = true
            fallbackView.isVisible = false
        }

        else -> {
            Glide.with(artworkView).clear(artworkView)
            artworkView.setImageDrawable(null)
            artworkView.isVisible = false
            fallbackView.isVisible = true
        }
    }
}
