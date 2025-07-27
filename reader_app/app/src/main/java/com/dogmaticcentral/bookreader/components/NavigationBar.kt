package com.dogmaticcentral.bookreader.components

// looks like things from the same packge, such as LargeButton here
// do not need to be imported

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController

@Composable
fun NavigationBar(navController: NavHostController, showBackButton: Boolean) {
    Row(
        modifier = Modifier.padding(16.dp, top = 16.dp, bottom = 48.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.Start // This ensures content starts from left

    ) {
        if (showBackButton) {
            LeftArrowButton(
                onClick = { navController.popBackStack() },
                modifier = Modifier.width(96.dp),

            )
        } else {
           // Spacer(Modifier.weight(1f))
        }

    }
}