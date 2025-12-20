package com.dogmaticcentral.bookreader.data.media

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.io.File
import java.io.FileOutputStream


//
//Order 1-10: Setup validation tests
//Order 11-20: Error case tests
//Order 21-30: Success case tests
//Order 31-40: Query tests
//

@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@LargeTest
class StoragePathsInstrumentedTest {

    private lateinit var context: Context

    private lateinit var contentResolver: ContentResolver
    private lateinit var testBookDirectory: File
    private val testBookName = "TestBook"
    private val testFileName = "test_chapter.mp3"

    // Track created resources for cleanup
    private val createdFiles = mutableListOf<File>()
    private val createdUris = mutableListOf<Uri>()

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        contentResolver = context.contentResolver

        // UPDATED: Use the confirmed safe directory from test030
        // We add "TestBookData" so we don't mix with other cache files
        val safeRoot = File(context.externalCacheDir, "TestBookData")

        // Ensure this root exists
        if (!safeRoot.exists()) safeRoot.mkdirs()

        val relativePath = StoragePaths.getBookRelativePath(testBookName)
        testBookDirectory = File(safeRoot, relativePath)

        // Delete existing directory if it exists (cleanup from failed previous tests)
        if (testBookDirectory.exists()) {
            testBookDirectory.deleteRecursively()
        } else {
            // Ensure the parent "TestSandbox" directory exists
            testBookDirectory.parentFile?.mkdirs()
        }

