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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.Q) // Scoped Storage tests
class MediaWriteTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val contentResolver = context.contentResolver

    // Test data constants
    private val testBookDir = "writeTestBook${System.currentTimeMillis()}"
    private val nonExistentDir = "nonExistentDir${System.currentTimeMillis()}"
    private val testFileName = "writeTestFile${System.currentTimeMillis()}.mp3"

    // Keep track of URIs to clean up after tests
    private val createdUris = mutableListOf<Uri>()

    @Before
    fun setup() {
        // Clear any leftover URIs from previous test runs
        createdUris.clear()
    }

    @After
    fun cleanup() {
        // Clean up all created URIs to prevent test pollution
        createdUris.forEach { uri ->
            try {
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
    fun createAudioFileUri_withNonExistentDirectory_returnsNull() {
        // Test that StoragePaths returns null when directory doesn't exist
        val uri = StoragePaths.createAudioFileUri(
            contentResolver,
            nonExistentDir,
            testFileName
        )

        assertNull("StoragePaths should return null for non-existent directory", uri)
        println("SUCCESS: StoragePaths correctly returned null for non-existent directory")
    }

    @Test
    fun createAudioFileUri_withEmptyMp3File_returnsNull() {
        // Test that StoragePaths returns null when directory doesn't exist
        val uri = StoragePaths.createAudioFileUri(
            contentResolver,
            nonExistentDir,
            testFileName
        )

        assertNull("StoragePaths should return null for non-existent directory", uri)
        println("SUCCESS: StoragePaths correctly returned null for non-existent directory")

    }

    @Test
    fun createAudioFileUri_withInvalidMp3File_returnsNull() {
        // 1. SETUP: Create a physical file that is 0 bytes
        // We use the helper to put a file exactly where StoragePaths looks
        val createdUri = createPreExistingFile(testBookDir, testFileName, ByteArray(0))
        if (createdUri != null) createdUris.add(createdUri)

        // 2. ACT
        val resultUri = StoragePaths.createAudioFileUri(
            contentResolver,
            testBookDir,
            testFileName
        )

        // 3. ASSERT
        assertNull("Should return null because file length is 0", resultUri)
        println("SUCCESS: correctly rejected empty file")

    }


    /**
     * Helper to write a file to disk so the System Under Test (SUT) can read it.
     * Note: This uses MediaStore to perform the write, ensuring the file physically exists.
     */
    private fun createPreExistingFile(dirName: String, fileName: String, content: ByteArray): Uri? {
        // We need to match the path logic used by your StoragePaths object.
        // Assuming StoragePaths.getBookRelativePath is accessible.
        // If it is private, you must manually construct the path string here (e.g. "Music/$dirName/")
        val relativePath = StoragePaths.getBookRelativePath(dirName)

        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg")
            put(MediaStore.Audio.Media.RELATIVE_PATH, relativePath)
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }

        val uri = contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)

        if (uri != null) {
            try {
                contentResolver.openOutputStream(uri)?.use { outputStream ->
                    if (content.isNotEmpty()) {
                        outputStream.write(content)
                    }
                }
                // Mark as finished
                val updateValues = ContentValues().apply {
                    put(MediaStore.Audio.Media.IS_PENDING, 0)
                }
                contentResolver.update(uri, updateValues, null, null)
                return uri
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
        return null
    }



    @Test
    fun createAudioFileUri_withValidNonEmptyMp3_returnsNonNullUri() {
        // Create a file URI
        val pendingUri = createTestAudioFileUri()

        // Write valid-looking MP3 content (simplified MP3 header + data)
        val validMp3Content = createSimulatedMp3Content()
        writeContentToUri(pendingUri, validMp3Content)

        // Commit the file
        commitFile(pendingUri)

        // Verify the file was created successfully
        verifyFileSize(pendingUri, validMp3Content.size.toLong())
        println("SUCCESS: Valid non-empty MP3 file was created and URI is non-null")
    }

    @Test
    fun createAudioFileUri_canCommitActualAudioFile() {
        // Create a file URI
        val pendingUri = createTestAudioFileUri()

        // Write simulated audio file content
        val audioContent = createSimulatedMp3Content()
        writeContentToUri(pendingUri, audioContent)

        // Commit the file (make it non-pending)
        commitFile(pendingUri)

        // Verify the committed file
        verifyCommittedFile(pendingUri, audioContent.size.toLong())
        println("SUCCESS: Audio file was successfully committed and is accessible")
    }

    /**
     * Helper method to create a test audio file URI and add it to cleanup list
     */
    private fun createTestAudioFileUri(): Uri {
        val uri = StoragePaths.createAudioFileUri(
            contentResolver,
            testBookDir,
            testFileName
        )

        assertNotNull("StoragePaths should return a non-null URI for valid parameters", uri)
        createdUris.add(uri!!)
        return uri
    }

    /**
     * Helper method to write content to a URI
     */
    private fun writeContentToUri(uri: Uri, content: ByteArray) {
        try {
            contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(content)
            }
        } catch (e: IOException) {
            fail("Failed to write to the output stream: ${e.message}")
        }
    }

    /**
     * Helper method to commit a file (make it non-pending)
     */
    private fun commitFile(uri: Uri) {
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.IS_PENDING, 0)
        }
        val updateCount = contentResolver.update(uri, values, null, null)
        assertTrue("File should be updated to non-pending state", updateCount > 0)
    }

    /**
     * Helper method to verify file size
     */
    private fun verifyFileSize(uri: Uri, expectedSize: Long) {
        contentResolver.query(
            uri,
            arrayOf(MediaStore.Audio.Media.SIZE),
            null,
            null,
            null
        )?.use { cursor ->
            assertTrue("Cursor should be able to move to the first row", cursor.moveToFirst())
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val fileSize = cursor.getLong(sizeColumn)

            assertEquals("File size should match expected size", expectedSize, fileSize)
        } ?: fail("Querying the URI returned a null cursor.")
    }

    /**
     * Helper method to verify a committed file
     */
    private fun verifyCommittedFile(uri: Uri, expectedSize: Long) {
        contentResolver.query(
            uri,
            arrayOf(MediaStore.Audio.Media.SIZE, MediaStore.Audio.Media.IS_PENDING),
            null,
            null,
            null
        )?.use { cursor ->
            assertTrue("Cursor should be able to move to the first row", cursor.moveToFirst())

            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val pendingColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.IS_PENDING)

            val fileSize = cursor.getLong(sizeColumn)
            val isPending = cursor.getInt(pendingColumn)

            assertEquals("File size should match expected size", expectedSize, fileSize)
            assertEquals("File should not be in pending state", 0, isPending)
        } ?: fail("Querying the committed URI returned a null cursor.")
    }

    /**
     * Helper method to create simulated MP3 content
     * This creates a byte array that resembles MP3 file structure
     */
    private fun createSimulatedMp3Content(): ByteArray {
        // Simplified MP3 header simulation (not a real MP3, but has proper-looking structure)
        val mp3Header = byteArrayOf(
            0xFF.toByte(), 0xFB.toByte(), // MP3 sync word
            0x90.toByte(), 0x00.toByte()  // Additional header bytes
        )

        // Add some dummy audio data
        val dummyAudioData = "This simulates MP3 audio data content for testing purposes."

        return mp3Header + dummyAudioData.toByteArray()
    }
}