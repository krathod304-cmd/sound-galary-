package com.example.spotify.music

import androidx.core.net.toUri
import com.google.android.gms.tasks.Task
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class FirestoreRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    suspend fun likeSong(userId: String, track: MusicTrack) {
        val saavnId = track.saavnId ?: track.id
        val payload = buildTrackPayload(track) + mapOf(
            "saavnId" to saavnId,
            "addedAt" to FieldValue.serverTimestamp()
        )

        awaitTask(
            likedSongsCollection(userId)
                .document(saavnId)
                .set(payload)
        )
    }

    suspend fun unlikeSong(userId: String, saavnId: String) {
        awaitTask(
            likedSongsCollection(userId)
                .document(saavnId)
                .delete()
        )
    }

    suspend fun isLiked(userId: String, saavnId: String): Boolean {
        return awaitTask(
            likedSongsCollection(userId)
                .document(saavnId)
                .get()
        ).exists()
    }

    fun getLikedSongs(userId: String): Flow<List<MusicTrack>> = callbackFlow {
        val listener = likedSongsCollection(userId)
            .orderBy("addedAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }

                trySend(snapshot?.documents.orEmpty().mapNotNull(::toStoredTrack))
            }

        awaitClose { listener.remove() }
    }.conflate()

    suspend fun createPlaylist(userId: String, name: String): String {
        val playlistRef = playlistsCollection(userId).document()
        val payload = mapOf(
            "name" to name.trim(),
            "createdAt" to FieldValue.serverTimestamp(),
            "coverUrl" to "",
            "songCount" to 0L
        )

        awaitTask(playlistRef.set(payload))
        return playlistRef.id
    }

    suspend fun addToPlaylist(userId: String, playlistId: String, track: MusicTrack) {
        val saavnId = track.saavnId ?: track.id
        val playlistRef = playlistsCollection(userId).document(playlistId)
        val songRef = playlistSongsCollection(userId, playlistId).document(saavnId)

        awaitTask(
            firestore.runTransaction { transaction ->
                val playlistSnapshot = transaction.get(playlistRef)
                if (!playlistSnapshot.exists()) {
                    error("Playlist does not exist.")
                }

                val songSnapshot = transaction.get(songRef)
                if (songSnapshot.exists()) {
                    return@runTransaction Unit
                }

                val currentCount = playlistSnapshot.getLong("songCount") ?: 0L
                val coverUrl = playlistSnapshot.getString("coverUrl").orEmpty()
                val payload = buildTrackPayload(track) + mapOf(
                    "saavnId" to saavnId,
                    "order" to currentCount.toInt(),
                    "addedAt" to FieldValue.serverTimestamp()
                )

                transaction.set(songRef, payload)
                transaction.update(
                    playlistRef,
                    mapOf(
                        "songCount" to currentCount + 1L,
                        "coverUrl" to if (coverUrl.isBlank()) {
                            track.imageUrl.orEmpty()
                        } else {
                            coverUrl
                        }
                    )
                )
            }
        )
    }

    suspend fun removeFromPlaylist(userId: String, playlistId: String, saavnId: String) {
        val playlistRef = playlistsCollection(userId).document(playlistId)
        val songRef = playlistSongsCollection(userId, playlistId).document(saavnId)

        awaitTask(
            firestore.runTransaction { transaction ->
                val playlistSnapshot = transaction.get(playlistRef)
                if (!playlistSnapshot.exists()) {
                    return@runTransaction Unit
                }

                val songSnapshot = transaction.get(songRef)
                if (!songSnapshot.exists()) {
                    return@runTransaction Unit
                }

                val currentCount = playlistSnapshot.getLong("songCount") ?: 0L
                val currentCoverUrl = playlistSnapshot.getString("coverUrl").orEmpty()
                val removedCoverUrl = songSnapshot.getString("imageUrl").orEmpty()

                transaction.delete(songRef)
                transaction.update(
                    playlistRef,
                    mapOf(
                        "songCount" to (currentCount - 1L).coerceAtLeast(0L),
                        "coverUrl" to if (currentCoverUrl == removedCoverUrl) "" else currentCoverUrl
                    )
                )
            }
        )
    }

    fun getPlaylists(userId: String): Flow<List<Playlist>> = callbackFlow {
        val listener = playlistsCollection(userId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }

                trySend(snapshot?.documents.orEmpty().map(::toPlaylist))
            }

        awaitClose { listener.remove() }
    }.conflate()

    fun getPlaylistSongs(userId: String, playlistId: String): Flow<List<MusicTrack>> = callbackFlow {
        val listener = playlistSongsCollection(userId, playlistId)
            .orderBy("order", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }

                trySend(snapshot?.documents.orEmpty().mapNotNull(::toStoredTrack))
            }

        awaitClose { listener.remove() }
    }.conflate()

    private fun likedSongsCollection(userId: String) =
        firestore.collection(USERS_COLLECTION)
            .document(userId)
            .collection(LIKED_SONGS_COLLECTION)

    private fun playlistsCollection(userId: String) =
        firestore.collection(USERS_COLLECTION)
            .document(userId)
            .collection(PLAYLISTS_COLLECTION)

    private fun playlistSongsCollection(userId: String, playlistId: String) =
        playlistsCollection(userId)
            .document(playlistId)
            .collection(PLAYLIST_SONGS_COLLECTION)

    private fun buildTrackPayload(track: MusicTrack): Map<String, Any> {
        val saavnId = track.saavnId ?: track.id
        return buildMap {
            put("saavnId", saavnId)
            put("name", track.title)
            put("artistName", track.artistName ?: track.artist)
            put("imageUrl", track.imageUrl ?: track.coverUrl.orEmpty())
            put("streamUrl", track.streamUrl ?: track.playbackUri.toString())
            put("album", track.album)
            put("duration", track.durationMs)
        }
    }

    private fun toStoredTrack(document: DocumentSnapshot): MusicTrack? {
        val streamUrl = document.getString("streamUrl").orEmpty().trim()
        if (streamUrl.isBlank()) return null

        val saavnId = document.getString("saavnId").orEmpty().ifBlank { document.id }
        val imageUrl = document.getString("imageUrl")
        val artistName = document.getString("artistName").orEmpty().ifBlank { "Unknown artist" }

        return MusicTrack(
            id = saavnId,
            title = document.getString("name").orEmpty().ifBlank { "Untitled track" },
            artist = artistName,
            album = document.getString("album").orEmpty().ifBlank { "Single" },
            durationMs = document.getLong("duration") ?: 0L,
            playbackUri = streamUrl.toUri(),
            source = MusicSource.REMOTE,
            imageUrl = imageUrl,
            coverUrl = imageUrl,
            remoteDocId = document.id,
            isOnline = true,
            streamUrl = streamUrl,
            saavnId = saavnId,
            artistName = artistName
        )
    }

    private fun toPlaylist(document: DocumentSnapshot): Playlist {
        val createdAtMillis = when (val rawValue = document.get("createdAt")) {
            is Timestamp -> rawValue.toDate().time
            is Number -> rawValue.toLong()
            else -> 0L
        }

        return Playlist(
            id = document.id,
            name = document.getString("name").orEmpty(),
            coverUrl = document.getString("coverUrl"),
            songCount = (document.getLong("songCount") ?: 0L).toInt(),
            createdAt = createdAtMillis
        )
    }

    private suspend fun <T> awaitTask(task: Task<T>): T = suspendCancellableCoroutine { continuation ->
        task.addOnSuccessListener { result -> continuation.resume(result) }
        task.addOnFailureListener { error -> continuation.resumeWithException(error) }
    }

    private companion object {
        const val USERS_COLLECTION = "users"
        const val LIKED_SONGS_COLLECTION = "liked_songs"
        const val PLAYLISTS_COLLECTION = "playlists"
        const val PLAYLIST_SONGS_COLLECTION = "songs"
    }
}
