package com.dogmaticcentral.bookreader.viewmodel

// HomeViewModel.kt
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dogmaticcentral.bookreader.data.BookRepository
import com.dogmaticcentral.bookreader.data.database.Book
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class HomeViewModel(
    private val repository: BookRepository
) : ViewModel() {
    // Track both books and their completion status
    private val _books = MutableStateFlow<List<Book>>(emptyList())
    private val _completionStatus = MutableStateFlow<Map<Int, Boolean>>(emptyMap())

    val uiState: StateFlow<HomeUiState> = combine(
        _books,
        _completionStatus,
        repository.getMostRecentlyPlayedBook()
    ) { books, statusMap, recentBook ->
        HomeUiState(
            books = books,
            completionStatus = statusMap,
            mostRecentBook = recentBook
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState()
    )

    init {
        loadBooks()
    }

    private fun loadBooks() {
        viewModelScope.launch {
            // Load books
            val books = repository.getAllBooksOneShot()
            _books.value = books

            // Load completion status in parallel
            val statusMap = mutableMapOf<Int, Boolean>()
            books.forEach { book ->
                val lastChapter = repository.getLastChapterOfBook(book.id)
                statusMap[book.id] = lastChapter?.let { chapter ->
                    repository.chapterFinishedPlaying(chapter.id)
                } ?: false
            }
            _completionStatus.value = statusMap
        }
    }
}

data class HomeUiState(
    val books: List<Book> = emptyList(),
    val completionStatus: Map<Int, Boolean> = emptyMap(),
    val mostRecentBook: Book? = null
)