package com.dogmaticcentral.bookreader.data.media

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.io.FileInputStream
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

        // 1. Construct the paths
        val relativePath = getBookRelativePath(bookDirectoryName)
        val externalRoot = Environment.getExternalStorageDirectory()

        // The directory object
        val directory = File(externalRoot, relativePath)

        // The actual physical file object we want to check
        val targetFile = File(directory, fileName)

        // 2. CHECK: Directory must exist
        if (!directory.exists() || !directory.isDirectory) {
            Log.e("StoragePaths.kt", "Directory does not exist: $relativePath")
            return null
        }

        // 3. CHECK: File must exist, be non-empty, and be a valid MP3
        if (!targetFile.exists()) {
            Log.e("StoragePaths.kt", "Target file does not exist: $fileName")
            return null
        }

        if (targetFile.length() <= 0) {
            Log.e("StoragePaths.kt", "Target file is empty.")
            return null
        }

        if (!isValidMp3File(targetFile)) {
            Log.e("StoragePaths.kt", "Target file is not a valid MP3.")
            return null
        }

        // 4. If we pass checks, INSERT into ContentResolver
        Log.d("StoragePaths.kt", "Validation passed. Creating URI for: $relativePath")

        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Audio.Media.MIME_TYPE, mimeType)
            put(MediaStore.Audio.Media.RELATIVE_PATH, getBookRelativePath(bookDirectoryName) )
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        // Use app-scoped volume instead of the legacy external shared URI
        val uri = contentResolver.insert(collectionUri, values)

        // Validation Logic: Ensure the physical file exists/is accessible
        return if (uri != null && isFileAccessible(contentResolver, uri)) {
            uri
        } else {
            // If uri exists but file doesn't, delete the empty DB row and return null
            if (uri != null) {
                try { contentResolver.delete(uri, null, null) }
                catch (e: Exception) { /* Ignore cleanup error */ }
            }
            null
        }
    }

    /**
     * Helper to verify a URI points to a valid, accessible physical file.
     */
    private fun isFileAccessible(contentResolver: ContentResolver, uri: Uri): Boolean {
        // Attempt to open the file. If it doesn't exist physically,
        // this throws FileNotFoundException.

        return contentResolver.openFileDescriptor(uri, "r")?.use {
                true // File opened successfully
            } ?: false

    }
    /**
     * Helper to validate MP3 headers using standard Java IO
     */
    private fun isValidMp3File(file: File): Boolean {
        return try {
            FileInputStream(file).use { fis ->
                val header = ByteArray(3)
                // If we can't read 3 bytes, it's not valid
                if (fis.read(header) != 3) return false

                // Check for "ID3" tag (Hex: 49 44 33)
                if (header[0] == 0x49.toByte() &&
                    header[1] == 0x44.toByte() &&
                    header[2] == 0x33.toByte()) {
                    return true
                }

                // Check for MPEG Frame Sync (First 11 bits set to 1)
                // Byte 0: 0xFF
                // Byte 1: 0xE0 (top 3 bits set)
                val b0 = header[0].toInt() and 0xFF
                val b1 = header[1].toInt() and 0xFF
                if (b0 == 0xFF && (b1 and 0xE0) == 0xE0) {
                    return true
                }

                return false
            }
        } catch (e: Exception) {
            Log.e("StoragePaths.kt", "Error reading file header", e)
            false
        }
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
 * Extension function to convert book title to a safe PascalCase directory name
 */
fun String?.toPascalCase(): String {
    if (this.isNullOrBlank()) return "unknown"
    val cleanedInstr = Pattern.compile("\\W+").matcher(this).replaceAll(" ").trim()
    if (cleanedInstr.isBlank()) return "unknown"
    val tokens = cleanedInstr.lowercase().split("\\s+".toRegex())
    val tokensCamelCased = tokens.map { it.replaceFirstChar { char -> char.uppercaseChar() } }
    return tokensCamelCased.joinToString(separator = "")

}