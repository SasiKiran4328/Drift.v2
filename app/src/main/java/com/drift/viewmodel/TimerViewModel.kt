package com.drift.viewmodel

import android.content.Context
import androidx.lifecycle.*
import com.drift.service.BluetoothService
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

sealed class TimerState {
    object Idle : TimerState()
    object Running : TimerState()
    object Finished : TimerState()
}

class TimerViewModel : ViewModel() {
    private val _countdown = MutableLiveData<String>("00:00")
    val countdown: LiveData<String> = _countdown

    private val _isRunning = MutableLiveData<Boolean>(false)
    val isRunning: LiveData<Boolean> = _isRunning

    private val _progress = MutableLiveData<Int>(0)
    val progress: LiveData<Int> = _progress

    private val _timerState = MutableLiveData<TimerState>(TimerState.Idle)
    val timerState: LiveData<TimerState> = _timerState

    private var timerMillis: Long = 15 * 60 * 1000L
    private var autoDisconnectBluetooth = false
    private var autoDisconnectHotspot = false
    private var timerJob: Job? = null
    private var totalMillis: Long = timerMillis

    fun setAutoDisconnectBluetooth(enabled: Boolean) {
        autoDisconnectBluetooth = enabled
    }

    fun setAutoDisconnectHotspot(enabled: Boolean) {
        autoDisconnectHotspot = enabled
    }

    fun setTimerOption(option: String) {
        timerMillis = when (option) {
            "15 min" -> 15 * 60 * 1000L
            "30 min" -> 30 * 60 * 1000L
            "60 min" -> 60 * 60 * 1000L
            else -> 15 * 60 * 1000L // Default or custom dialog
        }
        totalMillis = timerMillis
        _progress.value = 0
        _countdown.value = formatTime(timerMillis)
    }

    fun setCustomTimer(millis: Long) {
        timerMillis = millis
        totalMillis = timerMillis
        _progress.value = 0
        _countdown.value = formatTime(timerMillis)
    }

    fun startTimer(context: Context) {
        _isRunning.value = true
        _timerState.value = TimerState.Running
        timerJob?.cancel()
        timerJob = MainScope().launch {
            var millisLeft = timerMillis
            while (millisLeft > 0 && _isRunning.value == true) {
                _countdown.value = formatTime(millisLeft)
                _progress.value = ((totalMillis - millisLeft) * 100 / totalMillis).toInt()
                delay(1000)
                millisLeft -= 1000
            }
            _countdown.value = "00:00"
            _progress.value = 100
            _isRunning.value = false
            _timerState.value = TimerState.Finished
            BluetoothService.start(context, timerMillis, autoDisconnectBluetooth, autoDisconnectHotspot)
        }
    }

    fun stopTimer(context: Context) {
        _isRunning.value = false
        _timerState.value = TimerState.Idle
        timerJob?.cancel()
        BluetoothService.stop(context)
        _progress.value = 0
        _countdown.value = formatTime(timerMillis)
    }

    private fun formatTime(millis: Long): String {
        val totalSeconds = millis / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }
} 