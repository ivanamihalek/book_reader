package com.dogmaticcentral.bookreader


import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dogmaticcentral.bookreader.data.database.BookReaderDatabase
import com.dogmaticcentral.bookreader.data.database.ChapterDao
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChapterDaoTest {
    private lateinit var chapterDao: ChapterDao
    private lateinit var db: BookReaderDatabase

    @Before
    fun createDb() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.databaseBuilder(
            context, BookReaderDatabase::class.java, "test.db"
        ).createFromAsset("databases/bookreader.db").build()
        chapterDao = db.chapterDao()
    }

    @Test
    suspend fun testReadChapters() {
        val chapters = chapterDao.getAllChapters()
        assert(chapters.isNotEmpty())
    }

    @After
    fun closeDb() {
        db.close()
    }
}