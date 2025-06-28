package com.drift.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.*
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import com.drift.MainActivity

class HotspotService : Service() {

    private var timerMillis: Long = 0
    private var autoDisconnectHotspot = false
    private var countDownTimer: CountDownTimer? = null
    private var wifiManager: WifiManager? = null

    companion object {
        private const val TAG = "HotspotService"
        private const val NOTIFICATION_ID = 1002
        private const val CHANNEL_ID = "drift_hotspot_channel"
        
        fun start(context: Context, timerMillis: Long, autoHotspot: Boolean) {
            val intent = Intent(context, HotspotService::class.java).apply {
                putExtra("timerMillis", timerMillis)
                putExtra("autoDisconnectHotspot", autoHotspot)
                putExtra("action", "start")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, HotspotService::class.java).apply {
                putExtra("action", "stop")
            }
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        wifiManager = getSystemService(Context.WIFI_SERVICE) as WifiManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.getStringExtra("action")
        
        when (action) {
            "start" -> {
                timerMillis = intent.getLongExtra("timerMillis", 0)
                autoDisconnectHotspot = intent.getBooleanExtra("autoDisconnectHotspot", false)
                
                if (autoDisconnectHotspot && timerMillis > 0) {
                    startForeground(NOTIFICATION_ID, createNotification("Hotspot timer active"))
                    startHotspotTimer()
                }
            }
            "stop" -> {
                stopSelf()
            }
        }
        
        return START_STICKY
    }

    private fun startHotspotTimer() {
        countDownTimer = object : CountDownTimer(timerMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val minutes = millisUntilFinished / 1000 / 60
                val seconds = (millisUntilFinished / 1000) % 60
                updateNotification("Hotspot will disconnect in: ${String.format("%02d:%02d", minutes, seconds)}")
            }
            
            override fun onFinish() {
                if (autoDisconnectHotspot) {
                    turnOffHotspot()
                }
                updateNotification("Hotspot disconnected")
                stopSelf()
            }
        }.start()
    }

    private fun turnOffHotspot() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // For Android 8.0+, we need to guide user to settings
                val panelIntent = Intent(Settings.ACTION_WIRELESS_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(panelIntent)
                Log.d(TAG, "Opened wireless settings for hotspot control")
            } else {
                // For older versions, try programmatic control
                wifiManager?.let { wifi ->
                    try {
                        val method = wifi.javaClass.getMethod("setWifiApEnabled", android.net.wifi.WifiConfiguration::class.java, Boolean::class.javaPrimitiveType)
                        method.invoke(wifi, null, false)
                        Log.d(TAG, "Hotspot disabled programmatically")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error disabling hotspot: ${e.message}")
                        // Fallback to settings
                        val panelIntent = Intent(Settings.ACTION_WIRELESS_SETTINGS).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        startActivity(panelIntent)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error turning off hotspot: ${e.message}")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Drift Hotspot Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows Hotspot timer status"
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
            .setContentTitle("Drift - Hotspot Timer")
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