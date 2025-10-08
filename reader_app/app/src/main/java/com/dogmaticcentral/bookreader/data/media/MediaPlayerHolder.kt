package com.dogmaticcentral.bookreader.data.media

// MediaPlayerHolder.kt
import android.media.MediaPlayer
import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.IOException


fun fileSize(filePath: String): String {
    val file = File(filePath)
    val fileSizeInBytes = file.length()
    val fileSizeInMb = fileSizeInBytes / (1024.0 * 1024.0)
    val fileSizeStr = "%.2f MB".format(fileSizeInMb)
    return fileSizeStr
}

class MediaPlayerHolder(private val context: Context) {

    private val mediaPlayer: MediaPlayer = MediaPlayer().apply {
        setOnErrorListener { mp, what, extra ->
            Log.e("PLAYER", "Error $what/$extra")
            false
        }
    }
    private var currentUri: Uri? = null

    fun playAudio(
        audioUri: Uri,
        onPrepared: () -> Unit = {},
        onCompletion: () -> Unit = {}
    ) {

        try {
            if (currentUri == audioUri && mediaPlayer.isPlaying) {
                return
            }
            mediaPlayer.reset()
            mediaPlayer.setDataSource(context, audioUri)
            mediaPlayer.prepareAsync()
            mediaPlayer.setOnPreparedListener {
                onPrepared()
                it.start()
            }
            mediaPlayer.setOnCompletionListener {
                onCompletion()
            }
            currentUri = audioUri

        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    fun pause() {
        if (mediaPlayer.isPlaying) {
            mediaPlayer.pause()
        }
    }

    fun resume() {
        if (!mediaPlayer.isPlaying) {
            mediaPlayer.start()
        }
    }

    fun stop() {
        mediaPlayer.stop()
        mediaPlayer.reset()
        currentUri = null
    }

    fun isPlaying(): Boolean = mediaPlayer.isPlaying

    fun getCurrentPosition(): Int = mediaPlayer.currentPosition

    fun getDuration(): Int = mediaPlayer.duration

    fun seekTo(position: Int) {
        try {
            if (mediaPlayer.isPlaying || mediaPlayer.currentPosition > 0 || mediaPlayer.duration > 0) {
                Log.d("MediaPlayerHolder", "seekTo($position)")
                mediaPlayer.seekTo(position)
            } else {
                Log.w("MediaPlayerHolder", "seekTo() ignored – player not prepared yet")
            }
        } catch (e: IllegalStateException) {
            Log.e("MediaPlayerHolder", "seekTo() failed: ${e.message}")
        }
    }

    fun release() {
        mediaPlayer.release()
    }
}
