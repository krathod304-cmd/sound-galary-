package com.example.spotify.music

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface SaavnApiService {
    @GET("api/search/songs")
    suspend fun searchSongs(
        @Query("query") query: String,
        @Query("limit") limit: Int = 20
    ): SaavnSearchResponse

    @GET("api/songs/{id}")
    suspend fun getSong(
        @Path("id") id: String
    ): SaavnSongResponse
}
