package com.drift.service

import android.app.*
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import com.drift.MainActivity

class BluetoothService : Service() {

    private var timerMillis: Long = 0
    private var autoDisconnectBluetooth = false
    private var countDownTimer: CountDownTimer? = null
    private var bluetoothAdapter: BluetoothAdapter? = null

    companion object {
        private const val TAG = "BluetoothService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "drift_bluetooth_channel"
        
        fun start(context: Context, timerMillis: Long, autoBluetooth: Boolean) {
            val intent = Intent(context, BluetoothService::class.java).apply {
                putExtra("timerMillis", timerMillis)
                putExtra("autoDisconnectBluetooth", autoBluetooth)
                putExtra("action", "start")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, BluetoothService::class.java).apply {
                putExtra("action", "stop")
            }
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.getStringExtra("action")
        
        when (action) {
            "start" -> {
                timerMillis = intent.getLongExtra("timerMillis", 0)
                autoDisconnectBluetooth = intent.getBooleanExtra("autoDisconnectBluetooth", false)
                
                if (autoDisconnectBluetooth && timerMillis > 0) {
                    startForeground(NOTIFICATION_ID, createNotification("Bluetooth timer active"))
                    startBluetoothTimer()
                }
            }
            "stop" -> {
                stopSelf()
            }
        }
        
        return START_STICKY
    }

    private fun startBluetoothTimer() {
        countDownTimer = object : CountDownTimer(timerMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val minutes = millisUntilFinished / 1000 / 60
                val seconds = (millisUntilFinished / 1000) % 60
                updateNotification("Bluetooth will disconnect in: ${String.format("%02d:%02d", minutes, seconds)}")
            }
            
            override fun onFinish() {
                if (autoDisconnectBluetooth) {
                    disconnectBluetooth()
                }
                updateNotification("Bluetooth disconnected")
                stopSelf()
            }
        }.start()
    }

    private fun disconnectBluetooth() {
        try {
            bluetoothAdapter?.let { adapter ->
                if (adapter.isEnabled) {
                    // Disconnect all paired devices
                    adapter.bondedDevices?.forEach { device ->
                        try {
                            val method = device.javaClass.getMethod("removeBond")
                            method.invoke(device)
                            Log.d(TAG, "Disconnected device: ${device.name}")
                        } catch (e: Exception) {
                            Log.e(TAG, "Error disconnecting device: ${e.message}")
                        }
                    }
                    
                    // Disable Bluetooth
                    adapter.disable()
                    Log.d(TAG, "Bluetooth disabled")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error disconnecting Bluetooth: ${e.message}")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Drift Bluetooth Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows Bluetooth timer status"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(content: String): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Drift - Bluetooth Timer")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun updateNotification(content: String) {
        val notification = createNotification(content)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    override fun onBind(intent: Intent?) = null

    override fun onDestroy() {
        countDownTimer?.cancel()
        super.onDestroy()
    }
} 