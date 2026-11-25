package com.example.ui.navigation

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material.icons.automirrored.rounded.ListAlt
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.R
import com.example.ads.ConsentManager
import com.example.ads.InterstitialAdManager
import com.example.ads.RewardedAdManager
import com.example.ui.home.HomeScreen
import com.example.ui.home.HomeViewModel
import com.example.ui.profile.ProfileSheet
import com.example.ui.profile.ProfileViewModel
import com.example.ui.progress.ProgressScreen
import com.example.ui.progress.ProgressViewModel
import com.example.ui.tasks.TasksScreen
import com.example.ui.tasks.TasksViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

enum class MainTab(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    HOME("home", R.string.nav_home, Icons.Rounded.Home, Icons.Outlined.Home),
    TASKS("tasks", R.string.nav_tasks, Icons.AutoMirrored.Rounded.ListAlt, Icons.AutoMirrored.Outlined.ListAlt),
    PROGRESS("progress", R.string.nav_progress, Icons.Rounded.BarChart, Icons.Outlined.BarChart)
}

@Composable
fun HabitWorksRoot(
    consentManager: ConsentManager,
    interstitialAdManager: InterstitialAdManager,
    rewardedAdManager: RewardedAdManager
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val coroutineScope = rememberCoroutineScope()

    val canRequestAds by consentManager.canRequestAds.collectAsStateWithLifecycle()

    LaunchedEffect(activity) {
        if (activity != null) {
            consentManager.gatherConsentAndInitializeAds(activity)
        }
    }

    LaunchedEffect(canRequestAds) {
        if (canRequestAds) {
            interstitialAdManager.preload()
            rewardedAdManager.preload()
        }
    }

    val tabNavController = rememberNavController()
    val navBackStackEntry by tabNavController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: MainTab.HOME.route

    val homeViewModel: HomeViewModel = hiltViewModel()
    val tasksViewModel: TasksViewModel = hiltViewModel()
    val progressViewModel: ProgressViewModel = hiltViewModel()
    val profileViewModel: ProfileViewModel = hiltViewModel()

    val homeState by homeViewModel.uiState.collectAsStateWithLifecycle()
    val tasksState by tasksViewModel.uiState.collectAsStateWithLifecycle()
    val progressState by progressViewModel.uiState.collectAsStateWithLifecycle()
    val profileState by profileViewModel.uiState.collectAsStateWithLifecycle()

    var showProfileSheet by remember { mutableStateOf(false) }
    var externalJumpDate by remember { mutableStateOf<LocalDate?>(null) }
    var adFeedbackMessage by remember { mutableStateOf<String?>(null) }

    // Show interstitial strictly after the user completes all habits for the day (with cooldown)
    LaunchedEffect(homeViewModel, activity) {
        homeViewModel.allTodayDoneEvent.collect {
            if (activity != null) {
                interstitialAdManager.showAfterAllHabitsCompletedIfEligible(activity)
            }
        }
    }

    // SAF JSON Backup Export & Import Launchers
    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            runCatching {
                val jsonText = profileViewModel.buildExportBackupJson()
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { stream ->
                        stream.write(jsonText.toByteArray(Charsets.UTF_8))
                    } ?: error("Unable to open destination file.")
                }
            }.onSuccess {
                profileViewModel.showStatus("Backup exported successfully.")
            }.onFailure { err ->
                profileViewModel.showStatus("Export failed: ${err.localizedMessage ?: "Unknown error"}")
            }
        }
    }

    val importBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        stream.bufferedReader(Charsets.UTF_8).readText()
                    } ?: error("Unable to open backup file.")
                }
            }.onSuccess { rawJson ->
                profileViewModel.validateImportJson(rawJson)
            }.onFailure { err ->
                profileViewModel.showStatus("Import failed: ${err.localizedMessage ?: "Unknown error"}")
            }
        }
    }

    fun navigateToTab(target: MainTab) {
        tabNavController.navigate(target.route) {
            popUpTo(MainTab.HOME.route) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                MainTab.entries.forEach { tab ->
                    val selected = currentRoute == tab.route
                    NavigationBarItem(
                        selected = selected,
                        onClick = { navigateToTab(tab) },
                        icon = {
                            Icon(
                                imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                contentDescription = stringResource(tab.labelRes)
                            )
                        },
                        label = { Text(stringResource(tab.labelRes)) },
                        modifier = Modifier.testTag("nav_tab_${tab.route}")
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            NavHost(
                navController = tabNavController,
                startDestination = MainTab.HOME.route
            ) {
                composable(MainTab.HOME.route) {
                    HomeScreen(
                        state = homeState,
                        canRequestAds = canRequestAds,
                        externalJumpDate = externalJumpDate,
                        adFeedbackMessage = adFeedbackMessage,
                        onClearAdFeedback = { adFeedbackMessage = null },
                        onExternalJumpConsumed = { externalJumpDate = null },
                        onCompleteWelcome = homeViewModel::completeWelcome,
                        onSelectDate = homeViewModel::selectDate,
                        onClearFuturePreview = homeViewModel::clearFuturePreview,
                        onNextQuote = homeViewModel::nextQuote,
                        onToggleHabit = homeViewModel::toggleHabit,
                        onAdjustCount = homeViewModel::adjustCount,
                        onToggleTimer = homeViewModel::toggleTimer,
                        onResetTimer = homeViewModel::resetTimer,
                        onToggleSkipDay = homeViewModel::toggleSkipDay,
                        onUseFreeze = homeViewModel::useFreezeOnDate,
                        onWatchAdForStreakFreeze = { missedDate ->
                            if (activity == null) {
                                adFeedbackMessage = "Unable to show ad right now."
                            } else {
                                rewardedAdManager.showRewardedAd(
                                    activity = activity,
                                    onRewardEarned = {
                                        homeViewModel.grantFreezeTokenAndProtectIfNeeded(missedDate)
                                        adFeedbackMessage = "Streak Freeze applied to $missedDate!"
                                    },
                                    onFailure = { message ->
                                        adFeedbackMessage = message
                                    }
                                )
                            }
                        },
                        onOpenProfile = { showProfileSheet = true },
                        onGoToTasks = { navigateToTab(MainTab.TASKS) },
                        viewModel = homeViewModel
                    )
                }

                composable(MainTab.TASKS.route) {
                    TasksScreen(
                        state = tasksState,
                        onSelectCategory = tasksViewModel::selectCategory,
                        onAddClicked = tasksViewModel::onAddHabitClicked,
                        onEditClicked = tasksViewModel::onEditHabitClicked,
                        onDismissEditor = tasksViewModel::dismissEditor,
                        onQuickAddHabit = tasksViewModel::quickAddHabit,
                        onClearPlantMessage = tasksViewModel::clearPlantMessage,
                        onSaveHabit = tasksViewModel::saveHabit,
                        onDuplicateHabit = tasksViewModel::duplicateHabit,
                        onMoveHabit = tasksViewModel::moveHabit,
                        onApplySmartReminder = tasksViewModel::applySmartReminderSuggestion,
                        onDismissSmartReminder = tasksViewModel::dismissSmartReminderSuggestion,
                        onAddSuggested = tasksViewModel::addSuggestedHabit,
                        onAddStarterPack = tasksViewModel::addStarterPack,
                        onClearPackMessage = tasksViewModel::clearPackMessage,
                        onRequestArchive = tasksViewModel::requestArchive,
                        onSwipeArchive = tasksViewModel::archiveHabitDirect,
                        onConfirmArchive = tasksViewModel::confirmArchive,
                        onDismissArchive = tasksViewModel::dismissArchiveDialog,
                        onUndoArchive = tasksViewModel::undoArchive,
                        onClearLastArchived = tasksViewModel::clearLastArchived
                    )
                }

                composable(MainTab.PROGRESS.route) {
                    ProgressScreen(
                        state = progressState,
                        canRequestAds = canRequestAds,
                        onSelectRange = progressViewModel::selectRange,
                        onSaveDayNote = progressViewModel::saveDayNote,
                        onOpenDayOnHome = { date ->
                            externalJumpDate = date
                            navigateToTab(MainTab.HOME)
                        },
                        onExportCsv = progressViewModel::buildExportCsvShareIntent
                    )
                }
            }
        }
    }

    if (showProfileSheet) {
        ProfileSheet(
            state = profileState,
            onDismiss = { showProfileSheet = false },
            onUpdateFirstName = profileViewModel::updateFirstName,
            onSelectTheme = profileViewModel::selectTheme,
            onWatchAdForTheme = { themeOption ->
                if (activity == null) {
                    profileViewModel.showStatus("Unable to show ad right now.")
                } else {
                    rewardedAdManager.showRewardedAd(
                        activity = activity,
                        onRewardEarned = {
                            profileViewModel.onThemeUnlockRewardEarned(themeOption)
                        },
                        onFailure = { msg ->
                            profileViewModel.showStatus(msg)
                        }
                    )
                }
            },
            onWatchAdForStreakFreeze = {
                if (activity == null) {
                    profileViewModel.showStatus("Unable to show ad right now.")
                } else {
                    rewardedAdManager.showRewardedAd(
                        activity = activity,
                        onRewardEarned = {
                            profileViewModel.onStreakFreezeRewardEarned()
                        },
                        onFailure = { msg ->
                            profileViewModel.showStatus(msg)
                        }
                    )
                }
            },
            onApplyFreezeToMissedDate = profileViewModel::applyFreezeToMissedDate,
            onToggleTodaySkip = profileViewModel::toggleTodaySkip,
            onEnableVacationDays = profileViewModel::enableVacationDays,
            onToggleReminder = { enabled ->
                profileViewModel.setReminderEnabled(
                    enabled = enabled,
                    hour = profileState.reminderHour,
                    minute = profileState.reminderMinute
                )
            },
            onUpdateReminderTime = { hour, minute ->
                profileViewModel.updateReminderTime(
                    hour = hour,
                    minute = minute,
                    currentlyEnabled = profileState.reminderEnabled
                )
            },
            onExportBackupClick = {
                exportBackupLauncher.launch("habitworks-backup-${LocalDate.now()}.json")
            },
            onImportBackupClick = {
                importBackupLauncher.launch(arrayOf("application/json", "*/*"))
            },
            onConfirmImportBackup = profileViewModel::confirmImportBackup,
            onDismissImportConfirmation = profileViewModel::dismissImportConfirmation,
            onDeleteAllMyData = profileViewModel::deleteAllMyData,
            onClearStatus = profileViewModel::clearStatus
        )
    }
}
