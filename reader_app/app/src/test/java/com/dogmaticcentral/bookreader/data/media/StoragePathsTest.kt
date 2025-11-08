package com.dogmaticcentral.bookreader.data.media

import org.junit.Assert.*
import org.junit.Test

class StoragePathsTest {

    @Test
    fun `RELATIVE_PATH has correct format`() {
        val expected = "Audiobooks/BookReader/audio/"
        assertEquals(expected, StoragePaths.RELATIVE_PATH)
    }

    @Test
    fun `RELATIVE_PATH ends with slash`() {
        assertTrue(StoragePaths.RELATIVE_PATH.endsWith("/"))
    }

    @Test
    fun `RELATIVE_PATH contains required components`() {
        assertTrue(StoragePaths.RELATIVE_PATH.contains("Audiobooks"))
        assertTrue(StoragePaths.RELATIVE_PATH.contains("BookReader"))
        assertTrue(StoragePaths.RELATIVE_PATH.contains("audio"))
    }

    @Test
    fun `getBookRelativePath appends book directory`() {
        val bookDir = "harryPotter"
        val result = StoragePaths.getBookRelativePath(bookDir)

        assertEquals("Audiobooks/BookReader/audio/harryPotter/", result)
    }

    @Test
    fun `getBookRelativePath ends with slash`() {
        val result = StoragePaths.getBookRelativePath("testBook")
        assertTrue(result.endsWith("/"))
    }

    @Test
    fun `getFullRelativePath joins book directory and filename`() {
        val bookDir = "lordOfTheRings"
        val fileName = "chapter1.mp3"
        val result = StoragePaths.getFullRelativePath(bookDir, fileName)

        assertEquals("Audiobooks/BookReader/audio/lordOfTheRings/chapter1.mp3", result)
    }

    @Test
    fun `getFullRelativePath handles various filenames`() {
        val bookDir = "testBook"

        assertEquals(
            "Audiobooks/BookReader/audio/testBook/chapter_01.mp3",
            StoragePaths.getFullRelativePath(bookDir, "chapter_01.mp3")
        )

        assertEquals(
            "Audiobooks/BookReader/audio/testBook/intro.m4a",
            StoragePaths.getFullRelativePath(bookDir, "intro.m4a")
        )
    }


    @Test
    fun `toCamelCaseDirectory converts simple titles`() {
        assertEquals("harryPotter", "Harry Potter".toCamelCaseDirectory())
        assertEquals("theHobbit", "The Hobbit".toCamelCaseDirectory())
        assertEquals("book", "Book".toCamelCaseDirectory())
    }

    @Test
    fun `toCamelCaseDirectory handles single word`() {
        assertEquals("dune", "Dune".toCamelCaseDirectory())
        assertEquals("dune", "DUNE".toCamelCaseDirectory())
    }

    @Test
    fun `toCamelCaseDirectory removes special characters`() {
        assertEquals("harryPotter", "Harry Potter!!!".toCamelCaseDirectory())
        assertEquals("bookTitle", "Book-Title".toCamelCaseDirectory())
        assertEquals("testBook", "Test@Book#".toCamelCaseDirectory())
    }

    @Test
    fun `toCamelCaseDirectory handles multiple spaces`() {
        assertEquals("theBookTitle", "The   Book    Title".toCamelCaseDirectory())
    }

    @Test
    fun `toCamelCaseDirectory handles numbers`() {
        assertEquals("1984", "1984".toCamelCaseDirectory())
        assertEquals("book1Chapter2", "Book 1 Chapter 2".toCamelCaseDirectory())
    }

    @Test
    fun `toCamelCaseDirectory handles empty and whitespace`() {
        assertEquals("unknown", "".toCamelCaseDirectory())
        assertEquals("unknown", "   ".toCamelCaseDirectory())
    }

    @Test
    fun `toCamelCaseDirectory handles only special characters`() {
        assertEquals("unknown", "!!!@@@###".toCamelCaseDirectory())
    }

    @Test
    fun `path components do not have double slashes`() {
        val bookDir = "test"
        val fileName = "file.mp3"

        val relativePath = StoragePaths.getFullRelativePath(bookDir, fileName)
        assertFalse(relativePath.contains("//"))

        val bookPath = StoragePaths.getBookRelativePath(bookDir)
        assertFalse(bookPath.contains("//"))
    }
}