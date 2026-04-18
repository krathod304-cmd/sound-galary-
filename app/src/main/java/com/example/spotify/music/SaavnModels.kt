package com.example.spotify.music

import com.google.gson.annotations.SerializedName

data class SaavnSearchResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("data") val data: SaavnSearchData = SaavnSearchData()
)

data class SaavnSearchData(
    @SerializedName("total") val total: Int = 0,
    @SerializedName("start") val start: Int = 0,
    @SerializedName("results") val results: List<SaavnSong> = emptyList()
)

data class SaavnSongResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("data") val data: SaavnSong = SaavnSong()
)

data class SaavnSong(
    @SerializedName("id") val id: String = "",
    @SerializedName("name") val name: String = "",
    @SerializedName("type") val type: String = "",
    @SerializedName("year") val year: String = "",
    @SerializedName("releaseDate") val releaseDate: String? = null,
    @SerializedName("duration") val duration: Int = 0,
    @SerializedName("label") val label: String = "",
    @SerializedName("explicitContent") val explicitContent: Boolean = false,
    @SerializedName("playCount") val playCount: Long = 0L,
    @SerializedName("language") val language: String = "",
    @SerializedName("hasLyrics") val hasLyrics: Boolean = false,
    @SerializedName("lyricsId") val lyricsId: String? = null,
    @SerializedName("url") val url: String = "",
    @SerializedName("copyright") val copyright: String = "",
    @SerializedName("album") val album: SaavnAlbum = SaavnAlbum(),
    @SerializedName("artists") val artists: SaavnArtists = SaavnArtists(),
    @SerializedName("image") val image: List<SaavnImage> = emptyList(),
    @SerializedName("downloadUrl") val downloadUrl: List<SaavnDownloadUrl> = emptyList()
)

data class SaavnAlbum(
    @SerializedName("id") val id: String = "",
    @SerializedName("name") val name: String = "",
    @SerializedName("url") val url: String = ""
)

data class SaavnArtists(
    @SerializedName("primary") val primary: List<SaavnArtist> = emptyList(),
    @SerializedName("featured") val featured: List<SaavnArtist> = emptyList(),
    @SerializedName("all") val all: List<SaavnArtist> = emptyList()
)

data class SaavnArtist(
    @SerializedName("id") val id: String = "",
    @SerializedName("name") val name: String = "",
    @SerializedName("role") val role: String = "",
    @SerializedName("image") val image: List<SaavnImage> = emptyList(),
    @SerializedName("type") val type: String = "",
    @SerializedName("url") val url: String = ""
)

data class SaavnImage(
    @SerializedName("quality") val quality: String = "",
    @SerializedName("url") val url: String = ""
)

data class SaavnDownloadUrl(
    @SerializedName("quality") val quality: String = "",
    @SerializedName("url") val url: String = ""
)
