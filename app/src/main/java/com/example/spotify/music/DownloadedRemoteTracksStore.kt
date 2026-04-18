package com.example.spotify.music

import android.content.Context

object DownloadedRemoteTracksStore {
    private const val PREFS_NAME = "downloaded_remote_tracks_store"
    private const val KEY_REMOTE_IDS = "remote_ids"

    fun getDownloadedIds(context: Context): Set<String> {
        return prefs(context).getStringSet(KEY_REMOTE_IDS, emptySet()).orEmpty()
    }

    fun markDownloaded(context: Context, trackId: String) {
        val updated = getDownloadedIds(context).toMutableSet().apply { add(trackId) }
        prefs(context).edit().putStringSet(KEY_REMOTE_IDS, updated).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
