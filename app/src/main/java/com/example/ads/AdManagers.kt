package com.example.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.example.data.prefs.UserPreferencesRepository
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Central configuration for all Google AdMob Ad Unit IDs.
 *
 * TODO: Replace these official Google AdMob TEST ad unit IDs with your real
 * production Ad Unit IDs from AdMob Console before publishing to Google Play.
 */
object AdConfig {
    // Official Google AdMob TEST Adaptive Banner ID (Home screen)
    const val BANNER_HOME_AD_UNIT_ID = "ca-app-pub-3940256099942544/9214589741"

    // Official Google AdMob TEST Adaptive Banner ID (Progress screen)
    const val BANNER_PROGRESS_AD_UNIT_ID = "ca-app-pub-3940256099942544/9214589741"

    // Official Google AdMob TEST Interstitial ID (shown only after all daily habits are complete)
    const val INTERSTITIAL_ALL_DONE_AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"

    // Official Google AdMob TEST Rewarded Ad ID (Streak Freeze & 7-day Theme unlock)
    const val REWARDED_AD_UNIT_ID = "ca-app-pub-3940256099942544/5224354917"

    // Minimum 4 minutes between interstitial ads
    const val INTERSTITIAL_COOLDOWN_MS = 4 * 60 * 1000L
}

@Singleton
class ConsentManager @Inject constructor(
    @param:ApplicationContext private val appContext: Context
) {
    private val _canRequestAds = MutableStateFlow(false)
    val canRequestAds: StateFlow<Boolean> = _canRequestAds.asStateFlow()

    private val _consentFlowCompleted = MutableStateFlow(false)
    val consentFlowCompleted: StateFlow<Boolean> = _consentFlowCompleted.asStateFlow()

    private val isMobileAdsInitialized = AtomicBoolean(false)

    fun gatherConsentAndInitializeAds(activity: Activity) {
        val consentInfo: ConsentInformation = UserMessagingPlatform.getConsentInformation(activity)
        val params = ConsentRequestParameters.Builder().build()

        consentInfo.requestConsentInfoUpdate(
            activity,
            params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                    if (formError != null) {
                        Log.w("ConsentManager", "UMP form error: ${formError.message}")
                    }
                    val allowed = consentInfo.canRequestAds()
                    _consentFlowCompleted.value = true
                    if (allowed) {
                        initializeMobileAdsOnce()
                    } else {
                        _canRequestAds.value = false
                    }
                }
            },
            { requestError ->
                Log.w("ConsentManager", "UMP consent info update skipped: ${requestError.message}")
                val allowed = consentInfo.canRequestAds()
                _consentFlowCompleted.value = true
                if (allowed) {
                    initializeMobileAdsOnce()
                } else {
                    _canRequestAds.value = false
                }
            }
        )

        if (consentInfo.canRequestAds()) {
            initializeMobileAdsOnce()
        }
    }

    private fun initializeMobileAdsOnce() {
        if (isMobileAdsInitialized.getAndSet(true)) return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                MobileAds.initialize(appContext) {
                    _canRequestAds.value = true
                }
            } catch (e: Exception) {
                isMobileAdsInitialized.set(false)
                Log.w("ConsentManager", "MobileAds init skipped: ${e.message}")
            }
        }
    }
}

@Singleton
class InterstitialAdManager @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
    private val prefsRepository: UserPreferencesRepository,
    private val consentManager: ConsentManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var interstitialAd: InterstitialAd? = null
    private var isLoading = false

    fun preload() {
        if (!consentManager.canRequestAds.value || isLoading || interstitialAd != null) {
            return
        }
        isLoading = true
        InterstitialAd.load(
            appContext,
            AdConfig.INTERSTITIAL_ALL_DONE_AD_UNIT_ID,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    isLoading = false
                    interstitialAd = ad
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    isLoading = false
                    interstitialAd = null
                }
            }
        )
    }

    fun showAfterAllHabitsCompletedIfEligible(activity: Activity) {
        if (!consentManager.canRequestAds.value) return
        val ad = interstitialAd
        if (ad == null) {
            preload()
            return
        }

        scope.launch {
            val prefs = prefsRepository.userPrefs.first()
            val now = System.currentTimeMillis()
            if (now - prefs.lastInterstitialTimestampMs < AdConfig.INTERSTITIAL_COOLDOWN_MS) {
                return@launch
            }
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    interstitialAd = null
                    preload()
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    interstitialAd = null
                }
            }
            prefsRepository.setLastInterstitialTimestamp(now)
            interstitialAd = null
            ad.show(activity)
        }
    }
}

@Singleton
class RewardedAdManager @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
    private val consentManager: ConsentManager
) {
    private var rewardedAd: RewardedAd? = null
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun preload() {
        if (!consentManager.canRequestAds.value || _isLoading.value || rewardedAd != null) {
            return
        }
        _isLoading.value = true
        RewardedAd.load(
            appContext,
            AdConfig.REWARDED_AD_UNIT_ID,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    _isLoading.value = false
                    rewardedAd = ad
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    _isLoading.value = false
                    rewardedAd = null
                }
            }
        )
    }

    fun showRewardedAd(
        activity: Activity,
        onRewardEarned: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        if (!consentManager.canRequestAds.value) {
            onFailure("Ads are not ready yet. Check your connection or privacy settings and try again.")
            return
        }

        val readyAd = rewardedAd
        if (readyAd != null) {
            presentAd(activity, readyAd, onRewardEarned, onFailure)
            return
        }

        if (_isLoading.value) {
            onFailure("Rewarded ad is still loading. Please try again in a moment.")
            return
        }

        _isLoading.value = true
        RewardedAd.load(
            appContext,
            AdConfig.REWARDED_AD_UNIT_ID,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    _isLoading.value = false
                    presentAd(activity, ad, onRewardEarned, onFailure)
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    _isLoading.value = false
                    rewardedAd = null
                    onFailure("Could not load rewarded ad right now. Please check your connection and try again.")
                }
            }
        )
    }

    private fun presentAd(
        activity: Activity,
        ad: RewardedAd,
        onRewardEarned: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        rewardedAd = null
        var earned = false
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                preload()
                if (!earned) {
                    onFailure("Watch the full ad to unlock your reward.")
                }
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                preload()
                onFailure("Ad could not be displayed right now. Please try again.")
            }
        }
        ad.show(activity) {
            earned = true
            onRewardEarned()
        }
    }
}
