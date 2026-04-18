package com.example.spotify.music

import android.graphics.Bitmap
import android.net.Uri
import androidx.core.net.toUri

enum class MusicSource {
    LOCAL,
    REMOTE
}

data class MusicTrack(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val genre: String? = null,
    val durationMs: Long = 0L,
    val playbackUri: Uri = Uri.EMPTY,
    val source: MusicSource = MusicSource.LOCAL,
    val relativePath: String? = null,
    val dataPath: String? = null,
    val albumArt: Bitmap? = null,
    val imageUrl: String? = null,
    val coverUrl: String? = imageUrl,
    val remoteDocId: String? = null,
    val downloads: Long = 0L,
    val plays: Long = 0L,
    val isDownloaded: Boolean = false,
    val isOnline: Boolean = false,
    val streamUrl: String? = null,
    val saavnId: String? = null,
    val artistName: String? = null
) {

    companion object {
        fun fromSaavnSong(song: SaavnSong): MusicTrack {
            val resolvedStreamUrl = song.downloadUrl.lastOrNull()?.url.normalizeSaavnUrl()
            val resolvedImageUrl = song.image.lastOrNull()?.url.normalizeSaavnUrl()
            val resolvedArtist = song.artists.primary
                .mapNotNull { artist -> artist.name.trim().takeIf { it.isNotBlank() } }
                .joinToString(", ")
                .ifBlank { "Unknown artist" }

            return MusicTrack(
                id = song.id,
                title = song.name.ifBlank { "Untitled track" },
                artist = resolvedArtist,
                album = song.album.name.ifBlank { "Single" },
                genre = song.language.takeIf { it.isNotBlank() },
                durationMs = song.duration.toLong().coerceAtLeast(0L) * 1000L,
                playbackUri = resolvedStreamUrl?.toUri() ?: Uri.EMPTY,
                source = MusicSource.REMOTE,
                imageUrl = resolvedImageUrl,
                coverUrl = resolvedImageUrl,
                remoteDocId = song.id,
                isOnline = true,
                streamUrl = resolvedStreamUrl,
                saavnId = song.id,
                artistName = resolvedArtist
            )
        }
    }
}

private fun String?.normalizeSaavnUrl(): String? {
    val value = this?.trim().orEmpty()
    return value
        .replace("http://", "https://")
        .takeIf { it.isNotBlank() }
}
