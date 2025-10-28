// BaseActivity.kt
package com.dogmaticcentral.bookreader

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.dogmaticcentral.bookreader.data.BookRepository
import com.dogmaticcentral.bookreader.screens.ErrorScreen
import com.dogmaticcentral.bookreader.screens.LoadingScreen
import com.dogmaticcentral.bookreader.ui.theme.BookReaderTheme
import com.dogmaticcentral.bookreader.viewmodel.MainViewModel
import com.dogmaticcentral.bookreader.viewmodel.MainViewModelFactory
import kotlin.system.exitProcess

abstract class BaseActivity : ComponentActivity() {

    protected val mainViewModel: MainViewModel by viewModels {
        MainViewModelFactory(application)
    }

    @Composable
    abstract fun NavigationContent(navController: NavHostController)

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // PERMISSIONS
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_MEDIA_AUDIO
            ) == PackageManager.PERMISSION_GRANTED) {
            setupContent()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.READ_MEDIA_AUDIO)
        }
    }

    // NOT @Composable - just calls setContent
    private fun setupContent() {
        setContent {
            AppContent()
        }
    }

    // THIS is @Composable
    @Composable
    private fun AppContent() {
        BookReaderTheme {
            val databaseState by mainViewModel.databaseState.collectAsStateWithLifecycle()

            when (val state = databaseState) {
                is MainViewModel.DatabaseState.Loading -> {
                    LoadingScreen()
                }

                is MainViewModel.DatabaseState.Success -> {
                    CompositionLocalProvider(LocalBookRepository provides state.repository) {
                        NavigationContent(rememberNavController())
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

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            setupContent()
        } else {
            showPermissionDeniedDialog()
        }
    }

    private fun showPermissionDeniedDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permission Required")
            .setMessage("Audio access permission is required to play files. The app will now close.")
            .setCancelable(false)
            .setPositiveButton("OK") { _, _ ->
                finish()
            }
            .show()
    }
}

// CompositionLocal for providing repository throughout the app
val LocalBookRepository = staticCompositionLocalOf<BookRepository> {
    error("No BookRepository provided")
}