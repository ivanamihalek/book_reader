package com.dogmaticcentral.bookreader.data.media

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.Q) // These tests are for Scoped Storage
class StoragePathsTest {

    /**
     * This rule is ESSENTIAL. It grants the READ permission before each test,
     * which is required for any function that queries the general MediaStore collection
     * (like our queryAudioFileUri).
     */
    @get:Rule
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.READ_MEDIA_AUDIO
    )

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val contentResolver = context.contentResolver
    private val createdUris = mutableListOf<Uri>()

    @After
    fun tearDown() {
        // Clean up any files created during the tests
        createdUris.forEach { uri ->
            try {
                // Using a specific ID-based clause is safer for deletion
                val selection = "${MediaStore.Audio.Media._ID} = ?"
                val selectionArgs = arrayOf(uri.lastPathSegment)
                contentResolver.delete(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, selection, selectionArgs)
            } catch (e: Exception) {
                // Log and ignore errors during cleanup
                System.err.println("Error during cleanup: ${e.message}")
            }
        }
        createdUris.clear()
    }

    /**
     * A private helper function to reliably create and commit a test file.
     * This avoids code duplication and ensures a consistent state for query tests.
     */
    private fun createAndCommitTestFile(bookDir: String, fileName: String): Uri {
        // 1. Create the pending URI entry
        val pendingUri = StoragePaths.createAudioFileUri(contentResolver, bookDir, fileName)
        assertNotNull("Helper failed: createAudioFileUri returned null", pendingUri)
        createdUris.add(pendingUri!!)

        // 2. Write some dummy data to it so it's a real file
        try {
            contentResolver.openOutputStream(pendingUri)?.use { it.write("dummy data".toByteArray()) }
        } catch (e: IOException) {
            throw IOException("Helper failed: Could not write to output stream", e)
        }

        // 3. Commit the file by marking it as non-pending
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.IS_PENDING, 0)
        }
        val updatedRows = contentResolver.update(pendingUri, values, null, null)
        assertTrue("Helper failed: File was not committed (update rows was 0)", updatedRows > 0)

        return pendingUri
    }

    @Test
    fun createAudioFileUri_succeedsAndHasCorrectMetadata() {
        val bookDir = "CreateTestBook"
        val fileName = "chapter1.mp3"

        // Act
        val createdUri = StoragePaths.createAudioFileUri(contentResolver, bookDir, fileName)
        assertNotNull("createAudioFileUri should return a non-null URI", createdUri)
        createdUris.add(createdUri!!) // Ensure cleanup

        // Assert
        // We can query the specific URI we received without needing extra permissions
        contentResolver.query(createdUri, null, null, null, null)?.use { cursor ->
            assertTrue("Cursor should not be empty", cursor.moveToFirst())

            val displayName = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME))
            val relativePath = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.RELATIVE_PATH))
            val isPending = cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.IS_PENDING))

            assertEquals("Display name should match", fileName, displayName)
            assertEquals("Relative path should match", StoragePaths.getBookRelativePath(bookDir), relativePath)
            assertEquals("File should be marked as pending", 1, isPending)
        }
    }

    @Test
    fun queryAudioFileUri_findsCorrectFile_whenItExists() {
        val bookDir = "queryTestBook_${System.currentTimeMillis()}"
        val fileName = "existingFile.mp3"

        // Arrange: Create the file we expect to find
        val expectedUri = createAndCommitTestFile(bookDir, fileName)

        // Act: Use the function under test to try and find it
        val queriedUri = StoragePaths.queryAudioFileUri(contentResolver, bookDir, fileName)

        // Assert
        assertNotNull("queryAudioFileUri should have found the file", queriedUri)
        assertEquals("The found URI should match the created URI", expectedUri, queriedUri)
    }

    @Test
    fun queryAudioFileUri_returnsNull_whenFileDoesNotExist() {
        // Act
        val queriedUri = StoragePaths.queryAudioFileUri(
            contentResolver,
            "nonExistentBook",
            "nonExistentFile.mp3"
        )

        // Assert
        assertNull("queryAudioFileUri should return null for a non-existent file", queriedUri)
    }

    @Test
    fun getAudioFileLocation_returnsMediaStoreUri_forExistingFile() {
        val bookDir = "locationTestBook_${System.currentTimeMillis()}"
        val fileName = "locationFile.mp3"

        // Arrange
        val createdUri = createAndCommitTestFile(bookDir, fileName)

        // Act
        val location = StoragePaths.getAudioFileLocation(context, bookDir, fileName)

        // Assert
        assertTrue("Location should be of type MediaStoreUri", location is AudioFileLocation.MediaStoreUri)
        assertEquals("The URI in the location object should match the created URI", createdUri, (location as AudioFileLocation.MediaStoreUri).uri)
    }

    @Test
    fun getAudioFileLocation_returnsNotFound_forNonExistentFile() {
        // Act
        val location = StoragePaths.getAudioFileLocation(
            context,
            "nonExistentBookForLocation",
            "nonExistentFileForLocation.mp3"
        )

        // Assert
        assertTrue("Location should be NotFound", location is AudioFileLocation.NotFound)
    }

}
