package com.drift.viewmodel

// Android imports for context and lifecycle management
import android.content.Context
import androidx.lifecycle.*

// Kotlin coroutines for efficient async operations
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// Local service imports
import com.drift.service.BluetoothService
import com.drift.service.HotspotService

/**
 * Timer states for the application
 */
sealed class TimerState {
    object Idle : TimerState()
    object Running : TimerState()
    object Paused : TimerState()
    object Finished : TimerState()
}

/**
 * TimerViewModel - Manages timer state and business logic
 * 
 * Features:
 * - Separate timers for Bluetooth and Hotspot
 * - StateFlow for reactive UI updates
 * - Coroutines for efficient timer operations
 * - Error handling and validation
 * - Scalable architecture for millions of users
 */
class TimerViewModel : ViewModel() {
    
    companion object {
        private const val DEFAULT_TIMER = 15 * 60 * 1000L // 15 minutes
        private const val MAX_TIMER = 24 * 60 * 60 * 1000L // 24 hours
        private const val MIN_TIMER = 1000L // 1 second
    }
    
    // StateFlow for reactive UI updates (more efficient than LiveData)
    private val _countdown = MutableStateFlow("00:00")
    val countdown: StateFlow<String> = _countdown.asStateFlow()
    
    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()
    
    private val _progress = MutableStateFlow(0)
    val progress: StateFlow<Int> = _progress.asStateFlow()
    
    private val _timerState = MutableStateFlow<TimerState>(TimerState.Idle)
    val timerState: StateFlow<TimerState> = _timerState.asStateFlow()
    
    private val _remainingTime = MutableStateFlow(0L)
    val remainingTime: StateFlow<Long> = _remainingTime.asStateFlow()
    
    // Timer configurations
    private var bluetoothTimerMillis: Long = DEFAULT_TIMER
    private var hotspotTimerMillis: Long = DEFAULT_TIMER
    private var autoDisconnectBluetooth = false
    private var autoDisconnectHotspot = false
    
    // Coroutine management
    private var timerJob: Job? = null
    private var totalMillis: Long = bluetoothTimerMillis
    private var isPaused = false
    private var pausedTimeRemaining: Long = 0
    
    // Performance optimization
    private val timerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    init {
        _countdown.value = formatTime(bluetoothTimerMillis)
        _remainingTime.value = bluetoothTimerMillis
    }
    
    /**
     * Set Bluetooth auto-disconnect feature
     * @param enabled Whether to enable the feature
     */
    fun setAutoDisconnectBluetooth(enabled: Boolean) {
        autoDisconnectBluetooth = enabled
    }
    
    /**
     * Set Hotspot auto-disconnect feature
     * @param enabled Whether to enable the feature
     */
    fun setAutoDisconnectHotspot(enabled: Boolean) {
        autoDisconnectHotspot = enabled
    }
    
    /**
     * Set Bluetooth timer from preset options
     * @param option Preset timer option
     */
    fun setBluetoothTimerOption(option: String) {
        bluetoothTimerMillis = when (option) {
            "15 min" -> 15 * 60 * 1000L
            "30 min" -> 30 * 60 * 1000L
            "60 min" -> 60 * 60 * 1000L
            "90 min" -> 90 * 60 * 1000L
            "120 min" -> 120 * 60 * 1000L
            else -> DEFAULT_TIMER
        }
        resetTimer()
    }
    
    /**
     * Set Hotspot timer from preset options
     * @param option Preset timer option
     */
    fun setHotspotTimerOption(option: String) {
        hotspotTimerMillis = when (option) {
            "15 min" -> 15 * 60 * 1000L
            "30 min" -> 30 * 60 * 1000L
            "60 min" -> 60 * 60 * 1000L
            "90 min" -> 90 * 60 * 1000L
            "120 min" -> 120 * 60 * 1000L
            else -> DEFAULT_TIMER
        }
        resetTimer()
    }
    
    /**
     * Set custom Bluetooth timer with validation
     * @param millis Timer duration in milliseconds
     */
    fun setCustomBluetoothTimer(millis: Long) {
        if (isValidTimer(millis)) {
            bluetoothTimerMillis = millis
            resetTimer()
        }
    }
    
    /**
     * Set custom Hotspot timer with validation
     * @param millis Timer duration in milliseconds
     */
    fun setCustomHotspotTimer(millis: Long) {
        if (isValidTimer(millis)) {
            hotspotTimerMillis = millis
            resetTimer()
        }
    }
    
