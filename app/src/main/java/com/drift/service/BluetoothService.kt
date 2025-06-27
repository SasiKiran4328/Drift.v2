package com.drift.service

import android.app.*
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.os.*
import androidx.core.app.NotificationCompat
import com.drift.util.HotspotHelper

class BluetoothService : Service() {

    private var timerMillis: Long = 0
    private var autoDisconnectBluetooth = false
    private var autoDisconnectHotspot = false
    private var countDownTimer: CountDownTimer? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        timerMillis = intent?.getLongExtra("timerMillis", 0) ?: 0
        autoDisconnectBluetooth = intent?.getBooleanExtra("autoDisconnectBluetooth", false) ?: false
        autoDisconnectHotspot = intent?.getBooleanExtra("autoDisconnectHotspot", false) ?: false

        startForeground(1, createNotification("Timer started"))

        countDownTimer = object : CountDownTimer(timerMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                updateNotification("Time left: ${millisUntilFinished / 1000 / 60}:${(millisUntilFinished / 1000) % 60}")
            }
            override fun onFinish() {
                if (autoDisconnectBluetooth) disconnectBluetooth()
                if (autoDisconnectHotspot) HotspotHelper.turnOffHotspot(this@BluetoothService)
                stopSelf()
            }
        }.start()

        return START_STICKY
    }

    private fun disconnectBluetooth() {
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        bluetoothAdapter?.bondedDevices?.forEach { device ->
            try {
                val method = device.javaClass.getMethod("removeBond")
                method.invoke(device)
            } catch (e: Exception) { /* Handle error */ }
        }
        bluetoothAdapter?.disable()
    }

    private fun createNotification(content: String): Notification {
        val channelId = "drift_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Drift", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Drift Timer")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(content: String) {
        val notification = createNotification(content)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(1, notification)
    }

    override fun onBind(intent: Intent?) = null

    override fun onDestroy() {
        countDownTimer?.cancel()
        super.onDestroy()
    }

    companion object {
        fun start(context: Context, timerMillis: Long, autoBluetooth: Boolean, autoHotspot: Boolean) {
            val intent = Intent(context, BluetoothService::class.java).apply {
                putExtra("timerMillis", timerMillis)
                putExtra("autoDisconnectBluetooth", autoBluetooth)
                putExtra("autoDisconnectHotspot", autoHotspot)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, BluetoothService::class.java))
        }
    }
} 