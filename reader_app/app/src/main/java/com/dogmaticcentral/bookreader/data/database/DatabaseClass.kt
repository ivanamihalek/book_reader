package com.dogmaticcentral.bookreader.data.database

import android.content.Context
import android.text.TextUtils
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File

@Database(
    entities = [Book::class, Chapter::class],
    version = 1,
    exportSchema = true
)
abstract class BookReaderDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun chapterDao(): ChapterDao

    companion object {
        @Volatile
        private var INSTANCE: BookReaderDatabase? = null

        fun getDatabase(context: Context): BookReaderDatabase {
            return INSTANCE ?: synchronized(this) {
                // Check if database file exists before attempting to open
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    BookReaderDatabase::class.java,
                    "book_reader_database"
                )
                    .createFromAsset("databases/bookreader.db") // This line loads your pre-made database
                    .addCallback(DatabaseValidationCallback())
                    .build()

                INSTANCE = instance
                instance
            }
        }

        private class DatabaseValidationCallback : RoomDatabase.Callback() {
            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)

                // Validate that the database has data
                val bookCount = db.query("SELECT COUNT(*) FROM books").use { cursor ->
                    if (cursor.moveToFirst()) cursor.getInt(0) else 0
                }

                val chapterCount = db.query("SELECT COUNT(*) FROM chapters").use { cursor ->
                    if (cursor.moveToFirst()) cursor.getInt(0) else 0
                }

                if (bookCount == 0) {
                    throw EmptyDatabaseException(
                        "Database is empty: no books found"
                    )
                }

                if (chapterCount == 0) {
                    throw EmptyDatabaseException(
                        "Database is empty: no chapters found"
                    )
                }
            }
        }
    }
}

// Custom exceptions
class DatabaseNotFoundException(message: String) : Exception(message)
class EmptyDatabaseException(message: String) : Exception(message)