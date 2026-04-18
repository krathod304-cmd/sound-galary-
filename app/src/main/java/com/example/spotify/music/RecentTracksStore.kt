package com.example.spotify.music

import android.content.Context

object RecentTracksStore {
    private const val PREFS_NAME = "recent_tracks_store"
    private const val KEY_RECENT_IDS = "recent_ids"
    private const val MAX_RECENT = 12

    fun markPlayed(context: Context, trackId: String) {
        val updated = getRecentIds(context)
            .filterNot { it == trackId }
            .toMutableList()
            .apply { add(0, trackId) }
            .take(MAX_RECENT)

        prefs(context)
            .edit()
            .putString(KEY_RECENT_IDS, updated.joinToString(","))
            .apply()
    }

    fun getRecentIds(context: Context): List<String> {
        return prefs(context)
            .getString(KEY_RECENT_IDS, "")
            .orEmpty()
            .split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
