PLAN 1
Below is a pragmatic way to add a “watcher” that detects new audiobook files as they appear in your target directory, processes them, and inserts results into Room—without requiring the user to restart the app.

Part 1) Where to put the watcher (and how to structure it)
- Use a dedicated component for “ingestion” and a background job to do the heavy processing.
- Recommended pieces:
  - DirectoryWatcher (listens for new files): data/watch/DirectoryWatcher.kt
  - BookIngestWorker (unzips, parses NCC.HTML, renames chapters, inserts in Room): data/work/BookIngestWorker.kt
  - BookIngestRepository (transactional DB + filesystem ops API): data/repository/BookIngestRepository.kt (you can merge with Repository.kt if preferred)
  - App-level initializer to start/stop the watcher when the app starts/stops (no restart needed):
    - If you use the AndroidX Startup library: app/BookReaderInitializer.kt
    - Or, minimal: initialize in Application subclass (e.g., App.kt), tied to ProcessLifecycleOwner

Why this structure
- DirectoryWatcher should be lightweight and short-lived; it only detects new content and enqueues work.
- Heavy lifting must be robust and survive process death; WorkManager is the recommended solution on Android for deferrable background tasks.
- If ingestion can be lengthy or you want progress/notification, the worker can be a ForegroundService-backed WorkManager worker.

How to actually “watch”
- Avoid polling if possible. Use one of:
  - ContentObserver on MediaStore.Audio.Media with selection filtering for your relative path (best with scoped storage).
  - FileObserver on the concrete directory path (simple, but only works while your process is alive; pair with WorkManager to mitigate).
- If your target path is in shared storage under “Audiobooks”, prefer MediaStore + ContentObserver because direct file path APIs and Environment.getExternalStorageDirectory are deprecated.

Notes on storage path
- Your current code:
  - Environment.getExternalStorageDirectory and concatenation is deprecated and risky on API 29+.
- Prefer:
  - Use MediaStore with RELATIVE_PATH = Environment.DIRECTORY_AUDIOBOOKS + "/BookReader/audio"
  - Or, if you must keep a direct folder, use Context.getExternalFilesDir(Environment.DIRECTORY_MUSIC or DIRECTORY_AUDIOBOOKS) for app-private media. For shared media that other apps can see, use MediaStore.
- Since you want auto-detection when users drop new files, shared storage + MediaStore is the friendliest.

Part 2) Suggested development steps (each step testable in isolation)
1) Normalize the storage strategy (testable utility)
   - Create a StoragePaths utility that:
     - Exposes the RELATIVE_PATH ("Audiobooks/BookReader/audio/") for MediaStore.
     - Provides functions to resolve to DocumentFile/Uri or absolute path when needed for legacy APIs.
   - Tests:
     - Unit-test RELATIVE_PATH constant and simple path join logic.
     - Instrumentation test to verify you can query/create a file via MediaStore in that relative path.

2) Implement ZIP handling and temporary workspace (no DB, no watcher yet)
   - Functions: unzipToTemp(zipUri: Uri): File; cleanupTemp(dir: File)
   - Use context.cacheDir or context.externalCacheDir for the temp folder.
   - Tests:
     - Instrumentation test: feed a known small zip (in test assets), unzip, assert expected files exist.

3) Implement NCC.HTML parsing (pure unit, safe and quick)
   - Create a small parser: parseNcc(htmlFile: File): BookMeta(title, author, maybe chapter list if present).
   - Use JSoup for HTML parsing.
   - Tests:
     - Unit tests with representative NCC.HTML fixtures, including edge cases (missing author, different casing, extra tags).

4) Implement chapter renaming and target directory creation (filesystem-only)
   - Create the canonical directory name e.g., "AuthorName - BookTitle".
   - Implement safe-sanitization for filesystem names.
   - Implement a function to enumerate chapter mp3s, map them to new names (e.g., 001 - Chapter Title.mp3), and move/copy them to the target dir (via MediaStore for shared storage).
   - Tests:
     - Instrumentation tests against a temporary sandbox, validate renamed files and counts.
     - If using MediaStore, validate insertion and that files are playable.

5) Room layer updates (unit + instrumentation)
   - Add DAO and entities if not present: BookEntity, ChapterEntity.
   - Add repository methods: insertBookWithChapters(meta, files), upsert semantics, pre-existence checks (by title/author) to avoid duplicates.
   - Tests:
     - Room unit tests using an in-memory DB.
     - Verify transactional behavior and idempotency (re-processing the same zip doesn’t duplicate).

6) Implement the BookIngestWorker (isolated WorkManager testing)
   - A CoroutineWorker that:
     - Accepts the Uri/path of the new zip (or folder).
     - Calls unzip -> parse -> create target dir -> rename/copy -> insert into Room.
     - Emits foreground notification if long-running (required on Android 12+ for long work).
     - Does cleanup in a finally block.
   - Tests:
     - Use WorkManagerTestInitHelper to enqueue a OneTimeWorkRequest with a test zip Uri.
     - Verify DB state and files created.

