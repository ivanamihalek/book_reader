package com.dogmaticcentral.bookreader.data.media

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import java.util.regex.Pattern

/**
 * Centralized storage path management for audio book files.
 * Handles both Scoped Storage (Android 10+) and legacy file access.
 */
object StoragePaths {

    // Constants
    private const val APP_NAME = "BookReader"
    private const val AUDIO_SUBFOLDER = "audio"

    /**
     * Relative path for MediaStore (Android 10+)
     * Used with MediaStore.Audio.Media.RELATIVE_PATH
     */
    const val RELATIVE_PATH = "Audiobooks/$APP_NAME/$AUDIO_SUBFOLDER/"

    // A single source of truth for the correct MediaStore collection URI ---
    /**
     * Gets the appropriate MediaStore collection URI for audio files.
     * Uses the modern volume-specific URI on Android Q+ and falls back to the legacy URI.
     */
    private val collectionUri: Uri get() =
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)


    /**
     * Builds the book-specific relative path
     * @param bookDirectoryName Sanitized book directory name (e.g., "harryPotter")
     * @return Path like "Audiobooks/BookReader/audio/harryPotter/"
     */
    fun getBookRelativePath(bookDirectoryName: String): String {
        return "$RELATIVE_PATH$bookDirectoryName/"
    }

    /**
     * Builds the full relative path including filename
     * @param bookDirectoryName Sanitized book directory name
     * @param fileName Audio file name (e.g., "chapter1.mp3")
     * @return Full path like "Audiobooks/BookReader/audio/harryPotter/chapter1.mp3"
     */
    fun getFullRelativePath(bookDirectoryName: String, fileName: String): String {
        return "${getBookRelativePath(bookDirectoryName)}$fileName"
    }

    /**
     * Query for an audio file URI in MediaStore (Android 10+)
     * @param contentResolver ContentResolver instance
     * @param bookDirectoryName Sanitized book directory name
     * @param fileName Audio file name
     * @return Uri if file exists, null otherwise
     */
    fun queryAudioFileUri(
        contentResolver: ContentResolver,
        bookDirectoryName: String,
        fileName: String
    ): Uri? {
        val relativePath = getBookRelativePath(bookDirectoryName)

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.RELATIVE_PATH
        )

        val selection = "${MediaStore.Audio.Media.DISPLAY_NAME} = ? AND " +
                "${MediaStore.Audio.Media.RELATIVE_PATH} = ?"
        val selectionArgs = arrayOf(fileName, relativePath)

        return contentResolver.query(
            collectionUri,
            projection,
            selection,
            selectionArgs,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val id = cursor.getLong(idColumn)
                Uri.withAppendedPath(
                    collectionUri,
                    id.toString()
                )
            } else {
                null
            }
        }
    }

    /**
     * Create a new audio file entry in MediaStore (Android 10+)
     * @param contentResolver ContentResolver instance
     * @param bookDirectoryName Sanitized book directory name
     * @param fileName Audio file name
     * @param mimeType MIME type (default: "audio/mpeg")
     * @return Uri of created file entry, or null on failure
     */
    fun createAudioFileUri(
        contentResolver: ContentResolver,
        bookDirectoryName: String,
        fileName: String,
        mimeType: String = "audio/mpeg"
    ): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Audio.Media.MIME_TYPE, mimeType)
            put(MediaStore.Audio.Media.RELATIVE_PATH, getBookRelativePath(bookDirectoryName))
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        // Use app-scoped volume instead of the legacy external shared URI
        return contentResolver.insert(collectionUri, values)
    }


    /**
     * Get the appropriate audio file location based on Android version
     * Returns either a MediaStore Uri (Android 10+) or legacy file path
     */
    fun getAudioFileLocation(
        context: Context,
        bookDirectoryName: String,
        fileName: String
    ): AudioFileLocation {
        val uri = queryAudioFileUri(
            context.contentResolver,
            bookDirectoryName,
            fileName
        )
        return if (uri == null) {
            AudioFileLocation.NotFound

        } else {
            AudioFileLocation.MediaStoreUri(uri)
        }
    }

}

/**
 * Sealed class representing the location of an audio file
 */
sealed class AudioFileLocation {
    /** MediaStore Uri (Android 10+) */
    data class MediaStoreUri(val uri: Uri) : AudioFileLocation()

    /** Legacy absolute file path (Android < 10) */
    data class LegacyPath(val path: String) : AudioFileLocation()

    /** File not found */
    object NotFound : AudioFileLocation()
}

/**
 * Extension function to convert book title to a safe camelCase directory name
 */
fun String?.toCamelCase(): String {
    if (this.isNullOrBlank()) return "unknown"
    val cleanedInstr = Pattern.compile("\\W+").matcher(this).replaceAll(" ").trim()
    if (cleanedInstr.isBlank()) return "unknown"
    val tokens = cleanedInstr.lowercase().split("\\s+".toRegex())
    val tokensCamelCased = listOf(tokens.first()) + tokens.drop(1).map { it.replaceFirstChar { char -> char.uppercaseChar() } }
    return tokensCamelCased.joinToString(separator = "")

}