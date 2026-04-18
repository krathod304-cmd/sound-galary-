package com.example.spotify.music

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LocalMusicRepository {

    suspend fun loadDownloadedTracks(context: Context): List<MusicTrack> = withContext(Dispatchers.IO) {
        val resolver = context.applicationContext.contentResolver
        val collections = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY))
            }
            add(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI)
        }.distinct()

        val projection = buildList {
            add(MediaStore.Audio.Media._ID)
            add(MediaStore.Audio.Media.TITLE)
            add(MediaStore.Audio.Media.ARTIST)
            add(MediaStore.Audio.Media.ALBUM)
            add(MediaStore.Audio.Media.DURATION)
            add(MediaStore.Audio.Media.DATE_ADDED)
            add(MediaStore.MediaColumns.DISPLAY_NAME)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.MediaColumns.RELATIVE_PATH)
            } else {
                @Suppress("DEPRECATION")
                add(MediaStore.Audio.Media.DATA)
            }
        }.toTypedArray()

        val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"

        val tracks = mutableListOf<MusicTrack>()
        val seenKeys = mutableSetOf<String>()
        collections.forEach { collection ->
            resolver.query(collection, projection, null, null, sortOrder)?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durationIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val displayNameIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val relativePathIndex = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                } else {
                    -1
                }
                val dataIndex = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    @Suppress("DEPRECATION")
                    cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
                } else {
                    -1
                }

                while (cursor.moveToNext()) {
                    val trackId = cursor.getLong(idIndex)
                    val contentUri = ContentUris.withAppendedId(collection, trackId)
                    val relativePath = if (relativePathIndex >= 0) cursor.getString(relativePathIndex) else null
                    val dataPath = if (dataIndex >= 0) cursor.getString(dataIndex) else null
                    val displayName = cursor.getString(displayNameIndex).orEmpty()
                    val trackKey = dataPath ?: "${relativePath.orEmpty()}|${displayName.lowercase()}"
                    val isDownloaded = isDownloadedTrack(relativePath, dataPath)

                    if (!seenKeys.add(trackKey) || !isDownloaded) {
                        continue
                    }

                    val fallbackTitle = cursor.getString(titleIndex)
                        .cleanMetadata()
                        ?: displayName.removeFileExtension().ifBlank { UNKNOWN_TRACK }
                    val fallbackArtist = cursor.getString(artistIndex)
                        .cleanMetadata()
                        ?: UNKNOWN_ARTIST
                    val fallbackAlbum = cursor.getString(albumIndex)
                        .cleanMetadata()
                        ?: UNKNOWN_ALBUM
                    val fallbackDuration = cursor.getLong(durationIndex)

                    val embeddedMetadata = extractEmbeddedMetadata(
                        context = context.applicationContext,
                        contentUri = contentUri,
                        dataPath = dataPath
                    )

                    val resolvedDuration = embeddedMetadata.durationMs ?: fallbackDuration
                    if (resolvedDuration <= 0L) {
                        continue
                    }

                    tracks += MusicTrack(
                        id = "local:$trackId",
                        title = embeddedMetadata.title ?: fallbackTitle,
                        artist = embeddedMetadata.artist ?: fallbackArtist,
                        album = embeddedMetadata.album ?: fallbackAlbum,
                        durationMs = resolvedDuration,
                        playbackUri = contentUri,
                        source = MusicSource.LOCAL,
                        relativePath = relativePath,
                        dataPath = dataPath,
                        albumArt = embeddedMetadata.albumArt,
                        isDownloaded = true
                    )
                }
            }
        }

        tracks
    }

    private fun isDownloadedTrack(relativePath: String?, dataPath: String?): Boolean {
        val relative = relativePath.orEmpty().lowercase()
        val absolute = dataPath.orEmpty().lowercase()
        return relative.contains("download") || absolute.contains("/download/")
    }

    private fun extractEmbeddedMetadata(
        context: Context,
        contentUri: android.net.Uri,
        dataPath: String?
    ): ExtractedTrackMetadata {
        val retriever = MediaMetadataRetriever()
        return try {
            if (!dataPath.isNullOrBlank()) {
                runCatching { retriever.setDataSource(dataPath) }
                    .getOrElse { retriever.setDataSource(context, contentUri) }
            } else {
                retriever.setDataSource(context, contentUri)
            }

            ExtractedTrackMetadata(
                title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE).cleanMetadata(),
                artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST).cleanMetadata(),
                album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM).cleanMetadata(),
                durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull()
                    ?.takeIf { it > 0L },
                albumArt = retriever.embeddedPicture?.let(::decodeAlbumArt)
            )
        } catch (_: Exception) {
            ExtractedTrackMetadata()
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun decodeAlbumArt(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, ARTWORK_TARGET_SIZE)
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }

    private fun calculateInSampleSize(width: Int, height: Int, targetSize: Int): Int {
        var sampleSize = 1
        var scaledWidth = width
        var scaledHeight = height
        while (scaledWidth / 2 >= targetSize && scaledHeight / 2 >= targetSize) {
            sampleSize *= 2
            scaledWidth /= 2
            scaledHeight /= 2
        }
        return sampleSize.coerceAtLeast(1)
    }

    private fun String?.cleanMetadata(): String? {
        return this
            ?.trim()
            ?.takeIf { it.isNotBlank() && it != "<unknown>" }
    }

    private fun String.removeFileExtension(): String {
        return substringBeforeLast(".").trim()
    }

    private data class ExtractedTrackMetadata(
        val title: String? = null,
        val artist: String? = null,
        val album: String? = null,
        val durationMs: Long? = null,
        val albumArt: Bitmap? = null
    )

    private companion object {
        const val ARTWORK_TARGET_SIZE = 512
        const val UNKNOWN_TRACK = "Unknown track"
        const val UNKNOWN_ARTIST = "Unknown artist"
        const val UNKNOWN_ALBUM = "Unknown album"
    }
}
