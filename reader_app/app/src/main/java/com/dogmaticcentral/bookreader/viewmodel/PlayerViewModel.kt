package com.dogmaticcentral.bookreader.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dogmaticcentral.bookreader.data.BookRepository
import com.dogmaticcentral.bookreader.data.media.MediaPlayerHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class PlaybackState {
    IDLE, PLAYING, PAUSED, COMPLETED
}

class PlayerViewModel(
    application: Application,
    private val repository: BookRepository
) : AndroidViewModel(application) {

    private lateinit var mediaPlayerHolder: MediaPlayerHolder

    private val _playbackState = MutableStateFlow(PlaybackState.IDLE)
    val playbackState: StateFlow<PlaybackState> = _playbackState

    private val _currentPosition = MutableStateFlow(0)
    val currentPosition: StateFlow<Int> = _currentPosition

    private var currentChapterId: Int? = null

    private val _navigationInfo = MutableStateFlow<BookRepository.ChapterNavigationInfo?>(null)
    val navigationInfo: StateFlow<BookRepository.ChapterNavigationInfo?> = _navigationInfo.asStateFlow()
    val duration: StateFlow<Int> = navigationInfo.map { it?.duration ?: 0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    private val _shouldNavigateToNextChapter = MutableStateFlow(false)
    val shouldNavigateToNextChapter: StateFlow<Boolean> = _shouldNavigateToNextChapter.asStateFlow()

    // Independent scope so DB writes aren’t killed with ViewModel
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Track completion
    private var chapterFinished = false

    suspend fun initialize(context: Context, chapterId: Int, ignoreLastPlayed: Boolean = false) {
        if (!::mediaPlayerHolder.isInitialized) {
            mediaPlayerHolder = MediaPlayerHolder(context)
        }
        currentChapterId = chapterId

        loadNavigationInfo(chapterId)
        if (ignoreLastPlayed) {
            // when we come from the previous chapter, we want to keep playing in order
            // that is, start the next chapter from 0
            // not from where we were three days ago
            _currentPosition.value = 0

        } else {
            restoreLastPlayedPosition()
        }
        Log.d("PlayerViewModel", "initialize() done")
    }

    private suspend fun loadNavigationInfo(chapterId: Int) {
        _navigationInfo.value = repository.getChapterNavigationInfo(chapterId)
    }

    private fun debugSeekTo(label: String, position: Int) {
        Log.d("PlayerViewModel", ">>> SEEKTO[$label] pos=$position, " +
            "mediaPlayerHolder.isInitialized=${::mediaPlayerHolder.isInitialized}")
        try {
            mediaPlayerHolder.seekTo(position)
            Log.d("PlayerViewModel", "<<< SEEKTO[$label] completed successfully")
        } catch (e: Exception) {
            Log.e("PlayerViewModel", "!!! SEEKTO[$label] threw ${e.message}", e)
        }
    }

    private suspend fun restoreLastPlayedPosition() {
        currentChapterId?.let { chapterId ->
            val lastPosition = repository.getChapterById(chapterId)?.lastPlayedPosition ?: 0L
            val startPosition = (lastPosition.toLong() - 10000L).coerceAtLeast(0L).toInt()
            _currentPosition.value = startPosition
        }
    }

    fun playAudio(audioUri: Uri) {
        mediaPlayerHolder.playAudio(
            audioUri = audioUri,
            onPrepared = {
                val restorePos = _currentPosition.value
                mediaPlayerHolder.seekTo(restorePos)
                _playbackState.value = PlaybackState.PLAYING
                startProgressUpdates()
            },
            onCompletion = {
                _playbackState.value = PlaybackState.COMPLETED
                chapterFinished = true
                savePlaybackState(_currentPosition.value.toLong(), finishedPlaying = true)
                checkShouldNavigateToNextChapter()
            }
        )
    }

    fun seekRelative(deltaMillis: Int) {
        val newPosition = (_currentPosition.value + deltaMillis)
            .coerceAtLeast(0)
            .coerceAtMost(duration.value)
        mediaPlayerHolder.seekTo(newPosition)
        _currentPosition.value = newPosition
    }

    private fun checkShouldNavigateToNextChapter() {
        viewModelScope.launch {
            _navigationInfo.value?.let { info ->
                if (!info.isLastChapter) {
                    _shouldNavigateToNextChapter.value = true
                }
            }
        }
    }

    fun resetNavigationState() {
        _shouldNavigateToNextChapter.value = false
    }

    fun getNextChapterId(): Int? {
        return _navigationInfo.value?.nextChapterId
    }

    fun togglePlayPause() {
        when (playbackState.value) {
            PlaybackState.PLAYING -> {
                mediaPlayerHolder.pause()
                _playbackState.value = PlaybackState.PAUSED
                savePlaybackState(_currentPosition.value.toLong())
            }
            PlaybackState.PAUSED -> {
                mediaPlayerHolder.resume()
                _playbackState.value = PlaybackState.PLAYING
                startProgressUpdates()
            }
            else -> {}
        }
    }

    fun stop() {
        mediaPlayerHolder.stop()
        _playbackState.value = PlaybackState.IDLE
        savePlaybackState(_currentPosition.value.toLong())
    }

    fun seekTo(position: Int) {
        mediaPlayerHolder.seekTo(position)
        _currentPosition.value = position
    }

    private fun startProgressUpdates() {
        viewModelScope.launch {
            while (playbackState.value == PlaybackState.PLAYING) {
                _currentPosition.value = mediaPlayerHolder.getCurrentPosition()
                delay(1000)
            }
        }
    }

     fun savePlaybackState(position: Long, finishedPlaying: Boolean = false) {
        currentChapterId?.let { chapterId ->
            ioScope.launch {
                repository.updatePlayData(chapterId, position, System.currentTimeMillis(), finishedPlaying)
            }
        }
    }

    override fun onCleared() {
        savePlaybackState(_currentPosition.value.toLong(), finishedPlaying = chapterFinished)
        mediaPlayerHolder.release()
        super.onCleared()
    }


    private var wasPlayingBeforeSeek = false

    fun pauseForSeeking() {
        wasPlayingBeforeSeek = (playbackState.value == PlaybackState.PLAYING)
        if (wasPlayingBeforeSeek) {
            mediaPlayerHolder.pause()
            _playbackState.value = PlaybackState.PAUSED
        }
    }

    fun resumeAfterSeeking() {
        if (wasPlayingBeforeSeek) {
            mediaPlayerHolder.resume()
            _playbackState.value = PlaybackState.PLAYING
            startProgressUpdates()
        }
    }

}
