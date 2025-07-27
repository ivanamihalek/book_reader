// MainActivity.kt
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.dogmaticcentral.bookreader.data.BookRepository
import com.dogmaticcentral.bookreader.navigation.AppNavigation
import com.dogmaticcentral.bookreader.screens.ErrorScreen
import com.dogmaticcentral.bookreader.screens.LoadingScreen
import com.dogmaticcentral.bookreader.ui.theme.BookReaderTheme
import com.dogmaticcentral.bookreader.viewmodel.MainViewModel
import com.dogmaticcentral.bookreader.viewmodel.MainViewModelFactory
import kotlin.system.exitProcess


class MainActivity : ComponentActivity() {

    private val mainViewModel: MainViewModel by viewModels {
        MainViewModelFactory(application)
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // PERMISSIONS
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_MEDIA_AUDIO
            ) == PackageManager.PERMISSION_GRANTED) {
            // Permission already granted, proceed
        } else {
            // Request permission
            requestPermissionLauncher.launch(Manifest.permission.READ_MEDIA_AUDIO)
        }

        // DATABASE
        setContent {
            BookReaderTheme {
                val databaseState by mainViewModel.databaseState.collectAsStateWithLifecycle()

                when (val state = databaseState) {
                    is MainViewModel.DatabaseState.Loading -> {
                        LoadingScreen()
                    }

                    is MainViewModel.DatabaseState.Success -> {
                        // Provide repository to the entire app
                        CompositionLocalProvider(LocalBookRepository provides state.repository) {
                            AppNavigation(rememberNavController())
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
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Permission granted, proceed
        } else {
            // Permission denied
            showPermissionDeniedDialog()
        }
    }
    private fun showPermissionDeniedDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permission Required")
            .setMessage("Audio access permission is required to play files. The app will now close.")
            .setCancelable(false)
            .setPositiveButton("OK") { _, _ ->
                // Close the activity gracefully
                finish()
            }
            .show()
    }

}

// CompositionLocal for providing repository throughout the app
val LocalBookRepository = staticCompositionLocalOf<BookRepository> {
    error("No BookRepository provided")
}