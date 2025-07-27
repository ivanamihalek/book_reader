// navigation/AppNavigation.kt
package com.dogmaticcentral.bookreader.navigation

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.dogmaticcentral.bookreader.screens.HomeScreen
import com.dogmaticcentral.bookreader.screens.ChaptersScreen
import com.dogmaticcentral.bookreader.screens.PlayerScreen

const val HOME_ROUTE = "home"
const val CHAPTERS_ROUTE = "chapters/{bookId}"
const val PLAYER_ROUTE = "player/{bookId}/{chapterId}"
@Composable
fun AppNavigation(navController: NavHostController) {
    NavHost(navController, startDestination = HOME_ROUTE) {
        composable(HOME_ROUTE) { HomeScreen(navController) }

        composable(
            route = "chapters/{bookId}",
            arguments = listOf(
                navArgument("bookId") { type = NavType.IntType }
            )
        ) { backStackEntry ->
            val bookId = backStackEntry.arguments?.getInt("bookId") ?: 1
            ChaptersScreen(navController, bookId)
        }

        composable(
            route = "player/{bookId}/{chapterId}?playImmediately={playImmediately}",
            arguments = listOf(
                navArgument("bookId") { type = NavType.IntType },
                navArgument("chapterId") { type = NavType.IntType },
                navArgument("playImmediately") {
                    type = NavType.BoolType
                    defaultValue = false
                }
            )
        ) { backStackEntry ->
            PlayerScreen(
                navController,
                bookId = backStackEntry.arguments?.getInt("bookId") ?: 1,
                chapterId = backStackEntry.arguments?.getInt("chapterId") ?: 1,
                playImmediately = backStackEntry.arguments?.getBoolean("playImmediately") ?: false
            )
        }
    }
}