7) Implement the watcher
   Option A: MediaStore ContentObserver (recommended for shared Audiobooks directory)
   - Register a ContentObserver on MediaStore.Audio.Media.EXTERNAL_CONTENT_URI.
   - Filter for RELATIVE_PATH LIKE "Audiobooks/BookReader/audio/%" and mimeType = "application/zip" or "audio/mpeg" depending on your ingest trigger.
   - On change, query for “recent” items in that relative path and enqueue BookIngestWorker for new zips or new folders.
   Option B: FileObserver on a concrete directory
   - Watch for CREATE and MOVED_TO events.
   - Debounce events and verify the file is complete (size stable for N ms) before enqueueing the worker.
   Where to put it:
   - data/watch/DirectoryWatcher.kt implements start() and stop(), takes a callback (Uri/File) -> Unit.
   - Initialize in Application onCreate or via AndroidX Startup so it starts without user action.
   Tests:
   - Manual: drop a test zip into the directory and verify processing starts.
   - Automated: Instrumentation test can simulate via MediaStore insert.

8) App initialization (no restart needed)
   - If using AndroidX Startup: implement Initializer<DirectoryWatcher> that starts the watcher and holds a weak reference to Application.
   - Or, in Application subclass, observe ProcessLifecycleOwner to start watcher on onStart and stop on onStop.
   - Ensure it re-registers after process recreation.

9) Permissions and user prompts
   - Android 13+ (API 33+): request READ_MEDIA_AUDIO if you read audio; if you’re reading a zip not indexed as audio, you may need the photo/video counterparts or SAF to pick files. Prefer SAF or MediaStore insert for user-supplied zips.
   - Pre-33: READ_EXTERNAL_STORAGE as needed.
   - POST_NOTIFICATIONS for foreground WorkManager notifications on Android 13+.
   - Tests:
     - Run-time permission flow validation on physical/emulator devices.

10) Error handling, idempotency, and cleanup
   - Add safeguards:
     - Check if a book with same author+title already exists; if yes, skip or update.
     - Ensure temporary directories are deleted even on failure.
     - Implement a simple “ingestion lock” per zip (e.g., keep a small state table with file hash/path and status).
   - Tests:
     - Simulate duplicate zip, partial failure, corrupted zip.

11) Admin/Debug screen hooks (optional but very helpful)
   - In AdminScreen, add:
     - “Scan Now” button: manually enqueue a scan job that looks for unprocessed zips in the directory.
     - “Last 20 logs” view from a simple local log table or Logcat viewer.
   - Tests:
     - Manual UX check and QA.

12) Performance and UX polish
   - Debounce watcher triggers for a file until it stabilizes in size.
   - Show a notification “Processing new book…” with completion/failure result.
   - Consider a small backoff retry policy for the worker.

Minimal code outlines

DirectoryWatcher (FileObserver version)
- class DirectoryWatcher(private val dir: File, private val onNewFile: (File) -> Unit)
- start(): creates FileObserver(dir.path, CREATE or MOVED_TO), onEvent -> if file endsWith(".zip") schedule after debounce check -> onNewFile(file)
- stop(): observer.stopWatching()

MediaStore observer snippet
- Register ContentObserver on MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
- On change, query:
  - selection: MediaStore.MediaColumns.RELATIVE_PATH LIKE ? AND (mimeType = 'application/zip' OR mimeType = 'audio/mpeg')
  - selectionArgs: ["Audiobooks/BookReader/audio/%"]
- For each new zip not yet processed -> enqueue worker with zip Uri

WorkManager enqueue
- val request = OneTimeWorkRequestBuilder<BookIngestWorker>()
  .setInputData(workDataOf("zipUri" to uri.toString()))
  .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
  .build()
- WorkManager.getInstance(context).enqueue(request)

BookIngestWorker (high level)
- doWork():
  - val uri = inputData.getString("zipUri")?.toUri()
  - val tempDir = unzipToTemp(uri)
  - val meta = parseNcc(findNcc(tempDir))
  - val target = ensureTargetDir(meta)
  - val chapters = renameAndCopyChapters(tempDir, target)
  - repository.insertBookWithChapters(meta, chapters)
  - cleanupTemp(tempDir)
  - return Result.success()

Final tips
- Prefer MediaStore and scoped storage for future-proofing.
- Keep watcher lightweight; put all heavy work in WorkManager.
- Make ingestion idempotent and resilient to partial failures.
- Add a manual “Scan Now” path so QA and users can recover if an event was missed.

This plan lets you add the watcher in a standard place (data/watch + app initializer) and proceed step-by-step, testing each unit before integrating.
