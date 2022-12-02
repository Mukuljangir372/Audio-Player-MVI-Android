package com.mukul.jan.audioplayer

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.provider.MediaStore.Audio
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class AudioService : Service() {
    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "ScogoAudioChannelId"
        private const val NOTIFICATION_CHANNEL_NAME = "ScogoAudioChannel"
        private const val NOTIFICATION_ID = 101
    }

    private lateinit var scope: CoroutineScope
    private lateinit var viewModel: AudioServiceViewModel

    private var player: DefaultMediaPlayer? = null

    private val binder: Binder = AudioServiceBinder()

    inner class AudioServiceBinder : Binder() {
        val service: AudioService get() = this@AudioService
    }

    override fun onCreate() {
        super.onCreate()
        scope = CoroutineScope(Dispatchers.Main)

        viewModel = ViewModelFactory.create().get(default = AudioServiceViewModel())
        player = DefaultMediaPlayer.build(
            context = this,
            object : MediaPlayerListener {
                override fun onPrepared(audioUrl: String?) {
                    viewModel.push(AudioServiceEvent.SetPlayerIdle(idle = false))
                    viewModel.push(AudioServiceEvent.SetAudioUrl(url = audioUrl.orEmpty()))
                }

                override fun onPlay() {
                    viewModel.push(AudioServiceEvent.Play)
                }

                override fun onPause() {
                    viewModel.push(AudioServiceEvent.Pause)
                }

                override fun onProgress(max: Int, current: Int) {
                    viewModel.push(
                        AudioServiceEvent.ChangeProgress(
                            max = max, current = current
                        )
                    )
                }

                override fun onDuration(totalDuration: String, playedDuration: String) {
                    viewModel.push(
                        AudioServiceEvent.ChangeDuration(
                            totalDuration = totalDuration,
                            playedDuration = playedDuration,
                        )
                    )
                }

                override fun onBufferingUpdate(isBuffering: Boolean) {
                    viewModel.push(AudioServiceEvent.SetBuffering(buffering = isBuffering))
                }

                override fun onRelease() {
                    viewModel.push(AudioServiceEvent.Pause)
                }
            }
        )

        consumeControllerEvents()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, getNotification())
        return START_STICKY
    }

    override fun onBind(p0: Intent?): IBinder {
        return binder
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        player?.release()
        player = null
    }

    private fun consumeControllerEvents() {
        scope.launch {
            viewModel.receiveControllerEventFlow().collectLatest {
                when(it) {
                    is AudioServiceControllerEvent.Prepare -> {
                        player?.prepare(it.url)
                        if(it.play) {
                            player?.play()
                        }
                    }
                    is AudioServiceControllerEvent.PlayPause -> {
                        player?.playPause()
                    }
                    is AudioServiceControllerEvent.SeekTo -> {
                        player?.player()?.seekTo(it.seek)
                    }
                }
            }
        }
    }

    @SuppressLint("NewApi")
    private fun createNotificationChannel(context: Context?) {
        if (context == null) return
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID, NOTIFICATION_CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = ""
        }
        notificationManager().createNotificationChannel(channel)
    }

    private fun notificationManager(): NotificationManager {
        return getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    private fun getNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)

        val pendingIntentFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 0, intent, pendingIntentFlag
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel(this)
        }

        return NotificationCompat
            .Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Title")
            .setContentText("Text")
            .setContentIntent(pendingIntent)
            .setStyle(androidx.media.app.NotificationCompat.MediaStyle())
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .build()
    }
}