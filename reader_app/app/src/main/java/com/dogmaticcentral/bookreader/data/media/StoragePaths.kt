
package com.yourpackage.data.media

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

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
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val id = cursor.getLong(idColumn)
                Uri.withAppendedPath(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
        }

        return contentResolver.insert(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            values
        )
    }

    /**
     * Get legacy absolute file path (for Android < 10)
     * @param bookDirectoryName Sanitized book directory name
     * @param fileName Audio file name
     * @return Absolute file path string
     */
    @Suppress("DEPRECATION")
    fun getLegacyAbsolutePath(bookDirectoryName: String, fileName: String): String {
        val externalStorage = Environment.getExternalStorageDirectory()
        val audioBooksDir = Environment.DIRECTORY_AUDIOBOOKS
        return "$externalStorage/$audioBooksDir/$APP_NAME/$AUDIO_SUBFOLDER/$bookDirectoryName/$fileName"
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
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Use MediaStore for Android 10+
            val uri = queryAudioFileUri(
                context.contentResolver,
                bookDirectoryName,
                fileName
            )
            if (uri != null) {
                AudioFileLocation.MediaStoreUri(uri)
            } else {
                AudioFileLocation.NotFound
            }
        } else {
            // Use legacy file path for older versions
            @Suppress("DEPRECATION")
            val path = getLegacyAbsolutePath(bookDirectoryName, fileName)
            val file = File(path)
            if (file.exists()) {
                AudioFileLocation.LegacyPath(path)
            } else {
                AudioFileLocation.NotFound
            }
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
fun String.toCamelCaseDirectory(): String {
    return this
        .trim()
        .split(Regex("\\s+")) // Split on whitespace
        .filter { it.isNotEmpty() }
        .mapIndexed { index, word ->
            val cleaned = word.replace(Regex("[^a-zA-Z0-9]"), "")
            if (cleaned.isEmpty()) return@mapIndexed ""
            if (index == 0) {
                cleaned.lowercase()
            } else {
                cleaned.replaceFirstChar { it.uppercase() }
            }
        }
        .joinToString("")
        .takeIf { it.isNotEmpty() } ?: "unknown"
}