        // Clear tracking lists
        createdFiles.clear()
        createdUris.clear()

    }

    @After
    fun cleanup() {
        // Delete all created URIs from MediaStore
        createdUris.forEach { uri ->
            try {
                contentResolver.delete(uri, null, null)
            } catch (e: Exception) {
                android.util.Log.w("TestCleanup", "Failed to delete URI: $uri", e)
            }
        }
        createdUris.clear()

        // Delete all created files
        createdFiles.forEach { file ->
            try {
                if (file.exists()) {
                    file.delete()
                }
            } catch (e: Exception) {
                android.util.Log.w("TestCleanup", "Failed to delete file: ${file.name}", e)
            }
        }
        createdFiles.clear()

        // Delete test directory structure
        try {
            if (testBookDirectory.exists()) {
                testBookDirectory.deleteRecursively()
            }
        } catch (e: Exception) {
            android.util.Log.w("TestCleanup", "Failed to delete directory", e)
        }
    }

    @Test
    fun test003_verifyExternalCacheDirIsAccessibleAndWritable() {
        println("=".repeat(80))
        println("TEST 030: EXTERNAL CACHE DIRECTORY DIAGNOSTICS")
        println("=".repeat(80))

        // 1. Availability Check
        // getExternalCacheDir() returns the path to /sdcard/Android/data/<package>/cache
        // This does not require READ/WRITE_EXTERNAL_STORAGE permissions.
        val cacheDir = context.externalCacheDir

        val msgNull = "Context.externalCacheDir returned null. External storage might be unmounted or emulated storage is missing."
        assertNotNull(msgNull, cacheDir)

        // Force non-null for subsequent usage
        val dir = cacheDir!!

        println("PATH REPORT:")
        println("  Absolute Path: ${dir.absolutePath}")
        println("  Canonical Path: ${dir.canonicalPath}")
        println("  Free Space: ${dir.freeSpace} bytes")
        println("  Usable Space: ${dir.usableSpace} bytes")

        // 2. Property Validation
        println("\nPROPERTY CHECKS:")

        var msg = "Cache directory must exist"
        val exists = dir.exists()
        val iconExists = if (exists) "✓" else "✗"
        println("  $iconExists Exists: $exists")
        assertTrue(msg, exists)

        msg = "Cache path must be a directory"
        val isDir = dir.isDirectory
        val iconDir = if (isDir) "✓" else "✗"
        println("  $iconDir Is Directory: $isDir")
        assertTrue(msg, isDir)

        msg = "Cache directory must be readable"
        val canRead = dir.canRead()
        val iconRead = if (canRead) "✓" else "✗"
        println("  $iconRead Can Read: $canRead")
        assertTrue(msg, canRead)

        msg = "Cache directory must be writable"
        val canWrite = dir.canWrite()
        val iconWrite = if (canWrite) "✓" else "✗"
        println("  $iconWrite Can Write: $canWrite")
        assertTrue(msg, canWrite)

        // 3. Path Logic Check
        // Ensure we are actually inside the app sandbox and not root storage
        println("\nSANDBOX VALIDATION:")
        val packageName = context.packageName
        println("  Current Package: $packageName")

        msg = "Path should contain package name (confirms we are in app-specific storage)"
        val isSandboxed = dir.absolutePath.contains(packageName)
        val iconSandbox = if (isSandboxed) "✓" else "✗"
        println("  $iconSandbox Path contains package name: $isSandboxed")
        assertTrue(msg, isSandboxed)

        // 4. Exhaustive I/O Test (CRUD)
        println("\nI/O CAPABILITY TEST (CRUD):")

        val probeFileName = "test030_probe_${System.currentTimeMillis()}.tmp"
        val probeFile = File(dir, probeFileName)
        val testContent = "StoragePaths Diagnostics: " + java.util.UUID.randomUUID().toString()

        try {
            // A. Create
            println("  [1/4] Attempting creation of ${probeFile.name}...")
            val created = probeFile.createNewFile()
            println("        Result: $created")

            msg = "File creation failed or file already exists"
            assertTrue(msg, created || probeFile.exists())
            createdFiles.add(probeFile) // Add to global cleanup just in case

            // B. Write
            println("  [2/4] Attempting write (${testContent.length} bytes)...")
            probeFile.writeText(testContent)
            println("        Result: Success (No exception thrown)")

            msg = "File length should be > 0 after write"
            assertTrue(msg, probeFile.length() > 0)

            // C. Read
            println("  [3/4] Attempting read verification...")
            val readContent = probeFile.readText()
            println("        Read: '$readContent'")

            msg = "Read content must match written content"
            assertEquals(msg, testContent, readContent)

            // D. Delete
            println("  [4/4] Attempting deletion...")
            val deleted = probeFile.delete()
            println("        Result: $deleted")

            msg = "File should be deleted successfully"
            assertTrue(msg, deleted)

            // Remove from global cleanup since we handled it
            createdFiles.remove(probeFile)

            println("  ✓ I/O Test Complete: The directory is fully functional.")

        } catch (e: Exception) {
            println("  ✗ I/O Test FAILED with Exception: ${e.javaClass.simpleName}")
            println("    Message: ${e.message}")
            e.printStackTrace()
            throw e
        }

        println("=".repeat(80))
    }


    @Test
    fun test010_directoryCanBeCreated() {
        val directoryCreated = testBookDirectory.mkdirs()

        val msg = "Directory should be created successfully"
        assertTrue(msg, directoryCreated || testBookDirectory.exists())
        println("SUCCESS: $msg")
    }

    @Test
    fun test020_createdDirectoryExists() {
        testBookDirectory.mkdirs()

        val msg = "Directory should exist after creation"
        assertTrue(msg, testBookDirectory.exists())
        println("SUCCESS: $msg")
    }

    @Test
    fun test030_pathIsActuallyADirectory() {
        testBookDirectory.mkdirs()

        val msg = "Path should be a directory"
        assertTrue(msg, testBookDirectory.isDirectory)
        println("SUCCESS: $msg")
    }

    @Test
    fun test040_directoryContentsCanBeListed() {
        testBookDirectory.mkdirs()

        val msg = "Should be able to list directory contents"
        val contents = testBookDirectory.listFiles()
        assertNotNull(msg, contents)
        println("SUCCESS: $msg")
    }

    @Test
    fun test050_directoryPathContainsExpectedRelativePath() {
        testBookDirectory.mkdirs()

        val expectedPath = StoragePaths.getBookRelativePath(testBookName).trimEnd('/')
        val actualPath = testBookDirectory.absolutePath
        val msg = """
            Directory path should contain expected relative path
            Expected to contain: '$expectedPath'
            Actual path: '$actualPath'
        """.trimIndent()

        assertTrue(msg, actualPath.contains(expectedPath))
        println("SUCCESS: Directory path contains expected relative path")
    }

    @Test
    fun test060_filesCanBeCreatedInDirectory() {
        testBookDirectory.mkdirs()

        val testMarkerFile = File(testBookDirectory, "test_marker.txt")
        val fileCreated = testMarkerFile.createNewFile()
        createdFiles.add(testMarkerFile)

        val msg = "Should be able to create files in the directory"
        assertTrue(msg, fileCreated)
        println("SUCCESS: $msg")
    }

    @Test
    fun test070_createdFileExists() {
        testBookDirectory.mkdirs()

        val testMarkerFile = File(testBookDirectory, "test_marker.txt")
        testMarkerFile.createNewFile()
        createdFiles.add(testMarkerFile)

        val msg = "Created file should exist"
        assertTrue(msg, testMarkerFile.exists())
        println("SUCCESS: $msg")
    }

    @Test
    fun test080_createdFileCanBeFoundInDirectoryListing() {
        testBookDirectory.mkdirs()

        val testMarkerFile = File(testBookDirectory, "test_marker.txt")
        testMarkerFile.createNewFile()
        createdFiles.add(testMarkerFile)

        val filesInDirectory = testBookDirectory.listFiles()

        var msg = "Directory listing should not be null"
        assertNotNull(msg, filesInDirectory)
        println("SUCCESS: $msg")

        msg = "Should be able to find created file in directory listing"
        val foundMarkerFile = filesInDirectory!!.any { it.name == "test_marker.txt" }
        assertTrue(msg, foundMarkerFile)
        println("SUCCESS: $msg")
    }

    @Test
    fun test090_directoryPermissions_areCorrect() {
        testBookDirectory.mkdirs()

        var msg = "Directory should be readable"
        assertTrue(msg, testBookDirectory.canRead())
        println("SUCCESS: $msg")

        msg = "Directory should be writable"
        assertTrue(msg, testBookDirectory.canWrite())
        println("SUCCESS: $msg")

        msg = "Directory should be executable (listable)"
        assertTrue(msg, testBookDirectory.canExecute())
        println("SUCCESS: $msg")
    }

    @Test
    fun test100_canDeleteCreatedDirectory_validatesCleanupWorks() {
        // Create directory and file
        testBookDirectory.mkdirs()
        val testFile = File(testBookDirectory, "cleanup_test.txt")
        testFile.createNewFile()

        var msg = "Directory should exist"
        assertTrue(msg, testBookDirectory.exists())
        println("SUCCESS: $msg")

        msg = "File should exist"
        assertTrue(msg, testFile.exists())
        println("SUCCESS: $msg")

        // Manually cleanup
        testFile.delete()
        testBookDirectory.deleteRecursively()

        msg = "File should be deleted"
        assertTrue(msg, !testFile.exists())
        println("SUCCESS: $msg")

        msg = "Directory should be deleted"
        assertTrue(msg, !testBookDirectory.exists())
        println("SUCCESS: $msg")
    }

    // Error case tests

    @Test
    fun test110_createAudioFileUri_nonexistentDirectory_returnsNull() {
        // Ensure directory does not exist
        if (testBookDirectory.exists()) {
            testBookDirectory.deleteRecursively()
        }

        val result = StoragePaths.createAudioFileUri(
            contentResolver,
            testBookName,
            testFileName
        )

        val msg = "Expected null when directory does not exist"
        assertNull(msg, result)
        println("SUCCESS: $msg")
    }

    @Test
    fun test120_createAudioFileUri_nonexistentMp3File_returnsNull() {
        // Create directory but no file
        testBookDirectory.mkdirs()

        val testFile = File(testBookDirectory, testFileName)
        // Ensure file does not exist
        if (testFile.exists()) {
            testFile.delete()
        }

        val result = StoragePaths.createAudioFileUri(
            contentResolver,
            testBookName,
            testFileName
        )

        val msg = "Expected null when MP3 file does not exist"
        assertNull(msg, result)
        println("SUCCESS: $msg")
    }

    @Test
    fun test130_createAudioFileUri_emptyMp3File_returnsNull() {
        // Create directory
        testBookDirectory.mkdirs()

        // Create empty file
        val testFile = File(testBookDirectory, testFileName)
        testFile.createNewFile()
        createdFiles.add(testFile)

        var msg = "Test file should be empty"
        assertEquals(msg, 0L, testFile.length())
        println("SUCCESS: $msg")

        val result = StoragePaths.createAudioFileUri(
            contentResolver,
            testBookName,
            testFileName
        )

        msg = "Expected null when MP3 file is empty"
        assertNull(msg, result)
        println("SUCCESS: $msg")
    }

    @Test
    fun test140_createAudioFileUri_nonMp3File_returnsNull() {
        // Create directory
        testBookDirectory.mkdirs()

        // Create non-empty file with invalid MP3 header
        val testFile = File(testBookDirectory, testFileName)
        FileOutputStream(testFile).use { fos ->
            val invalidData = "This is just test, not audio data".toByteArray()
            fos.write(invalidData)
        }
        createdFiles.add(testFile)

        var msg = "Test file should be non-empty"
        assertTrue(msg, testFile.length() > 0)
        println("SUCCESS: $msg")

        val result = StoragePaths.createAudioFileUri(
            contentResolver,
            testBookName,
            testFileName
        )

        msg = "Expected null when file is not a valid MP3"
        assertNull(msg, result)
        println("SUCCESS: $msg")
    }

    // Success case tests

    @Test
    fun test150_createAudioFileUri_validMp3WithId3Header_returnsUri() {
        // Create directory
        testBookDirectory.mkdirs()

        // Create valid MP3 file with ID3 header
        val testFile = File(testBookDirectory, testFileName)
        FileOutputStream(testFile).use { fos ->
            fos.write(byteArrayOf(0x49.toByte(), 0x44.toByte(), 0x33.toByte()))
            fos.write(ByteArray(100))
        }
        createdFiles.add(testFile)

        var msg = "Test file should exist"
        assertTrue(msg, testFile.exists())
        println("SUCCESS: $msg")

        msg = "Test file should be non-empty"
        assertTrue(msg, testFile.length() > 0)
        println("SUCCESS: $msg")

        val result = StoragePaths.createAudioFileUri(
            contentResolver,
            testBookName,
            testFileName
        )

        msg = "Expected valid URI for valid MP3 file"
        assertNotNull(msg, result)
        println("SUCCESS: $msg")

        createdUris.add(result!!)
    }

    @Test
    fun test160_createAudioFileUri_validMp3WithMpegHeader_returnsUri() {
        // Create directory
        testBookDirectory.mkdirs()

        // Create valid MP3 file with MPEG frame sync header
        val testFile = File(testBookDirectory, testFileName)
        FileOutputStream(testFile).use { fos ->
            fos.write(byteArrayOf(0xFF.toByte(), 0xE0.toByte(), 0x00.toByte()))
            fos.write(ByteArray(100))
        }
        createdFiles.add(testFile)

        val result = StoragePaths.createAudioFileUri(
            contentResolver,
            testBookName,
            testFileName
        )

        val msg = "Expected valid URI for valid MP3 file with MPEG header"
        assertNotNull(msg, result)
        println("SUCCESS: $msg")

        createdUris.add(result!!)
    }

    @Test
    fun test170_createAudioFileUri_validMp3_fileCanBeCommitted() {
        // Create directory
        testBookDirectory.mkdirs()

        // Create valid MP3 file
        val testFile = File(testBookDirectory, testFileName)
        val testContent = "Test audio content"
        FileOutputStream(testFile).use { fos ->
            fos.write(byteArrayOf(0x49.toByte(), 0x44.toByte(), 0x33.toByte()))
            fos.write(testContent.toByteArray())
        }
        createdFiles.add(testFile)

        val uri = StoragePaths.createAudioFileUri(
            contentResolver,
            testBookName,
            testFileName
        )

        var msg = "URI should not be null"
        assertNotNull(msg, uri)
        println("SUCCESS: $msg")

        createdUris.add(uri!!)

        // Verify we can write to the URI
        contentResolver.openOutputStream(uri)?.use { outputStream ->
            outputStream.write(testContent.toByteArray())
        }

        msg = "Should be able to open output stream"
        println("SUCCESS: $msg")

        // Verify we can read back from the URI
        val readContent = contentResolver.openInputStream(uri)?.use { inputStream ->
            inputStream.readBytes().toString(Charsets.UTF_8)
        }

        msg = "Should be able to read from committed file"
        assertNotNull(msg, readContent)
        println("SUCCESS: $msg")

        msg = "Content should be readable from URI"
        assertTrue(msg, readContent!!.contains(testContent))
        println("SUCCESS: $msg")
    }

    @Test
    fun test180_createAudioFileUri_returnsUriWithCorrectMetadata() {
        // Create directory
        testBookDirectory.mkdirs()

        // Create valid MP3 file
        val testFile = File(testBookDirectory, testFileName)
        FileOutputStream(testFile).use { fos ->
            fos.write(byteArrayOf(0x49.toByte(), 0x44.toByte(), 0x33.toByte()))
            fos.write(ByteArray(100))
        }
        createdFiles.add(testFile)

        val uri = StoragePaths.createAudioFileUri(
            contentResolver,
            testBookName,
            testFileName,
            mimeType = "audio/mpeg"
        )

        var msg = "URI should not be null"
        assertNotNull(msg, uri)
        println("SUCCESS: $msg")

        createdUris.add(uri!!)

        // Query the metadata
        val projection = arrayOf(
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.RELATIVE_PATH
        )

        val cursor = contentResolver.query(uri, projection, null, null, null)

        msg = "Cursor should not be null"
        assertNotNull(msg, cursor)
        println("SUCCESS: $msg")

        cursor!!.use {
            msg = "Cursor should have at least one row"
            assertTrue(msg, it.moveToFirst())
            println("SUCCESS: $msg")

            val displayNameIndex = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val mimeTypeIndex = it.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
            val relativePathIndex = it.getColumnIndexOrThrow(MediaStore.Audio.Media.RELATIVE_PATH)

            val displayName = it.getString(displayNameIndex)
            val mimeType = it.getString(mimeTypeIndex)
            val relativePath = it.getString(relativePathIndex)

            msg = "Display name should match"
            assertEquals(msg, testFileName, displayName)
            println("SUCCESS: $msg")

            msg = "MIME type should match"
            assertEquals(msg, "audio/mpeg", mimeType)
            println("SUCCESS: $msg")

            msg = "Relative path should match"
            assertEquals(msg, StoragePaths.getBookRelativePath(testBookName), relativePath)
            println("SUCCESS: $msg")
        }
    }

    @Test
    fun test190_createAudioFileUri_existingDirectory_doesNotReturnNull() {
        // Create directory
        val directoryCreated = testBookDirectory.mkdirs()

        var msg = "Directory should be created successfully"
        assertTrue(msg, directoryCreated || testBookDirectory.exists())
        println("SUCCESS: $msg")

        msg = "Directory must exist before test"
        assertTrue(msg, testBookDirectory.exists())
        println("SUCCESS: $msg")

        // Create valid MP3 file in existing directory
        val testFile = File(testBookDirectory, testFileName)
        FileOutputStream(testFile).use { fos ->
            fos.write(byteArrayOf(0x49.toByte(), 0x44.toByte(), 0x33.toByte()))
            fos.write(ByteArray(100))
        }
        createdFiles.add(testFile)

        msg = "Test file should exist"
        assertTrue(msg, testFile.exists())
        println("SUCCESS: $msg")

        msg = "Test file should be in the correct directory"
        assertTrue(msg, testFile.parentFile?.absolutePath == testBookDirectory.absolutePath)
        println("SUCCESS: $msg")

        // Call the method under test
        val result = StoragePaths.createAudioFileUri(
            contentResolver,
            testBookName,
            testFileName
        )

        msg = "Should return URI when directory exists and file is valid"
        assertNotNull(msg, result)
        println("SUCCESS: $msg")

        if (result != null) {
            createdUris.add(result)
        }
    }

    // Query tests

    @Test
    fun test200_queryAudioFileUri_existingFile_returnsUri() {
        // Setup: Create file and insert into MediaStore
        testBookDirectory.mkdirs()

        val testFile = File(testBookDirectory, testFileName)
        FileOutputStream(testFile).use { fos ->
            fos.write(byteArrayOf(0x49.toByte(), 0x44.toByte(), 0x33.toByte()))
            fos.write(ByteArray(100))
        }
        createdFiles.add(testFile)

        val createdUri = StoragePaths.createAudioFileUri(
            contentResolver,
            testBookName,
            testFileName
        )

        var msg = "Created URI should not be null"
        assertNotNull(msg, createdUri)
        println("SUCCESS: $msg")

        createdUris.add(createdUri!!)

        // Test: Query for the file
        val queriedUri = StoragePaths.queryAudioFileUri(
            contentResolver,
            testBookName,
            testFileName
        )

        msg = "Query should find the existing file"
        assertNotNull(msg, queriedUri)
        println("SUCCESS: $msg")

        msg = "Queried URI should match created URI"
        assertEquals(msg, createdUri.toString(), queriedUri.toString())
        println("SUCCESS: $msg")
    }

    @Test
    fun test210_queryAudioFileUri_nonexistentFile_returnsNull() {
        val result = StoragePaths.queryAudioFileUri(
            contentResolver,
            testBookName,
            "nonexistent_file.mp3"
        )

        val msg = "Query should return null for nonexistent file"
        assertNull(msg, result)
        println("SUCCESS: $msg")
    }

    @Test
    fun test220_getAudioFileLocation_existingFile_returnsMediaStoreUri() {
        // Setup
        testBookDirectory.mkdirs()

        val testFile = File(testBookDirectory, testFileName)
        FileOutputStream(testFile).use { fos ->
            fos.write(byteArrayOf(0x49.toByte(), 0x44.toByte(), 0x33.toByte()))
            fos.write(ByteArray(100))
        }
        createdFiles.add(testFile)

        val uri = StoragePaths.createAudioFileUri(
            contentResolver,
            testBookName,
            testFileName
        )

        var msg = "Created URI should not be null"
        assertNotNull(msg, uri)
        println("SUCCESS: $msg")

        createdUris.add(uri!!)

        // Test
        val location = StoragePaths.getAudioFileLocation(
            context,
            testBookName,
            testFileName
        )

        msg = "Location should be MediaStoreUri"
        assertTrue(msg, location is AudioFileLocation.MediaStoreUri)
        println("SUCCESS: $msg")
    }

    @Test
    fun test230_getAudioFileLocation_nonexistentFile_returnsNotFound() {
        val location = StoragePaths.getAudioFileLocation(
            context,
            testBookName,
            "nonexistent.mp3"
        )

        val msg = "Location should be NotFound"
        assertTrue(msg, location is AudioFileLocation.NotFound)
        println("SUCCESS: $msg")
    }
}
