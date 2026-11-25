package com.example

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ads.ConsentManager
import com.example.ads.InterstitialAdManager
import com.example.ads.RewardedAdManager
import com.example.data.prefs.UserPreferencesRepository
import com.example.data.prefs.UserPrefs
import com.example.domain.usecase.CurrentDateProvider
import com.example.ui.navigation.HabitWorksRoot
import com.example.ui.theme.HabitWorksTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var currentDateProvider: CurrentDateProvider

    @Inject
    lateinit var consentManager: ConsentManager

    @Inject
    lateinit var interstitialAdManager: InterstitialAdManager

    @Inject
    lateinit var rewardedAdManager: RewardedAdManager

    @Inject
    lateinit var prefsRepository: UserPreferencesRepository

    private val dateTimeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            currentDateProvider.refresh()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        ContextCompat.registerReceiver(
            this,
            dateTimeReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        setContent {
            val prefs by prefsRepository.userPrefs.collectAsStateWithLifecycle(initialValue = UserPrefs())
            HabitWorksTheme(selectedTheme = prefs.effectiveTheme()) {
                HabitWorksRoot(
                    consentManager = consentManager,
                    interstitialAdManager = interstitialAdManager,
                    rewardedAdManager = rewardedAdManager
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        currentDateProvider.refresh()
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(dateTimeReceiver) }
        super.onDestroy()
    }
}
