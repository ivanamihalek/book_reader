package com.dogmaticcentral.bookreader.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dogmaticcentral.bookreader.data.BookRepository
import com.dogmaticcentral.bookreader.data.database.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// ViewModel that holds and manages the repository
class MainViewModel(application: Application) : AndroidViewModel(application) {

    sealed class DatabaseState {
        object Loading : DatabaseState()
        data class Success(val repository: BookRepository) : DatabaseState()
        data class Error(val message: String) : DatabaseState()
    }

    private val _databaseState = MutableStateFlow<DatabaseState>(DatabaseState.Loading)
    val databaseState: StateFlow<DatabaseState> = _databaseState.asStateFlow()

    private var database: BookReaderDatabase? = null
    var repository: BookRepository? = null
        private set

    init {
        initializeDatabase()
    }

    private fun initializeDatabase() {
        viewModelScope.launch {
            try {
                // Try to initialize the database
                database = BookReaderDatabase.getDatabase(getApplication())

                // Create repository
                val repo = BookRepository(database!!.bookDao(), database!!.chapterDao())
                repository = repo

                // Update state
                _databaseState.value = DatabaseState.Success(repo)

            } catch (e: DatabaseNotFoundException) {
                _databaseState.value = DatabaseState.Error(
                    "Database file not found. Please ensure the database is properly installed."
                )
            } catch (e: EmptyDatabaseException) {
                _databaseState.value = DatabaseState.Error(
                    "Database is empty. Please ensure the database contains book data."
                )
            } catch (e: Exception) {
                _databaseState.value = DatabaseState.Error(
                    "Failed to initialize database: ${e.message}"
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        database?.close()
    }
}

// Factory for creating MainViewModel
class MainViewModelFactory(
    private val application: Application
) : ViewModelProvider.AndroidViewModelFactory(application) {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

