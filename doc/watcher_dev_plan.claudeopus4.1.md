PLAN 2
Great project! I'll help you implement a file watcher capability for your book reader app. Let me provide you with a structured approach.

## 1. Suggested File Structure

Based on your current architecture, I recommend creating a new package for the watcher functionality:

```
├── data
│   ├── watcher
│   │   ├── BookFileWatcher.kt         // Main watcher service
│   │   ├── BookProcessor.kt           // Handles processing logic
│   │   └── FileObserverService.kt     // Android FileObserver wrapper
│   ├── database
│   ├── media
│   └── Repository.kt
```

The watcher should run as a **Foreground Service** to ensure it continues monitoring even when the app is in the background.

## 2. Development Steps

Here's a step-by-step implementation plan with individual testing points:

### Step 1: Create Basic FileObserver
**File: `data/watcher/BookFileWatcher.kt`**

```kotlin
package com.yourapp.data.watcher

import android.os.FileObserver
import android.util.Log
import java.io.File

class BookFileWatcher(
    private val watchPath: String,
    private val onNewFile: (File) -> Unit
) {
    private var fileObserver: FileObserver? = null
    
    fun startWatching() {
        fileObserver = object : FileObserver(watchPath, CREATE or MOVED_TO) {
            override fun onEvent(event: Int, path: String?) {
                path?.let {
                    when (event) {
                        CREATE, MOVED_TO -> {
                            Log.d("BookFileWatcher", "New file detected: $it")
                            val file = File(watchPath, it)
                            if (file.extension == "zip") {
                                onNewFile(file)
                            }
                        }
                    }
                }
            }
        }
        fileObserver?.startWatching()
    }
    
    fun stopWatching() {
        fileObserver?.stopWatching()
        fileObserver = null
    }
}
```

**Test Step 1:** Create a simple test activity that starts the watcher and logs when files are added to the directory.

### Step 2: Create Book Processing Logic
**File: `data/watcher/BookProcessor.kt`**

```kotlin
package com.yourapp.data.watcher

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.io.File
import java.util.zip.ZipFile

class BookProcessor(
    private val repository: Repository,
    private val audioPath: String
) {
    
    suspend fun processBook(zipFile: File): Result<BookInfo> = withContext(Dispatchers.IO) {
        try {
            // Step 1: Create temp directory
            val tempDir = File(zipFile.parent, "temp_${System.currentTimeMillis()}")
            tempDir.mkdirs()
            
            // Step 2: Unzip
            unzipFile(zipFile, tempDir)
            
            // Step 3: Parse NCC.HTML
            val bookInfo = parseNCCFile(tempDir)
            
            // Step 4: Create final directory
            val finalDir = createBookDirectory(bookInfo)
            
            // Step 5: Move and rename MP3 files
            moveAndRenameChapters(tempDir, finalDir, bookInfo)
            
            // Step 6: Add to database
            saveToDatabase(bookInfo)
            
            // Step 7: Cleanup
            tempDir.deleteRecursively()
            zipFile.delete()
            
            Result.success(bookInfo)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    private fun unzipFile(zipFile: File, destDir: File) {
        ZipFile(zipFile).use { zip ->
            zip.entries().asSequence().forEach { entry ->
                val outputFile = File(destDir, entry.name)
                if (entry.isDirectory) {
                    outputFile.mkdirs()
                } else {
                    outputFile.parentFile?.mkdirs()
                    zip.getInputStream(entry).use { input ->
                        outputFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }
        }
    }
    
    private fun parseNCCFile(tempDir: File): BookInfo {
        val nccFile = tempDir.walkTopDown().find { 
            it.name.equals("NCC.HTML", ignoreCase = true) 
        } ?: throw Exception("NCC.HTML not found")
        
        val doc = Jsoup.parse(nccFile, "UTF-8")
        val title = doc.select("title").text()
        val author = doc.select("meta[name=dc:creator]").attr("content")
        
        // Parse chapters from NCC.HTML structure
        val chapters = mutableListOf<ChapterInfo>()
        // ... parsing logic
        
        return BookInfo(title, author, chapters)
    }
    
    private fun createBookDirectory(bookInfo: BookInfo): File {
        val dirName = "${bookInfo.author} - ${bookInfo.title}".replace(Regex("[^a-zA-Z0-9.-]"), "_")
        val bookDir = File(audioPath, dirName)
        bookDir.mkdirs()
        return bookDir
    }
    
    private suspend fun saveToDatabase(bookInfo: BookInfo) {
        // Use your repository to save
        repository.insertBook(bookInfo.toEntity())
    }
}

data class BookInfo(
    val title: String,
    val author: String,
    val chapters: List<ChapterInfo>
)

data class ChapterInfo(
    val title: String,
    val fileName: String,
    val duration: Long? = null
)
```

