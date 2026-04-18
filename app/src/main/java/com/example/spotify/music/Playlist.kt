package com.example.spotify.music

data class Playlist(
    val id: String,
    val name: String,
    val coverUrl: String? = null,
    val songCount: Int = 0,
    val createdAt: Long = 0L
)
