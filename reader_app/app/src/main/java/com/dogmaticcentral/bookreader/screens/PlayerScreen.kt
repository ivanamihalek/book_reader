package com.dogmaticcentral.bookreader.screens

import android.app.AlertDialog
import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.dogmaticcentral.bookreader.LocalBookRepository
import com.dogmaticcentral.bookreader.R
import com.dogmaticcentral.bookreader.components.ScreenLayout
import com.dogmaticcentral.bookreader.data.getAudioContentUri
import com.dogmaticcentral.bookreader.viewmodel.PlaybackState
import com.dogmaticcentral.bookreader.viewmodel.PlayerViewModel
import com.dogmaticcentral.bookreader.viewmodel.PlayerViewModelFactory
import kotlinx.coroutines.delay

@Composable
fun PlayerScreen(
    navController: NavHostController,
    bookId: Int,
    chapterId: Int,
    playImmediately: Boolean,
    playerViewModel: PlayerViewModel = viewModel(
        factory = PlayerViewModelFactory(
            LocalBookRepository.current,
            LocalContext.current.applicationContext as Application
        )
    )
) {
    val context = LocalContext.current
    val repository = LocalBookRepository.current
    val playbackState by playerViewModel.playbackState.collectAsState()
    var audioFileUri by remember { mutableStateOf<Uri?>(null) }
    val lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current
    var isLoadingNext by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                playerViewModel.savePlaybackState(playerViewModel.currentPosition.value.toLong())
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            playerViewModel.savePlaybackState(playerViewModel.currentPosition.value.toLong())
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(bookId, chapterId) {
        audioFileUri = getAudioContentUri(context, repository, bookId, chapterId)
        if (audioFileUri == null) {
            showFileNotFoundDialog(context, bookId, chapterId)
        } else {
            playerViewModel.initialize(context, chapterId)
            if (playImmediately) {
                playerViewModel.playAudio(audioFileUri!!)
            }
        }
    }

    val shouldNavigate by playerViewModel.shouldNavigateToNextChapter.collectAsState()
    val navigationInfo by playerViewModel.navigationInfo.collectAsState()

    fun navigateToChapter(nextChapterId: Int?) {
        nextChapterId?.let { nextId ->
            playerViewModel.savePlaybackState(playerViewModel.currentPosition.value.toLong())
            playerViewModel.stop()
            isLoadingNext = true
            navController.popBackStack()
            navController.navigate("player/$bookId/$nextId?playImmediately=true") {
                launchSingleTop = true
            }
        }
    }

    LaunchedEffect(shouldNavigate) {
        if (shouldNavigate) {
            playerViewModel.getNextChapterId()?.let { nextChapterId ->
                playerViewModel.resetNavigationState()
                navigateToChapter(nextChapterId)
            }
        }
    }

    ScreenLayout(
        navController = navController,
        showBackButton = true,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            audioFileUri?.let {
                PlaybackControls(
                    playbackState = playbackState,
                    onPlayPause = {
                        if (playbackState == PlaybackState.IDLE) {
                            playerViewModel.playAudio(it)
                        } else {
                            playerViewModel.togglePlayPause()
                        }
                    },
                    onRewind = { playerViewModel.seekRelative(-120000) },
                    onForward = { playerViewModel.seekRelative(120000) }
                )
            }
        }
    }
}

@Composable
fun PlaybackControls(
    playbackState: PlaybackState,
    onPlayPause: () -> Unit,
    onRewind: () -> Unit,
    onForward: () -> Unit
) {
    val iconSize = 240.dp  // Make all icons equal in size visually

    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onRewind,
            modifier = Modifier.size(iconSize).padding(end = iconSize/3)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_rewind),
                contentDescription = "Rewind",
                modifier = Modifier.fillMaxSize()

            )
        }

        IconButton(
            onClick = onPlayPause,
            modifier = Modifier.size(iconSize)
        ) {
            Icon(
                painter = painterResource(
                    when (playbackState) {
                        PlaybackState.PLAYING -> R.drawable.ic_pause
                        else -> R.drawable.ic_play
                    }
                ),
                contentDescription = if (playbackState == PlaybackState.PLAYING) "Pause" else "Play",
                modifier = Modifier.fillMaxSize()
            )
        }

        IconButton(
            onClick = onForward,
            modifier = Modifier.size(iconSize).padding(start = iconSize/3)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_forward),
                contentDescription = "Fast Forward",
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

fun showFileNotFoundDialog(context: Context, bookId: Int, chapterId: Int) {
    AlertDialog.Builder(context)
        .setTitle("Audio file not found")
        .setMessage("Audio file for Book ID: $bookId, Chapter ID: $chapterId not found.")
        .setCancelable(false)
        .setPositiveButton("OK") { _, _ -> }
        .show()
}

fun Int.toTimeString(): String {
    val minutes = this / 1000 / 60
    val seconds = this / 1000 % 60
    return String.format("%02d:%02d", minutes, seconds)
}
