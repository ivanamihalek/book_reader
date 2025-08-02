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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.dogmaticcentral.bookreader.LocalBookRepository
import com.dogmaticcentral.bookreader.components.LargeButton
import com.dogmaticcentral.bookreader.components.ScreenLayout
import com.dogmaticcentral.bookreader.viewmodel.HomeViewModel
import com.dogmaticcentral.bookreader.viewmodel.HomeViewModelFactory

// HomeScreen.kt
@Composable
fun HomeScreen(
    navController: NavHostController,
    viewModel: HomeViewModel = viewModel(factory = HomeViewModelFactory(LocalBookRepository.current))
) {
    val uiState by viewModel.uiState.collectAsState()

    ScreenLayout(
        navController = navController,
        showBackButton = false,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp)
        ) {
            items(uiState.books) { book ->
                LargeButton(
                    text = book.title,
                    onClick = { navController.navigate("chapters/${book.id}") },
                    modifier = Modifier.fillMaxWidth()
                        .then(
                            when {
                                book.id == uiState.mostRecentBook?.id ->
                                    Modifier.border(4.dp, Color.Red)
                                uiState.completionStatus[book.id] == true ->
                                    Modifier.border(4.dp, Color.Blue)
                                else -> Modifier
                            }
                        )
                )
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}