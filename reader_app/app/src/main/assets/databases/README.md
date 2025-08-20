# Exporting DB from device to desktop

Room keeps its data in a normal SQLite file, it just lives in a place that is not world-readable.  
All you have to do is copy that file (and, when WAL is enabled, the accompanying -wal/-shm files) from

/data/data/your.package.name/databases/yourDbName.db

to your desktop.  
Below are the three most common ways to do it on a debug build (no root required).

──────────────────────────────────
1. Android Studio Device Explorer (easiest)
   ──────────────────────────────────
1. Make sure the app is NOT running (Run ▸ Stop), otherwise the WAL file may still be open.
2. Android Studio ▸ View ▸ Tool Windows ▸ Device Explorer (called “Device File Explorer” in older versions).
3. Pick the connected phone/emulator.
4. Navigate to: data ▸ data ▸ your.package.name ▸ databases.
5. Select  
   yourDbName.db  
   yourDbName.db-wal (if it exists)  
   yourDbName.db-shm (if it exists)  
   Right-click ▸ Save As … and choose a folder on your desktop.  
   The .db file is already in SQLite format—open it with any desktop SQLite client.

────────────────────────
2. Pure adb / command line
   ────────────────────────
   Stop the app first so nothing is writing to the DB:

   adb shell am force-stop your.package.name

Then pull the files using run-as (works on any debuggable build):

# Copy .db to stdout and redirect to a local file
adb exec-out "run-as your.package.name cat databases/yourDbName.db" > ~/Desktop/yourDbName.db

Do the same for the -wal and -shm files if they exist:

adb exec-out "run-as your.package.name cat databases/yourDbName.db-wal" > ~/Desktop/yourDbName.db-wal
adb exec-out "run-as your.package.name cat databases/yourDbName.db-shm" > ~/Desktop/yourDbName.db-shm

(Instead of exec-out + cat you can first copy to /sdcard and then adb pull from there; use whichever you prefer.)

Optional SQL text dump (if you want plain SQL instead of a binary .db):

adb shell "run-as your.package.name sqlite3 databases/yourDbName.db .dump" > ~/Desktop/yourDb.sql

Import on desktop with: sqlite3 new.db < yourDb.sql

──────────────────────────────
3. Programmatic export from inside the app
   ──────────────────────────────
   If you need an end-user feature (so adb isn’t available), copy the file to an external location your app can access:

```
val src = getDatabasePath("yourDbName.db")
val dst = File(
    getExternalFilesDir(null),          // or use SAF / MediaStore for API >= 29
    "exported-yourDbName.db"
)

FileInputStream(src).use { input ->
    FileOutputStream(dst).use { output ->
        input.channel.transferTo(0, input.channel.size(), output.channel)
    }
}
```

Then the user (or you) can grab the file from /sdcard/Android/data/… or share it with the Storage Access Framework.

──────────────────────────────────
Things to remember
──────────────────────────────────
• The .db file IS the “SQLite format” you asked for—no conversion needed.  
• Copy the database while the app is stopped or closed, or copy the extra -wal/-shm files as well; otherwise you can end up with an inconsistent snapshot.  
• The above works only on debuggable builds (the default when you run from Android Studio). For release builds on a non-rooted device there is no direct file access without adding your own export code.