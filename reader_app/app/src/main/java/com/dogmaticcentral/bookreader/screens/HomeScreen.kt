package com.dogmaticcentral.bookreader.screens

import android.util.Log
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.dogmaticcentral.bookreader.LocalBookRepository
import com.dogmaticcentral.bookreader.components.LargeButton
import com.dogmaticcentral.bookreader.components.ScreenLayout

@Composable
fun HomeScreen(navController: NavHostController) {
    val repository = LocalBookRepository.current
    val booksState = repository.getAllBooks().collectAsState(initial = emptyList())
    val mostRecentBookState = repository.getMostRecentlyPlayedBook().collectAsState(initial = null)
    ScreenLayout(
         navController = navController,
         showBackButton = false,
   ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp)
        ) {
            items(booksState.value) { book ->
                LargeButton(
                    text = book.title,
                    onClick = { navController.navigate("chapters/${book.id}") },
                    modifier = Modifier.fillMaxWidth()
                        .then(
                            if (book.id == mostRecentBookState.value?.id) {
                                Modifier.border(
                                    width = 4.dp,
                                    color = Color.Red
                                )
                            } else {
                                Modifier
                            }
                        )
                )
                Spacer(Modifier.height(32.dp))
            }
        }
    }

}