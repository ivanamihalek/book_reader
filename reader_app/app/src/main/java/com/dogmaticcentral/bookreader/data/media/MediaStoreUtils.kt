package com.dogmaticcentral.bookreader.data.media

import android.app.AlertDialog
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.IOException

fun saveMp3ToMediaStore(context: Context, sourceFilePath: String): Uri? {
    val resolver = context.contentResolver

    // Step 1: Check if file is already indexed in MediaStore
    val existingUri = getAudioContentUriFromFilePath(context, sourceFilePath)
    if (existingUri != null) {
        return existingUri
    }

    // Step 2: Prepare to insert new file
    val audioCollection =
        MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

    val contentValues = ContentValues().apply {
        val displayName = sourceFilePath.substringAfterLast('/')
        put(MediaStore.Audio.Media.DISPLAY_NAME, displayName) // e.g. "MySong.mp3"
        put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg")
        var relativePath: String = sourceFilePath.substringBeforeLast('/')
        // the relative path must start with Audiobooks
        // the physical path starts with /sdcard/, or more generally, ExternalStorageDirectory
        val externalStorageDirectory = android.os.Environment.getExternalStorageDirectory()
        if (relativePath.startsWith("$externalStorageDirectory/")) {
            relativePath = relativePath.substringAfter("$externalStorageDirectory/")
        }
        Log.d("[ivana] MediaStore", "relativePath: $relativePath")
        put(MediaStore.Audio.Media.RELATIVE_PATH, relativePath)
        put(MediaStore.Audio.Media.IS_PENDING, 1)
    }

    val newAudioUri = resolver.insert(audioCollection, contentValues)
    Log.d("[ivana] MediaStore", "newAudioUri: $newAudioUri")
    if (newAudioUri != null) {
        try {
            resolver.openOutputStream(newAudioUri)?.use { outputStream ->
                FileInputStream(sourceFilePath).use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            contentValues.clear()
            contentValues.put(MediaStore.Audio.Media.IS_PENDING, 0)
            resolver.update(newAudioUri, contentValues, null, null)

            Log.d("[ivana] MediaStore", "File saved and indexed: $newAudioUri")
            return newAudioUri

        } catch (e: IOException) {
            Log.e("[ivana] MediaStore", "Failed to save file: ${e.message}")
            resolver.delete(newAudioUri, null, null)
        }
    }
    return null
}

// Helper function to find existing Uri by file path
fun getAudioContentUriFromFilePath(context: Context, filePath: String): Uri? {
    val projection = arrayOf(MediaStore.Audio.Media._ID)
    val selection = "${MediaStore.Audio.Media.DATA} = ?"
    val selectionArgs = arrayOf(filePath)

    val internalFile = File(filePath)
    if (!internalFile.exists()) {
        AlertDialog.Builder(context)
                .setTitle("File not found")
                .setMessage("The file you are trying to play ($filePath) does not exist.")
                .setCancelable(false)
                .setPositiveButton("OK") { _, _ ->
                    // Close the activity gracefully
                }
                .show()
        return null
    }
    context.contentResolver.query(
        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
        projection,
        selection,
        selectionArgs,
        null
    )?.use { cursor ->
        if (cursor.moveToFirst()) {
            val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID))
            return Uri.withAppendedPath(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id.toString())
        }
    }

    return null
}