**Test Step 2:** Test the processor with a sample ZIP file manually to ensure parsing and file operations work correctly.

### Step 3: Create Foreground Service
**File: `data/watcher/FileObserverService.kt`**

```kotlin
package com.yourapp.data.watcher

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import javax.inject.Inject

class FileObserverService : Service() {
    
    @Inject lateinit var repository: Repository
    
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var bookFileWatcher: BookFileWatcher
    private lateinit var bookProcessor: BookProcessor
    
    override fun onCreate() {
        super.onCreate()
        
        val audioPath = "${android.os.Environment.getExternalStorageDirectory()}/" +
                       "${android.os.Environment.DIRECTORY_AUDIOBOOKS}/BookReader/audio/"
        
        bookProcessor = BookProcessor(repository, audioPath)
        
        bookFileWatcher = BookFileWatcher(audioPath) { file ->
            serviceScope.launch {
                val result = bookProcessor.processBook(file)
                result.fold(
                    onSuccess = { showNotification("Book added: ${it.title}") },
                    onFailure = { showNotification("Failed to process book: ${it.message}") }
                )
            }
        }
        
        startForeground(NOTIFICATION_ID, createNotification())
        bookFileWatcher.startWatching()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }
    
    override fun onDestroy() {
        super.onDestroy()
        bookFileWatcher.stopWatching()
        serviceScope.cancel()
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    private fun createNotification(): Notification {
        createNotificationChannel()
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Book Reader")
            .setContentText("Watching for new books...")
            .setSmallIcon(android.R.drawable.ic_menu_upload)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "File Watcher Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
    
    companion object {
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "file_watcher_channel"
    }
}
```

**Test Step 3:** Start the service and verify it runs in the foreground with a notification.

### Step 4: Integration with MainActivity
**Update: `MainActivity.kt`**

```kotlin
class MainActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Start the file watcher service
        startFileWatcherService()
        
        // ... rest of your code
    }
    
    private fun startFileWatcherService() {
        val intent = Intent(this, FileObserverService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}
```

### Step 5: Add Permissions
**Update: `AndroidManifest.xml`**

```xml
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />

<application>
    <!-- ... -->
    
    <service 
        android:name=".data.watcher.FileObserverService"
        android:foregroundServiceType="dataSync"
        android:exported="false" />
</application>
```

## Testing Strategy

1. **Unit Test BookProcessor**: Test parsing logic with sample NCC.HTML files
2. **Integration Test**: Test the complete flow with a sample ZIP file
3. **Service Test**: Verify the service stays alive and responds to file events
4. **UI Test**: Ensure the database updates are reflected in your UI screens

## Additional Recommendations

1. **Add a toggle in AdminScreen** to enable/disable the watcher
2. **Implement progress notifications** for large book processing
3. **Add error recovery** for partially processed books
4. **Consider using WorkManager** for one-time processing tasks if continuous watching isn't needed

This architecture keeps the watcher separate from your UI, ensures it runs continuously, and integrates smoothly with your existing Room database and repository pattern.
