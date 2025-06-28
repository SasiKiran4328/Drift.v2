package com.drift

// Core Android imports for UI and system functionality
import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast

// AndroidX imports for modern Android development
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope

// Kotlin coroutines for efficient async operations
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

// Local imports for our app components
import com.drift.databinding.ActivityMainBinding
import com.drift.viewmodel.TimerViewModel
import com.drift.ads.AdMobManager

/**
 * MainActivity - The primary UI controller for the Drift app
 * 
 * This activity manages:
 * - User interface for Bluetooth and Hotspot timer controls
 * - Permission handling for Bluetooth and WiFi access
 * - Timer state management through ViewModel
 * - Google Ads integration
 * - Theme switching (Dark/Light)
 * - User interactions and feedback
 */
class MainActivity : AppCompatActivity() {

    // ViewModel for managing timer state and business logic
    private val timerViewModel: TimerViewModel by viewModels()
    
    // AdMob manager for handling Google Ads
    private lateinit var adMobManager: AdMobManager
    
    // View binding for accessing UI elements
    private lateinit var binding: ActivityMainBinding

    companion object {
        // Request code for permission handling
        private const val PERMISSION_REQUEST_CODE = 100
        
        // All required permissions for the app to function properly
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.BLUETOOTH_CONNECT,    // For Bluetooth device control
            Manifest.permission.BLUETOOTH_ADMIN,      // For Bluetooth adapter control
            Manifest.permission.ACCESS_FINE_LOCATION, // Required for Bluetooth scanning
            Manifest.permission.ACCESS_WIFI_STATE,    // For WiFi state monitoring
            Manifest.permission.CHANGE_WIFI_STATE     // For Hotspot control
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply theme before super.onCreate for proper styling
        setTheme(R.style.Theme_Drift)
        super.onCreate(savedInstanceState)
        
        // Initialize view binding for type-safe UI access
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Setup all app components
        setupUI()
        setupObservers()
        setupClickListeners()
        setupAdMob()
        setupThemeToggle()
    }

