package com.dogmaticcentral.bookreader.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow
//DAO = Data Access Object
@Dao
interface BookDao {
    @Query("SELECT * FROM books")
    fun getAllBooks(): Flow<List<Book>>

    @Query("SELECT * FROM books")
    suspend fun getAllBooksOneShot(): List<Book>

    @Query("SELECT * FROM books WHERE id = :bookId")
    suspend fun getBookById(bookId: Int): Book?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBook(book: Book)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBooks(books: List<Book>)

    @Update
    suspend fun updateBook(book: Book)

    @Delete
    suspend fun deleteBook(book: Book)

    @Query("SELECT * FROM books WHERE id IN (SELECT bookId FROM chapters WHERE lastPlayedTimestamp > 0 ORDER BY lastPlayedTimestamp DESC LIMIT 1)")
    fun getMostRecentlyPlayedBook(): Flow<Book?>

}

@Dao
interface ChapterDao {
    @Query("SELECT * FROM chapters WHERE bookId = :bookId ORDER BY id")
    fun getChaptersByBookId(bookId: Int): Flow<List<Chapter>>

    @Query("SELECT id FROM chapters WHERE bookId = :bookId ORDER BY id")
    suspend fun getChapterIdsByBookId(bookId: Int): List<Int>

    @Query("SELECT * FROM chapters WHERE bookId = :bookId ORDER BY id")
    suspend fun getChaptersByBookIdOneShot(bookId: Int): List<Chapter>

    /**
     * Gets all chapter IDs from the same book as the given chapterId.
     * Orders them by ID to maintain chapter sequence.
     */
    @Query("""
        SELECT id FROM chapters
        WHERE bookId = (SELECT bookId FROM chapters WHERE id = :chapterId)
        ORDER BY id ASC
    """)
    suspend fun getAllChapterIdsInSameBook(chapterId: Int): List<Int>



    @Query("SELECT * FROM chapters WHERE id = :chapterId")
    suspend fun getChapterById(chapterId: Int): Chapter?

    @Query("SELECT * FROM chapters")
    suspend fun getAllChapters(): List<Chapter>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChapter(chapter: Chapter)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChapters(chapters: List<Chapter>)

    @Update
    suspend fun updateChapter(chapter: Chapter)

    @Query("UPDATE chapters SET lastPlayedPosition = :position WHERE id = :chapterId")
    suspend fun updateLastPlayedPosition(chapterId: Int, position: Long)

    @Query("UPDATE chapters SET lastPlayedPosition = :position, lastPlayedTimestamp = :timeStopped, finishedPlaying = :finishedPlaying WHERE id = :chapterId")
    suspend fun updatePlayData(chapterId: Int, position: Long, timeStopped: Long, finishedPlaying: Boolean)

    @Delete
    suspend fun deleteChapter(chapter: Chapter)

    @Query("SELECT * FROM chapters WHERE bookId = :bookId AND lastPlayedTimestamp > 0 ORDER BY lastPlayedTimestamp DESC LIMIT 1")
    fun getMostRecentlyPlayedChapter(bookId: Int): Flow<Chapter?>

    // chapter navigation

    /**
     * Gets the next chapter in the same book
     * Orders by ID to maintain chapter sequence
     */
    @Query("""
        SELECT * FROM chapters 
        WHERE bookId = (SELECT bookId FROM chapters WHERE id = :currentChapterId)
        AND id > :currentChapterId
        ORDER BY id ASC
        LIMIT 1
    """)
    suspend fun getNextChapter(currentChapterId: Int): Chapter?

    /**
     * Gets the previous chapter in the same book
     * Orders by ID descending to get the immediate previous chapter
     */
    @Query("""
        SELECT * FROM chapters 
        WHERE bookId = (SELECT bookId FROM chapters WHERE id = :currentChapterId)
        AND id < :currentChapterId
        ORDER BY id DESC
        LIMIT 1
    """)
    suspend fun getPreviousChapter(currentChapterId: Int): Chapter?

    /**
     * Gets just the ID of the next chapter (more efficient if you only need the ID)
     */
    @Query("""
        SELECT id FROM chapters 
        WHERE bookId = (SELECT bookId FROM chapters WHERE id = :currentChapterId)
        AND id > :currentChapterId
        ORDER BY id ASC
        LIMIT 1
    """)
    suspend fun getNextChapterId(currentChapterId: Int): Int?

    /**
     * Gets just the ID of the previous chapter (more efficient if you only need the ID)
     */
    @Query("""
        SELECT id FROM chapters 
        WHERE bookId = (SELECT bookId FROM chapters WHERE id = :currentChapterId)
        AND id < :currentChapterId
        ORDER BY id DESC
        LIMIT 1
    """)
    suspend fun getPreviousChapterId(currentChapterId: Int): Int?

    /**
     * Checks if a chapter is the first in its book
     */
    @Query("""
        SELECT CASE 
            WHEN EXISTS (
                SELECT 1 FROM chapters 
                WHERE bookId = (SELECT bookId FROM chapters WHERE id = :chapterId)
                AND id < :chapterId
            ) THEN 0
            ELSE 1
        END
    """)
    suspend fun isFirstChapter(chapterId: Int): Boolean

    /**
     * Checks if a chapter is the last in its book
     */
    @Query("""
        SELECT CASE 
            WHEN EXISTS (
                SELECT 1 FROM chapters 
                WHERE bookId = (SELECT bookId FROM chapters WHERE id = :chapterId)
                AND id > :chapterId
            ) THEN 0
            ELSE 1
        END
    """)
    suspend fun isLastChapter(chapterId: Int): Boolean

    /**
     * Gets the position of a chapter within its book (1-based index)
     */
    @Query("""
        SELECT COUNT(*) + 1
        FROM chapters
        WHERE bookId = (SELECT bookId FROM chapters WHERE id = :chapterId)
        AND id < :chapterId
    """)
    suspend fun getChapterPosition(chapterId: Int): Int

    /**
     * Gets the total number of chapters in the same book
     */
    @Query("""
        SELECT COUNT(*)
        FROM chapters
        WHERE bookId = (SELECT bookId FROM chapters WHERE id = :chapterId)
    """)
    suspend fun getTotalChaptersInBook(chapterId: Int): Int

     /**
     * Gets the last chapter in the book given bookId
     */
     @Query("SELECT * FROM chapters WHERE bookId = :bookId ORDER BY id DESC LIMIT 1")
     suspend fun getLastChapterOfBook(bookId: Int): Chapter?

     /**
     * Checks if a chapter is finished playing
     */
     @Query("SELECT finishedPlaying FROM chapters WHERE id = :chapterId")
     suspend fun chapterFinishedPlaying(chapterId: Int): Boolean

}
