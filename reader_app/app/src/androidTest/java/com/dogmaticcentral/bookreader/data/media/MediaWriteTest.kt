package com.dogmaticcentral.bookreader.data.media

import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.Q) // Scoped Storage tests
class MediaWriteTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val contentResolver = context.contentResolver

    private val testBookDir = "writeTestBook${System.currentTimeMillis()}"
    private val testFileName = "writeTestFile${System.currentTimeMillis()}.mp3"

    // Keep track of URIs to clean up after the test
    private val createdUris = mutableListOf<Uri>()

    @Before
    fun setup() {
        // No setup needed for this simple test
    }

    @After
    fun cleanup() {
        createdUris.forEach { uri ->
            try {
                // Use a where clause to prevent deleting other media by mistake
                val selection = "${MediaStore.Audio.Media._ID} = ?"
                val selectionArgs = arrayOf(uri.lastPathSegment)
                contentResolver.delete(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, selection, selectionArgs)
            } catch (e: Exception) {
                // Log error but don't fail the test in cleanup
                e.printStackTrace()
            }
        }
        createdUris.clear()
    }

    @Test
    fun canWriteAndCommitAudioFile() {
        // --- 1. Create a Pending File Entry ---
        // This calls the function from your StoragePaths object
        val pendingUri = StoragePaths.createAudioFileUri(
            contentResolver,
            testBookDir,
            testFileName
        )

        assertNotNull("MediaStore insert should return a non-null URI", pendingUri)
        println("SUCCESS: MediaStore insert returned a non-null URI: $pendingUri")
        createdUris.add(pendingUri!!) // Add to cleanup list immediately

        // --- 2. Write Data to the File ---
        val dummyContent = "This is dummy audio data."
        val dummyBytes = dummyContent.toByteArray()

        try {
            contentResolver.openOutputStream(pendingUri)?.use { outputStream ->
                outputStream.write(dummyBytes)
            }
        } catch (e: IOException) {
            fail("Failed to write to the output stream: ${e.message}")
        }

        // --- 3. Commit the File (Make it non-pending) ---
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.IS_PENDING, 0)
        }
        val updateCount = contentResolver.update(pendingUri, values, null, null)
        assertTrue("File should be updated to non-pending state", updateCount > 0)

        // --- 4. Verification ---
        // The ultimate proof: can we query the file by its URI and check its size?
        contentResolver.query(pendingUri, arrayOf(MediaStore.Audio.Media.SIZE),
            null, null, null)?.use { cursor ->
            assertTrue("Cursor should be able to move to the first row", cursor.moveToFirst())
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val fileSize = cursor.getLong(sizeColumn)

            assertEquals("File size on disk should match the size of the data we wrote",
                dummyBytes.size.toLong(), fileSize)
            println("SUCCESS: File written and committed successfully with size: $fileSize")
        } ?: fail("Querying the committed URI returned a null cursor.")
    }
}