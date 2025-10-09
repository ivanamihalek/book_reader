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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

import com.dogmaticcentral.bookreader.LocalBookRepository
import com.dogmaticcentral.bookreader.R
import com.dogmaticcentral.bookreader.components.ScreenLayout
import com.dogmaticcentral.bookreader.data.getAudioContentUri
import com.dogmaticcentral.bookreader.viewmodel.PlaybackState
import com.dogmaticcentral.bookreader.viewmodel.PlayerViewModel
import com.dogmaticcentral.bookreader.viewmodel.PlayerViewModelFactory
import kotlinx.coroutines.delay
import android.util.Log
import androidx.compose.animation.core.animateFloatAsState
import kotlinx.coroutines.launch

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

    // Let's define the local context first
    val context = LocalContext.current
    val repository = LocalBookRepository.current
    val playbackState by playerViewModel.playbackState.collectAsState()
    var audioFileUri by remember { mutableStateOf<Uri?>(null) }
    val lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current
    var isLoadingNext by remember { mutableStateOf(false) }

   ///////////////////////////////////////////////////////////////
    suspend fun checkAndMarkChapterFinished() {
        try {
            val chapter = repository.getChapterById(chapterId)
            if (chapter == null) {
                Log.w("PlayerScreen", "No chapter found for ID $chapterId")
                return
            }

            val playTime = chapter.playTime
            val currentPosition = playerViewModel.currentPosition.value

            if (playTime <= 0) {
                Log.w("PlayerScreen", "Invalid playTime=$playTime for chapterId=$chapterId")
                return
            }

            val ratio = currentPosition.toFloat() / playTime.toFloat()

            if (ratio > 0.95f && !chapter.finishedPlaying) {
                Log.d(
                    "PlayerScreen",
                    "Marking chapterId=$chapterId finished (pos=$currentPosition, playTime=$playTime, ratio=$ratio)"
                )
                repository.updatePlayData(
                    chapterId = chapterId,
                    position = currentPosition.toLong(),
                    timeStopped = System.currentTimeMillis(),
                    finishedPlaying = true
                )
            } else {
                Log.v(
                    "PlayerScreen",
                    "Playback progress check: pos=$currentPosition / $playTime ($ratio), finished=${chapter.finishedPlaying}"
                )
            }
        } catch (e: Exception) {
            Log.e("PlayerScreen", "Error checking finished state: ${e.message}", e)
        }
    }



    ///////////////////////////////////////////////////////////////
    //  Lifecycle observer for saving playback state on pause/stop
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                playerViewModel.savePlaybackState(
                    playerViewModel.currentPosition.value.toLong(),
                    finishedPlaying = (playbackState == PlaybackState.COMPLETED))
            }
            // Check if it’s effectively finished
            playerViewModel.viewModelScope.launch {
                checkAndMarkChapterFinished()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            playerViewModel.savePlaybackState(playerViewModel.currentPosition.value.toLong(),
                finishedPlaying = (playbackState == PlaybackState.COMPLETED))
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    LaunchedEffect(playbackState) {
        if (playbackState == PlaybackState.COMPLETED ||
            playbackState == PlaybackState.PAUSED ||
            playbackState == PlaybackState.IDLE) {
            checkAndMarkChapterFinished()
        }
    }
    ///////////////////////////////////////////////////////////////
    // Coroutine for loading audio when book or chapter changes
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


    ///////////////////////////////////////////////////////////////
    // Navigation state
    val shouldNavigate by playerViewModel.shouldNavigateToNextChapter.collectAsState()
    //val navigationInfo by playerViewModel.navigationInfo.collectAsState()

    ///////////////////////////////////////////////////////////////
    // FUnction wrapping the actins needed to navigate to the next chapter
    fun navigateToChapter(nextChapterId: Int?) {
        nextChapterId?.let { nextId ->
            playerViewModel.savePlaybackState(
                playerViewModel.currentPosition.value.toLong(),
                finishedPlaying = (playbackState == PlaybackState.COMPLETED))
            playerViewModel.stop()
            isLoadingNext = true
            navController.popBackStack()
            navController.navigate("player/$bookId/$nextId?playImmediately=true") {
                launchSingleTop = true
            }
        }
    }

    ///////////////////////////////////////////////////////////////
    //  Lifecycle observer for tracking the changes in navigation state
    LaunchedEffect(shouldNavigate) {
        if (shouldNavigate) {
            playerViewModel.getNextChapterId()?.let { nextChapterId ->
                playerViewModel.resetNavigationState()
                navigateToChapter(nextChapterId)
            }
        }
    }

    ///////////////////////////////////////////////////////////////
    //  UI Layout
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
                        Log.d("PlayerScreen", "onPlayPause playbackState=$playbackState")
                        if (playbackState == PlaybackState.IDLE) {
                            playerViewModel.playAudio(it)
                        } else {
                            playerViewModel.togglePlayPause()
                        }
                    },
                    onRewind = { playerViewModel.seekRelative(-120000) },
                    onForward = {
                        playerViewModel.seekRelative(120000)
                        playerViewModel.viewModelScope.launch { checkAndMarkChapterFinished() }
                    }
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
    val iconSize = 240.dp
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope() // for launching coroutines
    var rewindPressed by remember { mutableStateOf(false) }
    var forwardPressed by remember { mutableStateOf(false) }

    val rewindScale by animateFloatAsState(if (rewindPressed) 0.9f else 1f)
    val forwardScale by animateFloatAsState(if (forwardPressed) 0.9f else 1f)

    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onRewind()
                rewindPressed = true
                coroutineScope.launch {
                    delay(100)
                    rewindPressed = false
                }
            },
            modifier = Modifier
                .size(iconSize)
                .padding(end = iconSize / 3)
                .graphicsLayer(scaleX = rewindScale, scaleY = rewindScale)
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
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onForward()
                forwardPressed = true
                coroutineScope.launch {
                    delay(100)
                    forwardPressed = false
                }
            },
            modifier = Modifier
                .size(iconSize)
                .padding(start = iconSize / 3)
                .graphicsLayer(scaleX = forwardScale, scaleY = forwardScale)
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
