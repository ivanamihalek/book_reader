package com.dogmaticcentral.bookreader.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenLayout(
    navController: NavHostController,
    showBackButton: Boolean,
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        // Remove bottomBar
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            // Main content
            content(innerPadding)

            // Place navigation bar manually at bottom left
            if (showBackButton) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 16.dp, bottom = 48.dp)
                ) {
                    LeftArrowButton(
                        onClick = { navController.popBackStack() },
                        modifier = Modifier.width(96.dp),
                    )
                }
            }
        }
    }
}
