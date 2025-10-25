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
import androidx.compose.ui.graphics.Color
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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SliderDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.tooling.preview.Preview
import kotlinx.coroutines.launch

@Composable
fun PlayerScreen(
    navController: NavHostController,
    bookId: Int,
    chapterId: Int,
    playImmediately: Boolean,
    fromPrevious: Boolean = false,
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
    var isReadyForUI by remember { mutableStateOf(false) }

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
            playbackState == PlaybackState.IDLE
        ) {
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
            // This is a suspend call. The LaunchedEffect will wait
            // for it to complete before moving on.
            playerViewModel.initialize(context, chapterId, ignoreLastPlayed = fromPrevious)
            // This will only be called AFTER initialize is fully done
            if (playImmediately) {
                playerViewModel.playAudio(audioFileUri!!)
            }
            isReadyForUI = true
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
                finishedPlaying = (playbackState == PlaybackState.COMPLETED)
            )
            playerViewModel.stop()
            isLoadingNext = true
            navController.popBackStack()
            navController.navigate("player/$bookId/$nextId?playImmediately=true&fromPrevious=true") {
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
    if (!isReadyForUI) {
         // Blank screen while loading
        Box(modifier = Modifier.fillMaxSize())

    } else {
        ScreenLayout(
            navController = navController,
            showBackButton = true,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp),
                verticalArrangement = Arrangement.Top,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val configuration = LocalConfiguration.current
                val screenHeight = configuration.screenHeightDp.dp
                val topOffset = 0.12f // 15% from top, adjust as needed
                val midSpaceFraction = 0.15f

                Spacer(modifier = Modifier.height(screenHeight * topOffset))
                // Playback controls
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
                        onForward = {
                            playerViewModel.seekRelative(120000)
                            playerViewModel.viewModelScope.launch { checkAndMarkChapterFinished() }
                        }
                    )
                }

                // Adjustable spacing between controls and slider
                Spacer(modifier = Modifier.height(screenHeight * midSpaceFraction))  // CHANGE THIS VALUE TO ADJUST DISTANCE

                // Position slider
                PositionSlider(
                    currentPosition = playerViewModel.currentPosition.collectAsState().value,
                    duration = playerViewModel.duration.collectAsState().value,
                    playbackState = playbackState,
                    onSeekStart = { playerViewModel.pauseForSeeking() },
                    onSeekEnd = { position ->
                        playerViewModel.seekTo(position)
                        playerViewModel.savePlaybackState(position.toLong())
                        playerViewModel.resumeAfterSeeking()
                    },
                    modifier = Modifier.fillMaxWidth(0.75f)
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

    val coroutineScope = rememberCoroutineScope() // for launching coroutines

    val context = LocalContext.current

    // Create MediaPlayer for click sound
    val clickSound = remember {
        android.media.MediaPlayer.create(context, R.raw.click)
    }

    // Clean up when composable is disposed
    DisposableEffect(Unit) {
        onDispose {
            clickSound?.release()
        }
    }

    fun playClickSound() {
        clickSound?.let {
            if (it.isPlaying) {
                it.seekTo(0)
            }
            it.start()
        }
    }


    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = {
                playClickSound()
                onRewind()
            },
            modifier = Modifier
                .size(iconSize)
                .padding(end = iconSize / 3)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_rewind),
                contentDescription = "Rewind",
                modifier = Modifier.fillMaxSize()
            )
        }

        IconButton(
            onClick = {
                onPlayPause()
            },
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
                playClickSound()
                onForward()
            },
            modifier = Modifier
                .size(iconSize)
                .padding(start = iconSize / 3)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_forward),
                contentDescription = "Fast Forward",
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PositionSlider(
    currentPosition: Int,
    duration: Int,
    playbackState: PlaybackState,
    onSeekStart: () -> Unit,
    onSeekEnd: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var isUserSeeking by remember { mutableStateOf(false) }
    var seekPosition by remember { mutableStateOf(0f) }

    // Define the valid maximum duration, ensuring it's at least 1f to prevent
    // an invalid range (e.g., 0f..0f) which can also cause crashes.
    val maxDuration = duration.toFloat().coerceAtLeast(1f)

    // Determine the position to display
    val rawPosition = if (isUserSeeking) seekPosition else currentPosition.toFloat()

    // **THE FIX:**
    // Coerce (clamp) the display position to always be within the valid range.
    val displayPosition = rawPosition.coerceIn(0f, maxDuration)

    androidx.compose.material3.Slider(
        value = displayPosition,
        onValueChange = { newValue ->
            if (!isUserSeeking) {
                isUserSeeking = true
                onSeekStart()
            }
            // The slider itself should only emit values within the range,
            // but coercing here is extra defense.
            seekPosition = newValue.coerceIn(0f, maxDuration)
        },
        onValueChangeFinished = {
            isUserSeeking = false
            // Also coerce the final seek position just to be safe.
            onSeekEnd(seekPosition.coerceIn(0f, maxDuration).toInt())
        },
        valueRange = 0f..maxDuration, // Use the sanitized maxDuration
        colors = SliderDefaults.colors(
            thumbColor = Color.Black,
            activeTrackColor = Color.Black,
            inactiveTrackColor = Color.Blue
        ),
        thumb = {
            val circleShape =  androidx.compose.foundation.shape.CircleShape
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(Color.Black, shape = circleShape)
             )
        },
        track = { sliderState ->
            val shape = RoundedCornerShape(12.dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)
                    .clip(shape)
                    .border(
                        width = 6.dp,
                        color = Color.Black,
                        shape = shape
                    )
                    .background(Color.White, shape)
            ) {
                SliderDefaults.Track(
                    sliderState = sliderState,
                    modifier = Modifier.fillMaxSize(),
                    colors = SliderDefaults.colors(
                        activeTrackColor = Color.Black,
                        inactiveTrackColor = Color.Blue.copy(alpha = 0.5f)
                    )
                )
            }
        },
        modifier = modifier
    )
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
