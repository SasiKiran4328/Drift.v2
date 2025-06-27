package com.drift.util

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import android.content.Intent

object HotspotHelper {
    fun turnOffHotspot(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Direct programmatic control is restricted; prompt user
            val panelIntent = Intent(Settings.ACTION_WIRELESS_SETTINGS)
            panelIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(panelIntent)
        } else {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            try {
                val method = wifiManager.javaClass.getMethod("setWifiApEnabled", android.net.wifi.WifiConfiguration::class.java, Boolean::class.javaPrimitiveType)
                method.invoke(wifiManager, null, false)
            } catch (e: Exception) { /* Handle error */ }
        }
    }
} 