package com.mukul.jan.audioplayer

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.*
import kotlinx.coroutines.Runnable
import java.util.Timer
import java.util.TimerTask
import kotlin.math.max

interface MediaPlayerCallbacks {
    fun prepare(url: String?): MediaPlayer?
    fun attachListener()
    fun player(): MediaPlayer?
    fun play()
    fun pause()
    fun playPause()
    fun release()
}

interface MediaPlayerListener {
    fun onPrepared(audioUrl: String?)
    fun onPlay()
    fun onPause()
    fun onProgress(max: Int, current: Int)
    fun onDuration(totalDuration: String, playedDuration: String)
    fun onBufferingUpdate(isBuffering: Boolean)
    fun onRelease()
}

class DefaultMediaPlayer private constructor(
    private var context: Context? = null,
    private var listener: MediaPlayerListener? = null,
) : MediaPlayerCallbacks {
    companion object {
        fun build(
            context: Context,
            listener: MediaPlayerListener?
        ): DefaultMediaPlayer {
            return DefaultMediaPlayer(
                context = context,
                listener = listener
            )
        }
    }

    init {
        initPlayer()
    }

    private var player: MediaPlayer? = null
    private fun initPlayer() {
        val audioAttrs = AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .build()
        player = MediaPlayer().apply {
            setAudioAttributes(audioAttrs)
        }
        attachListener()
        calculateProgressAndDuration()
    }

    override
    fun prepare(url: String?): MediaPlayer? {
        initPlayer()
        listener?.onPrepared(url)
        player?.let {
            it.setDataSource(url)
            it.prepareAsync()
        }
        return player
    }

    override fun attachListener() {
        player?.let {
            it.setOnPreparedListener {
                play()
            }
            it.setOnCompletionListener {
                listener?.onPause()
            }
            it.setOnBufferingUpdateListener { _, isBuffering ->
                if (isBuffering == 0) listener?.onBufferingUpdate(isBuffering = false)
                else listener?.onBufferingUpdate(isBuffering = true)
            }
        }
    }

    private var progressCalculationAborted: Boolean = false
    private fun calculateProgressAndDuration() {
        if(listener == null || player == null) return
        progressCalculationAborted = false

        val runnable = object : Runnable {
            override fun run() {
                if(!progressCalculationAborted) {
                    val maxProgress = player?.duration ?: 0
                    val currentProgress = player?.currentPosition ?: 0

                    val totalDuration = milliSecondsToTimer(maxProgress.toLong())
                    val playedDuration = milliSecondsToTimer(currentProgress.toLong())

                    listener?.onDuration(totalDuration = totalDuration, playedDuration = playedDuration)
                    listener?.onProgress(max = maxProgress, current = currentProgress)
                    Handler(Looper.myLooper()!!).postDelayed(this, 500)
                }
            }
        }
        Handler(Looper.myLooper()!!).postDelayed(runnable, 0)
    }

    override fun player(): MediaPlayer? {
        return player
    }

    override fun play() {
        progressCalculationAborted = false
        player?.start()
        listener?.onPlay()
    }

    override fun pause() {
        progressCalculationAborted = true
        player?.pause()
        listener?.onPause()
    }

    override fun playPause() {
        if (player?.isPlaying == true) {
            pause()
        } else {
            play()
        }
    }

    override fun release() {
        progressCalculationAborted = true
        player?.reset()
        listener?.onRelease()
        player = null
        context = null
    }
}

fun milliSecondsToTimer(milliseconds: Long): String {
    var finalTimerString = ""
    var secondsString = ""

    // Convert total duration into time
    val hours = (milliseconds / (1000 * 60 * 60)).toInt()
    val minutes = (milliseconds % (1000 * 60 * 60)).toInt() / (1000 * 60)
    val seconds = (milliseconds % (1000 * 60 * 60) % (1000 * 60) / 1000).toInt()
    // Add hours if there
    if (hours > 0) {
        finalTimerString = "$hours:"
    }

    // Prepending 0 to seconds if it is one digit
    secondsString = if (seconds < 10) {
        "0$seconds"
    } else {
        "" + seconds
    }
    finalTimerString = "$finalTimerString$minutes:$secondsString"

    // return timer string
    return finalTimerString
}