package com.mukul.jan.audioplayer

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

class MainActivity : AppCompatActivity() {
    private var audioService: AudioService? = null
    private var serviceBounded: Boolean = false
    private lateinit var viewModel: AudioServiceViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelFactory.create().get(default = AudioServiceViewModel())

        setContentView(R.layout.activity_main)
        bindServiceConnection()
        consumeUiState()

        findViewById<MaterialButton>(R.id.playPauseBtn).setOnClickListener {
            tryStartService()
        }
    }

    private fun consumeUiState() {
        viewModel.outputState.observe(this@MainActivity) {
            renderUi(it)
        }
    }

    private fun renderUi(state: AudioServiceUiState) {
        val playedDurationView = findViewById<TextView>(R.id.playedDuration)
        val totalDurationView = findViewById<TextView>(R.id.totalDuration)
        val seekbar = findViewById<SeekBar>(R.id.seekbar)
        val playPauseBtn = findViewById<MaterialButton>(R.id.playPauseBtn)

        playedDurationView.text = state.playedDuration
        totalDurationView.text = state.totalDuration
        playPauseBtn.text = if (state.isPlaying) "Pause" else "Play"
        seekbar.progress = state.currentProgress
        seekbar.max = state.maxProgress

        seekbar.setOnSeekBarChangeListener(object : OnSeekBarChangeListener{
            override fun onProgressChanged(p0: SeekBar?, p1: Int, p2: Boolean) {}
            override fun onStartTrackingTouch(p0: SeekBar?) {}
            override fun onStopTrackingTouch(p0: SeekBar?) {
                viewModel.pushToController(AudioServiceControllerEvent.SeekTo(p0?.progress ?: 0))
            }
        })
    }

    private fun tryStartService() {
        val url = "https://scogo-grafana-dashboard-backup.s3.ap-south-1.amazonaws.com/Scogo+_+Scan+%26+Book+an+Engineer+!.mp3"

        val state = viewModel.outputState.value ?: AudioServiceUiState.EMPTY
        if(state.isPlayerIdle) {
            startService()
            viewModel.pushToController(AudioServiceControllerEvent.Prepare(url, true))
        } else {
            viewModel.pushToController(AudioServiceControllerEvent.PlayPause)
        }
    }

    private fun startService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(audioServiceIntent())
        } else {
            startService(audioServiceIntent())
        }
    }

    private fun bindServiceConnection() {
        bindService(audioServiceIntent(), serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun audioServiceIntent(): Intent {
        return Intent(this, AudioService::class.java)
    }

    private var serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(p0: ComponentName?, p1: IBinder?) {
            audioService = (p1 as? AudioService.AudioServiceBinder)?.service
            serviceBounded = true
        }

        override fun onServiceDisconnected(p0: ComponentName?) {
            serviceBounded = false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unbindService(serviceConnection)
        audioService = null
    }

}