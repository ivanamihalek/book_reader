// MainActivity.kt
package com.dogmaticcentral.bookreader

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import com.dogmaticcentral.bookreader.navigation.AppNavigation

class MainActivity : BaseActivity() {

    @Composable
    override fun NavigationContent(navController: NavHostController) {
        AppNavigation(navController)
    }
}
