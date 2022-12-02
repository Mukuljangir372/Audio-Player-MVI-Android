package com.mukul.jan.audioplayer

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlin.math.max

sealed class AudioServiceControllerEvent {
    data class Prepare(val url: String, val play: Boolean): AudioServiceControllerEvent()
    object PlayPause: AudioServiceControllerEvent()
    data class SeekTo(val seek: Int): AudioServiceControllerEvent()
}

sealed class AudioServiceEvent {
    object Play : AudioServiceEvent()
    object Pause : AudioServiceEvent()

    data class SetPlayerIdle(val idle: Boolean) : AudioServiceEvent()
    data class SetBuffering(val buffering: Boolean) : AudioServiceEvent()
    data class SetAudioUrl(val url: String) : AudioServiceEvent()

    data class ChangeDuration(val totalDuration: String, val playedDuration: String) :
        AudioServiceEvent()

    data class ChangeProgress(val max: Int, val current: Int) : AudioServiceEvent()
}

data class AudioServiceUiState(
    val isPlayerIdle: Boolean,
    val isBuffering: Boolean,
    val isPlaying: Boolean,
    val audioUrl: String,
    val totalDuration: String,
    val playedDuration: String,
    val currentProgress: Int,
    val maxProgress: Int,
) {
    companion object {
        val EMPTY = AudioServiceUiState(
            isPlayerIdle = true,
            isBuffering = false,
            isPlaying = false,
            audioUrl = "",
            totalDuration = "",
            playedDuration = "",
            currentProgress = 0,
            maxProgress = 0
        )
    }
}

class AudioServiceViewModel : ViewModel() {
    private val controllerEventChannel = Channel<AudioServiceControllerEvent>(Channel.UNLIMITED)
    private val inputChannel = Channel<AudioServiceEvent>(Channel.UNLIMITED)

    private val _outputState = MutableLiveData<AudioServiceUiState>()
    val outputState: LiveData<AudioServiceUiState> get() = _outputState

    fun receiveControllerEventFlow() = controllerEventChannel.receiveAsFlow()

    init {
        handleInputs()
    }

    private fun updateOutputState(newState: AudioServiceUiState) {
        _outputState.value = newState
    }

    fun push(event: AudioServiceEvent) {
        viewModelScope.launch {
            inputChannel.send(event)
        }
    }

    fun pushToController(event: AudioServiceControllerEvent) {
        viewModelScope.launch {
            controllerEventChannel.send(event)
        }
    }

    private fun handleInputs() {
        viewModelScope.launch {
            inputChannel.consumeAsFlow()
                .collectLatest { event ->
                    updateOutputState(
                        newState = transformEventToState(
                            event = event,
                            state = outputState.value ?: AudioServiceUiState.EMPTY
                        )
                    )
                }
        }
    }

    private fun transformEventToState(
        event: AudioServiceEvent,
        state: AudioServiceUiState,
    ): AudioServiceUiState {
        return when (event) {
            is AudioServiceEvent.Play -> {
                state.copy(isPlaying = true)
            }
            is AudioServiceEvent.Pause -> {
                state.copy(isPlaying = false)
            }
            is AudioServiceEvent.SetPlayerIdle -> {
                state.copy(isPlayerIdle = event.idle)
            }
            is AudioServiceEvent.SetBuffering -> {
                state.copy(isBuffering = event.buffering)
            }
            is AudioServiceEvent.SetAudioUrl -> {
                state.copy(audioUrl = event.url)
            }
            is AudioServiceEvent.ChangeDuration -> {
                state.copy(
                    totalDuration = event.totalDuration,
                    playedDuration = event.playedDuration
                )
            }
            is AudioServiceEvent.ChangeProgress -> {
                state.copy(maxProgress = event.max, currentProgress = event.current)
            }
        }
    }
}




















