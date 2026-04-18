package com.example.spotify.ui

fun Long.toPlaybackTime(): String {
    if (this <= 0L) return "0:00"
    val totalSeconds = this / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
