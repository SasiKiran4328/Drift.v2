package com.drift.ads

import android.app.Activity
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds

class AdMobManager(private val activity: Activity) {
    fun loadBannerAd(adView: AdView) {
        MobileAds.initialize(activity) {}
        adView.adUnitId = "ca-app-pub-3940256099942544/6300978111" // Test Ad Unit
        adView.loadAd(AdRequest.Builder().build())
    }
} 