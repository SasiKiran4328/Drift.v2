package com.drift

import android.Manifest
import android.app.AlertDialog
import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.drift.databinding.ActivityMainBinding
import com.drift.viewmodel.TimerViewModel
import com.drift.ads.AdMobManager

class MainActivity : AppCompatActivity() {

    private val timerViewModel: TimerViewModel by viewModels()
    private lateinit var adMobManager: AdMobManager
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_Drift)
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Timer options
        val timerOptions = arrayOf("15 min", "30 min", "60 min", "Custom")
        binding.timerDropdown.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, timerOptions)

        // Observe ViewModel
        timerViewModel.countdown.observe(this) { time ->
            binding.countdownText.text = time
        }
        timerViewModel.isRunning.observe(this) { running ->
            binding.startStopButton.text = if (running) getString(R.string.stop) else getString(R.string.start)
        }
        timerViewModel.progress.observe(this) { progress ->
            binding.timerProgressBar.progress = progress
        }

        // Toggle listeners
        binding.toggleBluetooth.setOnCheckedChangeListener { _, isChecked ->
            timerViewModel.setAutoDisconnectBluetooth(isChecked)
        }
        binding.toggleHotspot.setOnCheckedChangeListener { _, isChecked ->
            timerViewModel.setAutoDisconnectHotspot(isChecked)
        }

        // Timer selection
        binding.timerDropdown.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>, view: android.view.View?, position: Int, id: Long) {
                val option = timerOptions[position]
                if (option == "Custom") {
                    showCustomTimerDialog()
                } else {
                    timerViewModel.setTimerOption(option)
                }
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>) {}
        }

        // Start/Stop button
        binding.startStopButton.setOnClickListener {
            if (timerViewModel.isRunning.value == true) {
                timerViewModel.stopTimer(this)
            } else {
                requestPermissionsAndStart()
            }
        }

        // AdMob
        adMobManager = AdMobManager(this)
        adMobManager.loadBannerAd(binding.adView)
    }

    private fun requestPermissionsAndStart() {
        val permissions = arrayOf(
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        requestPermissions(permissions, 100)
        timerViewModel.startTimer(this)
    }

    private fun showErrorDialog(message: String) {
        AlertDialog.Builder(this)
            .setTitle(R.string.error)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun showCustomTimerDialog() {
        val dialogView = layoutInflater.inflate(android.R.layout.simple_list_item_2, null)
        val minInput = android.widget.EditText(this)
        minInput.hint = "Minutes"
        minInput.inputType = android.text.InputType.TYPE_CLASS_NUMBER
        val secInput = android.widget.EditText(this)
        secInput.hint = "Seconds"
        secInput.inputType = android.text.InputType.TYPE_CLASS_NUMBER
        val layout = android.widget.LinearLayout(this)
        layout.orientation = android.widget.LinearLayout.VERTICAL
        layout.addView(minInput)
        layout.addView(secInput)
        AlertDialog.Builder(this)
            .setTitle("Custom Timer")
            .setView(layout)
            .setPositiveButton("OK") { _, _ ->
                val min = minInput.text.toString().toIntOrNull() ?: 0
                val sec = secInput.text.toString().toIntOrNull() ?: 0
                val millis = (min * 60 + sec) * 1000L
                timerViewModel.setCustomTimer(millis)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
} 