package com.dogmaticcentral.bookreader.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.dogmaticcentral.bookreader.data.BookRepository
import com.dogmaticcentral.bookreader.data.database.Book
import com.dogmaticcentral.bookreader.data.database.Chapter
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

// ViewModel for book list screen
class BookListViewModel(
    private val repository: BookRepository
) : ViewModel() {

    val books: StateFlow<List<Book>> = repository.getAllBooks()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    companion object {
        fun factory(repository: BookRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                BookListViewModel(repository)
            }
        }
    }
}

// ViewModel for book detail/chapters screen
class BookDetailViewModel(
    private val repository: BookRepository,
    private val bookId: Int
) : ViewModel() {

    private val _book = MutableStateFlow<Book?>(null)
    val book: StateFlow<Book?> = _book.asStateFlow()

    val chapters: StateFlow<List<Chapter>> = repository.getChaptersByBookId(bookId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        loadBook()
    }

    private fun loadBook() {
        viewModelScope.launch {
            _book.value = repository.getBookById(bookId)
        }
    }

    fun updatePlaybackPosition(chapterId: Int, position: Long) {
        viewModelScope.launch {
            repository.updateLastPlayedPosition(chapterId, position)
        }
    }

    companion object {
        fun factory(
            repository: BookRepository,
            bookId: Int
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                BookDetailViewModel(repository, bookId)
            }
        }
    }
}

