package com.dogmaticcentral.bookreader.data

import android.content.Context
import android.net.Uri
import android.util.Log
import com.dogmaticcentral.bookreader.data.database.*
import com.dogmaticcentral.bookreader.data.media.getAudioContentUriFromFilePath
import com.dogmaticcentral.bookreader.data.media.saveMp3ToMediaStore
import kotlinx.coroutines.flow.Flow
import java.io.File

class BookRepository(
    private val bookDao: BookDao,
    private val chapterDao: ChapterDao
) {
    // Book operations
    fun getAllBooks(): Flow<List<Book>> = bookDao.getAllBooks()

    suspend fun getAllBooksOneShot(): List<Book> = bookDao.getAllBooksOneShot()

    suspend fun getBookById(bookId: Int): Book? = bookDao.getBookById(bookId)


    // Chapter operations
    fun getChaptersByBookId(bookId: Int): Flow<List<Chapter>> =
        chapterDao.getChaptersByBookId(bookId)

    suspend fun getChapterById(chapterId: Int): Chapter? =
        chapterDao.getChapterById(chapterId)

    // Navigation methods that delegate to DAO
    suspend fun getNextChapterId(currentChapterId: Int): Int? =
        chapterDao.getNextChapterId(currentChapterId)

    suspend fun getPreviousChapterId(currentChapterId: Int): Int? =
        chapterDao.getPreviousChapterId(currentChapterId)

    suspend fun getNextChapter(currentChapterId: Int): Chapter? =
        chapterDao.getNextChapter(currentChapterId)

    suspend fun getPreviousChapter(currentChapterId: Int): Chapter? =
        chapterDao.getPreviousChapter(currentChapterId)

    suspend fun isFirstChapter(chapterId: Int): Boolean =
        chapterDao.isFirstChapter(chapterId)

    suspend fun isLastChapter(chapterId: Int): Boolean =
        chapterDao.isLastChapter(chapterId)


    data class ChapterNavigationInfo(
        val currentChapterId: Int,
        val previousChapterId: Int?,
        val nextChapterId: Int?,
        val isFirstChapter: Boolean,
        val isLastChapter: Boolean,
        val chapterPosition: Int,
        val totalChapters: Int
    )
    suspend fun getChapterNavigationInfo(chapterId: Int): ChapterNavigationInfo? {
        val chapterIds = chapterDao.getAllChapterIdsInSameBook(chapterId)
        val currentIndex = chapterIds.indexOf(chapterId)
        if (currentIndex == -1) return null

        return ChapterNavigationInfo(
            currentChapterId = chapterId,
            previousChapterId = if (currentIndex > 0) chapterIds[currentIndex - 1] else null,
            nextChapterId = if (currentIndex < chapterIds.lastIndex) chapterIds[currentIndex + 1] else null,
            isFirstChapter = currentIndex == 0,
            isLastChapter = currentIndex == chapterIds.lastIndex,
            chapterPosition = currentIndex + 1,
            totalChapters = chapterIds.size
        )
    }


    suspend fun updateLastPlayedPosition(chapterId: Int, position: Long) =
        chapterDao.updateLastPlayedPosition(chapterId, position)

    suspend fun updateLastPlayedPositionAndTime(
        chapterId: Int,
        position: Long,
        timeStopped: Long
    ) {
        chapterDao.updateLastPlayedPositionAndTime(chapterId, position, timeStopped)
    }

    // Get the most recently played book based on lastPlayedTimestamp in chapters
    fun getMostRecentlyPlayedBook(): Flow<Book?> = bookDao.getMostRecentlyPlayedBook()

    // Get the most recently played chapter for a given bookId
    fun getMostRecentlyPlayedChapter(bookId: Int): Flow<Chapter?> = chapterDao.getMostRecentlyPlayedChapter(bookId)

}

// Extension functions and utility functions
fun String.toCamelCaseDirectory(): String {
    return this.split(" ")
        .joinToString("") { word ->
            word.replaceFirstChar {
                if (it.isLowerCase()) it.titlecase()
                else it.toString()
            }
        }
        .replace("[^a-zA-Z0-9]".toRegex(), "")
}

suspend fun getAudioFilePath(repository: BookRepository, bookId: Int, chapterId: Int): String? {
    val book = repository.getBookById(bookId)
    val chapter = repository.getChapterById(chapterId)
    var filePath: String? = null
    if (book != null && chapter != null) {
        val directoryName = book.title.toCamelCaseDirectory()
        val externalStorageDirectory = android.os.Environment.getExternalStorageDirectory()
        val audioBooksDIr = android.os.Environment.DIRECTORY_AUDIOBOOKS

        filePath = "$externalStorageDirectory/$audioBooksDIr/BookReader/audio/$directoryName/${chapter.fileName}"
    }

    return filePath
}

suspend fun getAudioContentUri(
    context: Context,
    repository: BookRepository,
    bookId: Int,
    chapterId: Int
): Uri? {
    val filePath = getAudioFilePath(repository, bookId, chapterId) ?: return null
    var uri: Uri? = getAudioContentUriFromFilePath(context, filePath)
    if (uri == null) {
        uri = saveMp3ToMediaStore(context, filePath)
    }
    return uri
}