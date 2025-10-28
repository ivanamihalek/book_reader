// navigation/AdminNavigation.kt
package com.dogmaticcentral.bookreader.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.dogmaticcentral.bookreader.screens.AdminScreen


const val ADMIN_HOME_ROUTE = "admin_home"

@Composable
fun AdminNavigation(navController: NavHostController) {
    NavHost(navController, startDestination = ADMIN_HOME_ROUTE) {
        composable(ADMIN_HOME_ROUTE) { AdminScreen(navController) }
        // Add more admin routes here
    }
}
