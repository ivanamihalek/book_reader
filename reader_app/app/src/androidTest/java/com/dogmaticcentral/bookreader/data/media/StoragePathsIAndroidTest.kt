package com.dogmaticcentral.bookreader.data.media

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.yourpackage.data.media.AudioFileLocation
import com.yourpackage.data.media.StoragePaths
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StoragePathsAndroidTest {

    private lateinit var context: Context
    private val testBookDir = "testBook${System.currentTimeMillis()}"
    private val testFileName = "test_chapter_${System.currentTimeMillis()}.mp3"
    private val createdUris = mutableListOf<Uri>()

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }

    @After
    fun cleanup() {
        // Clean up created test files
        createdUris.forEach { uri ->
            try {
                context.contentResolver.delete(uri, null, null)
            } catch (e: Exception) {
                // Ignore cleanup errors
                e.printStackTrace()
            }
        }
        createdUris.clear()
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.Q)
    fun `can create file in MediaStore with RELATIVE_PATH`() {
        // Create file
        val uri = StoragePaths.createAudioFileUri(
            context.contentResolver,
            testBookDir,
            testFileName
        )

        assertNotNull("Should create file URI", uri)
        uri?.let { createdUris.add(it) }

        // Mark as not pending
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.IS_PENDING, 0)
        }
        context.contentResolver.update(uri!!, values, null, null)

        // Verify it was created
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME
        )

        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            assertTrue("Cursor should have data", cursor.moveToFirst())
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val name = cursor.getString(nameColumn)
            assertEquals("Filename should match", testFileName, name)
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.Q)
    fun `can query created file by name and path`() {
        // Create file
        val createdUri = StoragePaths.createAudioFileUri(
            context.contentResolver,
            testBookDir,
            testFileName
        )
        assertNotNull(createdUri)
        createdUri?.let { createdUris.add(it) }

        // Mark as complete
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.IS_PENDING, 0)
        }
        context.contentResolver.update(createdUri!!, values, null, null)

        // Query for it
        val queriedUri = StoragePaths.queryAudioFileUri(
            context.contentResolver,
            testBookDir,
            testFileName
        )

        assertNotNull("Should find created file", queriedUri)
        assertEquals("URIs should match", createdUri.toString(), queriedUri.toString())
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.Q)
    fun `file has correct RELATIVE_PATH in MediaStore`() {
        // Create file
        val uri = StoragePaths.createAudioFileUri(
            context.contentResolver,
            testBookDir,
            testFileName
        )
        assertNotNull(uri)
        uri?.let { createdUris.add(it) }

        // Mark as complete
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.IS_PENDING, 0)
        }
        context.contentResolver.update(uri!!, values, null, null)

        // Query relative path
        val projection = arrayOf(MediaStore.Audio.Media.RELATIVE_PATH)
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            assertTrue(cursor.moveToFirst())
            val pathColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.RELATIVE_PATH)
            val relativePath = cursor.getString(pathColumn)

            val expectedPath = StoragePaths.getBookRelativePath(testBookDir)
            assertEquals("Relative path should match", expectedPath, relativePath)
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.Q)
    fun `query returns null for non-existent file`() {
        val uri = StoragePaths.queryAudioFileUri(
            context.contentResolver,
            "nonExistentBook",
            "nonExistentFile.mp3"
        )

        assertNull("Should return null for non-existent file", uri)
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.Q)
    fun `can create multiple files in same book directory`() {
        val file1 = "chapter1_${System.currentTimeMillis()}.mp3"
        val file2 = "chapter2_${System.currentTimeMillis()}.mp3"

        // Create two files
        val uri1 = StoragePaths.createAudioFileUri(context.contentResolver, testBookDir, file1)
        val uri2 = StoragePaths.createAudioFileUri(context.contentResolver, testBookDir, file2)

        assertNotNull(uri1)
        assertNotNull(uri2)
        uri1?.let { createdUris.add(it) }
        uri2?.let { createdUris.add(it) }

        // Mark both as complete
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.IS_PENDING, 0)
        }
        context.contentResolver.update(uri1!!, values, null, null)
        context.contentResolver.update(uri2!!, values, null, null)

        // Query both
        val queriedUri1 = StoragePaths.queryAudioFileUri(context.contentResolver, testBookDir, file1)
        val queriedUri2 = StoragePaths.queryAudioFileUri(context.contentResolver, testBookDir, file2)

        assertNotNull(queriedUri1)
        assertNotNull(queriedUri2)
        assertNotEquals("URIs should be different", queriedUri1.toString(), queriedUri2.toString())
    }

    @Test
    fun `getAudioFileLocation returns appropriate type for Android version`() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Should return NotFound or MediaStoreUri
            val location = StoragePaths.getAudioFileLocation(
                context,
                testBookDir,
                testFileName
            )
            assertTrue(
                "Should be NotFound or MediaStoreUri on Android 10+",
                location is AudioFileLocation.NotFound ||
                        location is AudioFileLocation.MediaStoreUri
            )
        } else {
            // Should return NotFound or LegacyPath
            val location = StoragePaths.getAudioFileLocation(
                context,
                testBookDir,
                testFileName
            )
            assertTrue(
                "Should be NotFound or LegacyPath on Android < 10",
                location is AudioFileLocation.NotFound ||
                        location is AudioFileLocation.LegacyPath
            )
        }
    }
}