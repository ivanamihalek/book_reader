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
        val bookDir = "HarryPotter"
        val result = StoragePaths.getBookRelativePath(bookDir)

        assertEquals("Audiobooks/BookReader/audio/HarryPotter/", result)
    }

    @Test
    fun `getBookRelativePath ends with slash`() {
        val result = StoragePaths.getBookRelativePath("TestBook")
        assertTrue(result.endsWith("/"))
    }

    @Test
    fun `getFullRelativePath joins book directory and filename`() {
        val bookDir = "LordOfTheRings"
        val fileName = "chapter1.mp3"
        val result = StoragePaths.getFullRelativePath(bookDir, fileName)

        assertEquals("Audiobooks/BookReader/audio/LordOfTheRings/chapter1.mp3", result)
    }

    @Test
    fun `getFullRelativePath handles various filenames`() {
        val bookDir = "TestBook"

        assertEquals(
            "Audiobooks/BookReader/audio/TestBook/chapter_01.mp3",
            StoragePaths.getFullRelativePath(bookDir, "chapter_01.mp3")
        )

        assertEquals(
            "Audiobooks/BookReader/audio/TestBook/intro.m4a",
            StoragePaths.getFullRelativePath(bookDir, "intro.m4a")
        )
    }


    @Test
    fun `toPascalCaseDirectory converts simple titles`() {
        assertEquals("HarryPotter", "Harry Potter".toPascalCase())
        assertEquals("HarryPotter", "harry PoTTEr".toPascalCase())
        assertEquals("TheHobbit", "the hobbit".toPascalCase())
        assertEquals("Book", "Book".toPascalCase())
    }

    @Test
    fun `toPascalCaseDirectory handles single word`() {
        assertEquals("Dune", "Dune".toPascalCase())
        assertEquals("Dune", "DUNE".toPascalCase())
    }

    @Test
    fun `toPascalCaseDirectory removes special characters`() {
        assertEquals("HarryPotter", "Harry Potter!!!".toPascalCase())
        assertEquals("BookTitle", "Book-Title".toPascalCase())
        assertEquals("TestBook", "Test@Book#".toPascalCase())
    }

    @Test
    fun `toPascalCaseDirectory handles multiple spaces`() {
        assertEquals("TheBookTitle", "The   Book    Title".toPascalCase())
    }

    @Test
    fun `toPascalCaseDirectory handles numbers`() {
        assertEquals("1984", "1984".toPascalCase())
        assertEquals("Book1Chapter2", "Book 1 Chapter 2".toPascalCase())
    }

    @Test
    fun `toCamelCaseDirectory handles empty and whitespace`() {
        assertEquals("unknown", "".toPascalCase())
        assertEquals("unknown", "   ".toPascalCase())
    }

    @Test
    fun `toCamelCaseDirectory handles only special characters`() {
        assertEquals("unknown", "!!!@@@###".toPascalCase())
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