    /**
     * Start the timer with optimized coroutine execution
     * @param context Application context for service calls
     */
    fun startTimer(context: Context) {
        if (_isRunning.value == true) return
        
        _isRunning.value = true
        _timerState.value = TimerState.Running
        isPaused = false
        
        val startTime = if (pausedTimeRemaining > 0) pausedTimeRemaining else bluetoothTimerMillis
        _remainingTime.value = startTime
        
        // Cancel any existing timer job
        timerJob?.cancel()
        
        // Start new timer with optimized coroutine
        timerJob = timerScope.launch {
            var millisLeft = startTime
            
            while (millisLeft > 0 && _isRunning.value && !isPaused) {
                _countdown.value = formatTime(millisLeft)
                _remainingTime.value = millisLeft
                _progress.value = ((totalMillis - millisLeft) * 100 / totalMillis).toInt()
                
                // Use delay with try-catch for better error handling
                try {
                    delay(1000)
                } catch (e: CancellationException) {
                    break
                }
                
                millisLeft -= 1000
            }
            
            when {
                millisLeft <= 0 -> {
                    // Timer finished
                    _countdown.value = "00:00"
                    _progress.value = 100
                    _isRunning.value = false
                    _timerState.value = TimerState.Finished
                    _remainingTime.value = 0
                    
                    // Start services in background
                    launch(Dispatchers.IO) {
                        if (autoDisconnectBluetooth) {
                            BluetoothService.start(context, bluetoothTimerMillis, true)
                        }
                        if (autoDisconnectHotspot) {
                            HotspotService.start(context, hotspotTimerMillis, true)
                        }
                    }
                }
                isPaused -> {
                    // Timer paused
                    pausedTimeRemaining = millisLeft
                    _timerState.value = TimerState.Paused
                }
            }
        }
    }
    
    /**
     * Pause the currently running timer
     */
    fun pauseTimer() {
        if (_isRunning.value == true && !isPaused) {
            isPaused = true
            _timerState.value = TimerState.Paused
        }
    }
    
    /**
     * Resume the paused timer
     * @param context Application context for service calls
     */
    fun resumeTimer(context: Context) {
        if (isPaused) {
            isPaused = false
            startTimer(context)
        }
    }
    
    /**
     * Stop the timer and cleanup resources
     * @param context Application context for service calls
     */
    fun stopTimer(context: Context) {
        _isRunning.value = false
        _timerState.value = TimerState.Idle
        isPaused = false
        pausedTimeRemaining = 0
        
        // Cancel timer job
        timerJob?.cancel()
        timerJob = null
        
        // Stop services in background
        CoroutineScope(Dispatchers.IO).launch {
            try {
                BluetoothService.stop(context)
                HotspotService.stop(context)
            } catch (e: Exception) {
                // Log error but don't crash
            }
        }
        
        resetTimer()
    }
    
    /**
     * Reset timer to initial state
     */
    fun resetTimer() {
        totalMillis = bluetoothTimerMillis
        _progress.value = 0
        _countdown.value = formatTime(bluetoothTimerMillis)
        _remainingTime.value = bluetoothTimerMillis
        pausedTimeRemaining = 0
    }
    
    /**
     * Get current settings for UI display
     * @return Map of current settings
     */
    fun getCurrentSettings(): Map<String, Any> {
        return mapOf(
            "bluetoothTimer" to bluetoothTimerMillis,
            "hotspotTimer" to hotspotTimerMillis,
            "autoDisconnectBluetooth" to autoDisconnectBluetooth,
            "autoDisconnectHotspot" to autoDisconnectHotspot
        )
    }
    
    /**
     * Check if timer is currently active
     * @return true if timer is running or paused
     */
    fun isTimerActive(): Boolean {
        return _isRunning.value || isPaused
    }
    
    /**
     * Get Bluetooth timer duration
     * @return Timer duration in milliseconds
     */
    fun getBluetoothTimerMillis(): Long = bluetoothTimerMillis
    
    /**
     * Get Hotspot timer duration
     * @return Timer duration in milliseconds
     */
    fun getHotspotTimerMillis(): Long = hotspotTimerMillis
    
    /**
     * Validate timer duration
     * @param millis Timer duration to validate
     * @return true if valid, false otherwise
     */
    private fun isValidTimer(millis: Long): Boolean {
        return millis in MIN_TIMER..MAX_TIMER
    }
    
    /**
     * Format time for display
     * @param millis Time in milliseconds
     * @return Formatted time string
     */
    private fun formatTime(millis: Long): String {
        val totalSeconds = millis / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        
        return when {
            hours > 0 -> String.format("%02d:%02d:%02d", hours, minutes, seconds)
            else -> String.format("%02d:%02d", minutes, seconds)
        }
    }
    
    /**
     * Cleanup resources when ViewModel is destroyed
     */
    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        timerScope.cancel()
    }
} 