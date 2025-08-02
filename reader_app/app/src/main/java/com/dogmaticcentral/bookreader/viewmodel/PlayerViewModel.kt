package com.dogmaticcentral.bookreader.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dogmaticcentral.bookreader.data.BookRepository
import com.dogmaticcentral.bookreader.data.media.MediaPlayerHolder
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

    private val _hasReachedEnd = MutableStateFlow(false)


    private val _navigationInfo = MutableStateFlow<BookRepository.ChapterNavigationInfo?>(null)
    val navigationInfo: StateFlow<BookRepository.ChapterNavigationInfo?> = _navigationInfo.asStateFlow()

    private val _shouldNavigateToNextChapter = MutableStateFlow(false)
    val shouldNavigateToNextChapter: StateFlow<Boolean> = _shouldNavigateToNextChapter.asStateFlow()

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
                // Start 10 seconds earlier, but not less than zero
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
                // Reset position and update time on completion
                savePlaybackState(_currentPosition.value.toLong())
                 checkShouldNavigateToNextChapter()
            },

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
            else -> { /* No-op for IDLE or COMPLETED */ }
        }
    }

    fun stop() {
        mediaPlayerHolder.stop()
        _playbackState.value = PlaybackState.IDLE
        savePlaybackState(_currentPosition.value.toLong())
       // _currentPosition.value = 0
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
        val timeStopped = System.currentTimeMillis()
        currentChapterId?.let { chapterId ->
            viewModelScope.launch {
                repository.updatePlayData(chapterId, position, timeStopped, finishedPlaying)
            }
        }
    }

    override fun onCleared() {
        savePlaybackState(_currentPosition.value.toLong())
        mediaPlayerHolder.release()
        super.onCleared()
    }
}
