package com.example.spotify.music

import android.content.Context
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import retrofit2.HttpException

class MusicRepository(
    private val localMusicRepository: LocalMusicRepository = LocalMusicRepository(),
    private val saavnApi: SaavnApiService = RetrofitClient.saavnApi
) {

    suspend fun loadOfflineTracks(context: Context): List<MusicTrack> {
        return localMusicRepository.loadDownloadedTracks(context)
    }

    suspend fun searchOnlineSongs(query: String): Result<List<MusicTrack>> {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isBlank()) {
            return Result.success(emptyList())
        }

        return runCatching {
            val response = saavnApi.searchSongs(query = normalizedQuery)
            if (!response.success) {
                throw IOException("The music service returned an invalid search response.")
            }
            val mappedTracks = response.data.results
                .map(MusicTrack::fromSaavnSong)
                .filter { track -> !track.streamUrl.isNullOrBlank() }

            if (mappedTracks.isEmpty()) {
                throw IOException("No songs matched \"$normalizedQuery\".")
            }

            mappedTracks
        }.fold(
            onSuccess = Result.Companion::success,
            onFailure = { error -> Result.failure(error.toRepositoryError()) }
        )
    }

    suspend fun getSongById(id: String): Result<MusicTrack> {
        return runCatching {
            val response = saavnApi.getSong(id)
            if (!response.success) {
                throw IOException("The music service could not load this song.")
            }

            MusicTrack.fromSaavnSong(response.data)
                .takeIf { track -> !track.streamUrl.isNullOrBlank() }
                ?: throw IOException("This song is unavailable right now.")
        }.fold(
            onSuccess = Result.Companion::success,
            onFailure = { error -> Result.failure(error.toRepositoryError()) }
        )
    }

    suspend fun getFeaturedSongs(query: String = DEFAULT_FEATURED_QUERY): Result<List<MusicTrack>> {
        return searchOnlineSongs(query)
    }

    private fun Throwable.toRepositoryError(): Throwable {
        val message = when (this) {
            is UnknownHostException -> "No internet connection."
            is SocketTimeoutException -> "The music service took too long to respond."
            is HttpException -> "The music service is unavailable right now."
            is IOException -> localizedMessage ?: "The music request could not be completed."
            else -> localizedMessage ?: "Something went wrong while loading music."
        }
        return IOException(message, this)
    }

    private companion object {
        const val DEFAULT_FEATURED_QUERY = "top hits"
    }
}
