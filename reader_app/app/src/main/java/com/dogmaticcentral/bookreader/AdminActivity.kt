// AdminActivity.kt
package com.dogmaticcentral.bookreader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dogmaticcentral.bookreader.screens.ErrorScreen
import com.dogmaticcentral.bookreader.screens.LoadingScreen
import com.dogmaticcentral.bookreader.ui.theme.BookReaderTheme
import com.dogmaticcentral.bookreader.viewmodel.MainViewModel
import com.dogmaticcentral.bookreader.viewmodel.MainViewModelFactory
import kotlin.system.exitProcess

class AdminActivity : ComponentActivity() {

    private val mainViewModel: MainViewModel by viewModels {
        MainViewModelFactory(application)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            BookReaderTheme {
                val databaseState by mainViewModel.databaseState.collectAsStateWithLifecycle()

                when (val state = databaseState) {
                    is MainViewModel.DatabaseState.Loading -> {
                        LoadingScreen()
                    }

                    is MainViewModel.DatabaseState.Success -> {
                        CompositionLocalProvider(LocalBookRepository provides state.repository) {
                            AdminScreen()
                        }
                    }

                    is MainViewModel.DatabaseState.Error -> {
                        ErrorScreen(
                            message = state.message,
                            onDismiss = {
                                finish()
                                exitProcess(0)
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen() {
    // TODO: Get actual books from repository
    val sampleBooks = listOf(
        "Book One",
        "Book Two",
        "Book Three",
        "Another Book Title",
        "Yet Another Book"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Book Administration") }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            item {
                // Header row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Book Name",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "Delete",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(64.dp)
                    )
                }
                HorizontalDivider()
            }

            items(sampleBooks) { bookName ->
                BookRow(
                    bookName = bookName,
                    onDelete = {
                        // TODO: Implement delete logic
                    }
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
fun BookRow(
    bookName: String,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = bookName,
            modifier = Modifier.weight(1f)
        )
        IconButton(
            onClick = onDelete,
            modifier = Modifier.width(64.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Delete $bookName",
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}