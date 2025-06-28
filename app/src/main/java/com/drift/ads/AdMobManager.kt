package com.drift.ads

import android.app.Activity
import android.util.Log
import com.google.android.gms.ads.*
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

/**
 * AdMobManager - Handles Google AdMob functionality with error handling and scalability
 */
class AdMobManager(private val activity: Activity) {
    
    companion object {
        private const val TAG = "AdMobManager"
        private const val BANNER_AD_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"
        private const val INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"
    }
    
    private var interstitialAd: InterstitialAd? = null
    private var interstitialAdListener: (() -> Unit)? = null
    
    init {
        initializeAdMob()
    }
    
    private fun initializeAdMob() {
        try {
            MobileAds.initialize(activity) { initializationStatus ->
                Log.d(TAG, "AdMob initialized: ${initializationStatus.adapterStatusMap}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize AdMob: ${e.message}")
        }
    }
    
    fun loadBannerAd(adView: AdView) {
        try {
            adView.adUnitId = BANNER_AD_UNIT_ID
            adView.adSize = AdSize.BANNER
            adView.loadAd(AdRequest.Builder().build())
            
            adView.adListener = object : AdListener() {
                override fun onAdLoaded() = Log.d(TAG, "Banner ad loaded")
                override fun onAdFailedToLoad(error: LoadAdError) = Log.e(TAG, "Banner ad failed: ${error.message}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading banner ad: ${e.message}")
        }
    }
    
    fun preloadInterstitialAd() {
        if (interstitialAd != null) return
        loadInterstitialAd()
    }
    
    private fun loadInterstitialAd() {
        try {
            InterstitialAd.load(
                activity,
                INTERSTITIAL_AD_UNIT_ID,
                AdRequest.Builder().build(),
                object : InterstitialAdLoadCallback() {
                    override fun onAdLoaded(ad: InterstitialAd) {
                        interstitialAd = ad
                        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                            override fun onAdDismissedFullScreenContent() {
                                interstitialAd = null
                                interstitialAdListener?.invoke()
                                loadInterstitialAd() // Preload next
                            }
                        }
                    }
                    
                    override fun onAdFailedToLoad(error: LoadAdError) {
                        Log.e(TAG, "Interstitial failed: ${error.message}")
                        interstitialAd = null
                    }
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error loading interstitial: ${e.message}")
        }
    }
    
    fun showInterstitialAd(): Boolean {
        return try {
            interstitialAd?.show(activity) ?: false
        } catch (e: Exception) {
            Log.e(TAG, "Error showing interstitial: ${e.message}")
            false
        }
    }
    
    fun setInterstitialAdListener(listener: () -> Unit) {
        interstitialAdListener = listener
    }
    
    fun isInterstitialAdReady(): Boolean = interstitialAd != null
    
    fun cleanup() {
        interstitialAd = null
        interstitialAdListener = null
    }
}