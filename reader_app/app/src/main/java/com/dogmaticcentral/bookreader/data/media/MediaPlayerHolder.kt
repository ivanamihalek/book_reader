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
    private var  currentUri: Uri? = null

    fun playAudio(audioUri: Uri,
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
        mediaPlayer.seekTo(position)
    }

    fun release() {
        mediaPlayer.release()
    }
}