    /**
     * Initialize the user interface components
     * Sets up timer dropdown options and initial UI state
     */
    private fun setupUI() {
        // Timer options for both Bluetooth and Hotspot features
        val timerOptions = arrayOf("15 min", "30 min", "60 min", "90 min", "120 min", "Custom")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, timerOptions)
        binding.timerDropdown.adapter = adapter
    }

    /**
     * Setup StateFlow observers for reactive UI updates
     * Uses lifecycleScope for efficient coroutine management
     */
    private fun setupObservers() {
        // Observe countdown timer display
        lifecycleScope.launch {
            timerViewModel.countdown.collectLatest { time ->
                binding.countdownText.text = time
            }
        }

        // Observe timer running state for button text updates
        lifecycleScope.launch {
            timerViewModel.isRunning.collectLatest { running ->
                binding.startStopButton.text = if (running) getString(R.string.stop) else getString(R.string.start)
                binding.startStopButton.isEnabled = true
            }
        }

        // Observe progress bar updates
        lifecycleScope.launch {
            timerViewModel.progress.collectLatest { progress ->
                binding.timerProgressBar.progress = progress
            }
        }

        // Observe timer state for handling completion and other states
        lifecycleScope.launch {
            timerViewModel.timerState.collectLatest { state ->
                when (state) {
                    is TimerViewModel.TimerState.Finished -> {
                        showTimerFinishedDialog()
                    }
                    is TimerViewModel.TimerState.Idle -> {
                        binding.startStopButton.isEnabled = true
                    }
                    else -> {}
                }
            }
        }
    }

    /**
     * Setup click listeners for user interactions
     * Handles Bluetooth/Hotspot toggles and timer controls
     */
    private fun setupClickListeners() {
        // Bluetooth toggle with individual timer setting dialog
        binding.toggleBluetooth.setOnCheckedChangeListener { _, isChecked ->
            timerViewModel.setAutoDisconnectBluetooth(isChecked)
            if (isChecked) {
                // Show timer selection dialog when Bluetooth is enabled
                showBluetoothTimerDialog()
            } else {
                showToast("Bluetooth auto-disconnect disabled")
            }
        }

        // Hotspot toggle with individual timer setting dialog
        binding.toggleHotspot.setOnCheckedChangeListener { _, isChecked ->
            timerViewModel.setAutoDisconnectHotspot(isChecked)
            if (isChecked) {
                // Show timer selection dialog when Hotspot is enabled
                showHotspotTimerDialog()
            } else {
                showToast("Hotspot auto-disconnect disabled")
            }
        }

        // Start/Stop button with permission handling and timer control
        binding.startStopButton.setOnClickListener {
            if (timerViewModel.isRunning.value) {
                // Stop timer and services
                timerViewModel.stopTimer(this)
                showToast("Timer stopped")
            } else {
                // Check permissions before starting timer
                if (checkPermissions()) {
                    timerViewModel.startTimer(this)
                    showToast("Timer started")
                } else {
                    requestPermissions()
                }
            }
        }
    }

    /**
     * Initialize Google AdMob for monetization
     * Sets up banner ads and interstitial ads
     */
    private fun setupAdMob() {
        adMobManager = AdMobManager(this)
        
        // Load banner ad in the bottom of the screen
        adMobManager.loadBannerAd(binding.adView)
        
        // Preload interstitial ad for better user experience
        adMobManager.preloadInterstitialAd()
        
        // Show interstitial ad when timer finishes (optional)
        adMobManager.setInterstitialAdListener {
            // Ad was closed, can perform additional actions here
        }
    }

    /**
     * Setup theme toggle functionality
     * Allows users to switch between light and dark themes
     */
    private fun setupThemeToggle() {
        // Add theme toggle to menu or settings
        // This can be implemented as a menu item or settings option
        // For now, we'll use system theme by default
    }

    /**
     * Toggle between light and dark themes
     */
    private fun toggleTheme() {
        val currentMode = AppCompatDelegate.getDefaultNightMode()
        val newMode = when (currentMode) {
            AppCompatDelegate.MODE_NIGHT_YES -> AppCompatDelegate.MODE_NIGHT_NO
            else -> AppCompatDelegate.MODE_NIGHT_YES
        }
        AppCompatDelegate.setDefaultNightMode(newMode)
    }

    /**
     * Check if all required permissions are granted
     * @return true if all permissions are granted, false otherwise
     */
    private fun checkPermissions(): Boolean {
        return REQUIRED_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Request all required permissions from user
     * Shows system permission dialog
     */
    private fun requestPermissions() {
        ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, PERMISSION_REQUEST_CODE)
    }

    /**
     * Handle permission request results
     * Starts timer if permissions granted, shows error otherwise
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                // All permissions granted, start timer
                timerViewModel.startTimer(this)
                showToast("Timer started")
            } else {
                // Some permissions denied, show error
                showErrorDialog("Permissions required to start timer")
            }
        }
    }

    /**
     * Show dialog for setting Bluetooth timer
     * Allows user to choose preset or custom timer duration
     */
    private fun showBluetoothTimerDialog() {
        val timerOptions = arrayOf("15 min", "30 min", "60 min", "90 min", "120 min", "Custom")
        AlertDialog.Builder(this)
            .setTitle("Set Bluetooth Timer")
            .setItems(timerOptions) { _, which ->
                val option = timerOptions[which]
                if (option == "Custom") {
                    showCustomBluetoothTimerDialog()
                } else {
                    timerViewModel.setBluetoothTimerOption(option)
                    showToast("Bluetooth timer set to $option")
                }
            }
            .setNegativeButton("Cancel") { _, _ ->
                // Reset toggle if user cancels
                binding.toggleBluetooth.isChecked = false
                timerViewModel.setAutoDisconnectBluetooth(false)
            }
            .show()
    }

    /**
     * Show dialog for setting Hotspot timer
     * Allows user to choose preset or custom timer duration
     */
    private fun showHotspotTimerDialog() {
        val timerOptions = arrayOf("15 min", "30 min", "60 min", "90 min", "120 min", "Custom")
        AlertDialog.Builder(this)
            .setTitle("Set Hotspot Timer")
            .setItems(timerOptions) { _, which ->
                val option = timerOptions[which]
                if (option == "Custom") {
                    showCustomHotspotTimerDialog()
                } else {
                    timerViewModel.setHotspotTimerOption(option)
                    showToast("Hotspot timer set to $option")
                }
            }
            .setNegativeButton("Cancel") { _, _ ->
                // Reset toggle if user cancels
                binding.toggleHotspot.isChecked = false
                timerViewModel.setAutoDisconnectHotspot(false)
            }
            .show()
    }

    /**
     * Show dialog for custom Bluetooth timer input
     * Allows user to input specific minutes and seconds
     */
    private fun showCustomBluetoothTimerDialog() {
        // Create input fields for minutes and seconds
        val minInput = android.widget.EditText(this).apply {
            hint = "Minutes (0-999)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText("15") // Default value
        }
        val secInput = android.widget.EditText(this).apply {
            hint = "Seconds (0-59)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText("0") // Default value
        }
        
        // Create layout for input fields
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
            addView(minInput)
            addView(secInput)
        }

        AlertDialog.Builder(this)
            .setTitle("Set Custom Bluetooth Timer")
            .setView(layout)
            .setPositiveButton("Set") { _, _ ->
                // Parse and validate input
                val min = minInput.text.toString().toIntOrNull() ?: 0
                val sec = secInput.text.toString().toIntOrNull() ?: 0
                
                // Validate time range
                if (min < 0 || min > 999 || sec < 0 || sec > 59) {
                    showErrorDialog("Invalid time values. Minutes: 0-999, Seconds: 0-59")
                    return@setPositiveButton
                }
                
                // Convert to milliseconds
                val millis = (min * 60 + sec) * 1000L
                if (millis == 0L) {
                    showErrorDialog("Timer must be greater than 0 seconds")
                    return@setPositiveButton
                }
                
                // Set custom timer
                timerViewModel.setCustomBluetoothTimer(millis)
                showToast("Bluetooth timer set to ${min}m ${sec}s")
            }
            .setNegativeButton("Cancel") { _, _ ->
                // Reset toggle if user cancels
                binding.toggleBluetooth.isChecked = false
                timerViewModel.setAutoDisconnectBluetooth(false)
            }
            .show()
    }

    /**
     * Show dialog for custom Hotspot timer input
     * Allows user to input specific minutes and seconds
     */
    private fun showCustomHotspotTimerDialog() {
        // Create input fields for minutes and seconds
        val minInput = android.widget.EditText(this).apply {
            hint = "Minutes (0-999)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText("15") // Default value
        }
        val secInput = android.widget.EditText(this).apply {
            hint = "Seconds (0-59)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText("0") // Default value
        }
        
        // Create layout for input fields
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
            addView(minInput)
            addView(secInput)
        }

        AlertDialog.Builder(this)
            .setTitle("Set Custom Hotspot Timer")
            .setView(layout)
            .setPositiveButton("Set") { _, _ ->
                // Parse and validate input
                val min = minInput.text.toString().toIntOrNull() ?: 0
                val sec = secInput.text.toString().toIntOrNull() ?: 0
                
                // Validate time range
                if (min < 0 || min > 999 || sec < 0 || sec > 59) {
                    showErrorDialog("Invalid time values. Minutes: 0-999, Seconds: 0-59")
                    return@setPositiveButton
                }
                
                // Convert to milliseconds
                val millis = (min * 60 + sec) * 1000L
                if (millis == 0L) {
                    showErrorDialog("Timer must be greater than 0 seconds")
                    return@setPositiveButton
                }
                
                // Set custom timer
                timerViewModel.setCustomHotspotTimer(millis)
                showToast("Hotspot timer set to ${min}m ${sec}s")
            }
            .setNegativeButton("Cancel") { _, _ ->
                // Reset toggle if user cancels
                binding.toggleHotspot.isChecked = false
                timerViewModel.setAutoDisconnectHotspot(false)
            }
            .show()
    }

    /**
     * Show dialog when timer finishes
     * Displays which features were disconnected and shows interstitial ad
     */
    private fun showTimerFinishedDialog() {
        // Get current settings to show what was disconnected
        val settings = timerViewModel.getCurrentSettings()
        val bluetoothEnabled = settings["autoDisconnectBluetooth"] as Boolean
        val hotspotEnabled = settings["autoDisconnectHotspot"] as Boolean
        
        // Build message showing what was disconnected
        val message = buildString {
            append("Your timer has completed.\n\n")
            if (bluetoothEnabled) {
                append("• Bluetooth has been disconnected\n")
            }
            if (hotspotEnabled) {
                append("• Hotspot has been disconnected\n")
            }
            if (!bluetoothEnabled && !hotspotEnabled) {
                append("No auto-disconnect features were enabled.")
            }
        }
        
        // Show interstitial ad when timer finishes
        adMobManager.showInterstitialAd()
        
        AlertDialog.Builder(this)
            .setTitle("Timer Finished!")
            .setMessage(message)
            .setPositiveButton("OK") { _, _ ->
                timerViewModel.resetTimer()
            }
            .setCancelable(false)
            .show()
    }

    /**
     * Show error dialog with custom message
     * @param message The error message to display
     */
    private fun showErrorDialog(message: String) {
        AlertDialog.Builder(this)
            .setTitle("Error")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    /**
     * Show toast message for user feedback
     * @param message The message to display
     */
    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    /**
     * Clean up resources when activity is destroyed
     * Stops timer and services to prevent memory leaks
     */
    override fun onDestroy() {
        super.onDestroy()
        timerViewModel.stopTimer(this)
        adMobManager.cleanup()
    }
} 