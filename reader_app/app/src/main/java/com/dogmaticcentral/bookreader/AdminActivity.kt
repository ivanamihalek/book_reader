// AdminActivity.kt
package com.dogmaticcentral.bookreader

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import com.dogmaticcentral.bookreader.navigation.AdminNavigation

class AdminActivity : BaseActivity() {

    @Composable
    override fun NavigationContent(navController: NavHostController) {
        AdminNavigation(navController)
    }
}