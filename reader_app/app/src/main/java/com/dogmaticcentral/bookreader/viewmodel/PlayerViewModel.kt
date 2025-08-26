package com.dogmaticcentral.bookreader.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dogmaticcentral.bookreader.data.BookRepository
import com.dogmaticcentral.bookreader.data.media.MediaPlayerHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    private val _shouldNavigateToNextChapter = MutableStateFlow(false)
    val shouldNavigateToNextChapter: StateFlow<Boolean> = _shouldNavigateToNextChapter.asStateFlow()

    // Independent scope so DB writes aren’t killed with ViewModel
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Track completion
    private var chapterFinished = false

    fun initialize(context: Context, chapterId: Int) {
        if (!::mediaPlayerHolder.isInitialized) {
            mediaPlayerHolder = MediaPlayerHolder(context)
        }
        currentChapterId = chapterId
        loadNavigationInfo(chapterId)
        restoreLastPlayedPosition()
    }

    private fun loadNavigationInfo(chapterId: Int) {
        viewModelScope.launch {
            _navigationInfo.value = repository.getChapterNavigationInfo(chapterId)
        }
    }

    private fun restoreLastPlayedPosition() {
        currentChapterId?.let { chapterId ->
            viewModelScope.launch {
                val lastPosition = repository.getChapterById(chapterId)?.lastPlayedPosition ?: 0L
                val startPosition = (lastPosition.toLong() - 10000L).coerceAtLeast(0L).toInt()
                _currentPosition.value = startPosition
                if (::mediaPlayerHolder.isInitialized) {
                    mediaPlayerHolder.seekTo(startPosition)
                }
            }
        }
    }

    fun playAudio(audioUri: Uri) {
        mediaPlayerHolder.playAudio(
            audioUri = audioUri,
            onPrepared = {
                _playbackState.value = PlaybackState.PLAYING
                mediaPlayerHolder.seekTo(_currentPosition.value)
                startProgressUpdates()
            },
            onCompletion = {
                _playbackState.value = PlaybackState.COMPLETED
                chapterFinished = true
                // Use independent scope so it isn’t cancelled on ViewModel clear
                savePlaybackState(_currentPosition.value.toLong(), finishedPlaying = true)
                checkShouldNavigateToNextChapter()
            }
        )
    }

    fun seekRelative(deltaMillis: Int) {
        val newPosition = (_currentPosition.value + deltaMillis)
            .coerceAtLeast(0)
            .coerceAtMost(mediaPlayerHolder.getDuration())
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
}
