package com.example.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.MainActivity
import com.example.R
import com.example.data.local.HabitDao
import com.example.data.local.HabitLogDao
import com.example.data.local.HabitReminderDao
import com.example.data.local.SkipDayDao
import com.example.data.local.StreakFreezeDao
import com.example.data.local.toDomain
import com.example.data.prefs.UserPreferencesRepository
import com.example.domain.model.Habit
import com.example.domain.model.HabitReminder
import com.example.domain.usecase.HabitCalculator
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderScheduler @Inject constructor(
    private val workManager: WorkManager
) {
    companion object {
        const val WORK_NAME_GLOBAL = "daily_habit_reminder"
        const val WORK_NAME_STREAK_NUDGE = "streak_at_risk_nudge"
        const val WORK_TAG_REMINDERS = "habitworks_reminder_work"
        const val CHANNEL_ID = "habit_reminders"
        const val GLOBAL_NOTIFICATION_ID = 1001
        const val STREAK_NUDGE_NOTIFICATION_ID = 1002

        const val KEY_HABIT_ID = "habit_id"
        const val KEY_REMINDER_ID = "reminder_id"
        const val KEY_HOUR = "hour"
        const val KEY_MINUTE = "minute"
        const val KEY_IS_SNOOZE = "is_snooze"
        const val KEY_IS_STREAK_NUDGE = "is_streak_nudge"
    }

    fun scheduleDaily(hour: Int, minute: Int) {
        val delayMs = computeInitialDelayMs(hour, minute)
        val request = OneTimeWorkRequestBuilder<DailyReminderWorker>()
            .addTag(WORK_TAG_REMINDERS)
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setInputData(
                workDataOf(
                    KEY_HOUR to hour,
                    KEY_MINUTE to minute
                )
            )
            .build()
        workManager.enqueueUniqueWork(
            WORK_NAME_GLOBAL,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun cancelGlobal() {
        workManager.cancelUniqueWork(WORK_NAME_GLOBAL)
    }

    fun scheduleHabitReminder(reminder: HabitReminder) {
        if (!reminder.enabled) {
            cancelHabitReminder(reminder.id)
            return
        }
        val delayMs = computeInitialDelayMs(reminder.hour, reminder.minute)
        val request = OneTimeWorkRequestBuilder<DailyReminderWorker>()
            .addTag(WORK_TAG_REMINDERS)
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setInputData(
                workDataOf(
                    KEY_HABIT_ID to reminder.habitId,
                    KEY_REMINDER_ID to reminder.id,
                    KEY_HOUR to reminder.hour,
                    KEY_MINUTE to reminder.minute
                )
            )
            .build()
        workManager.enqueueUniqueWork(
            "habit_reminder_${reminder.id}",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun cancelHabitReminder(reminderId: String) {
        workManager.cancelUniqueWork("habit_reminder_$reminderId")
    }

    fun syncHabitReminders(habitId: String, oldReminders: List<HabitReminder>, newReminders: List<HabitReminder>) {
        oldReminders.filter { it.habitId == habitId }.forEach { cancelHabitReminder(it.id) }
        newReminders.forEach { scheduleHabitReminder(it) }
    }

    fun rescheduleAll(
        globalEnabled: Boolean,
        globalHour: Int,
        globalMinute: Int,
        habitReminders: List<HabitReminder>
    ) {
        cancelAll()
        if (globalEnabled) {
            scheduleDaily(globalHour, globalMinute)
        }
        habitReminders.filter { it.enabled }.forEach { scheduleHabitReminder(it) }
        scheduleStreakAtRiskCheck(replaceExisting = true)
    }

    fun scheduleStreakAtRiskCheck(replaceExisting: Boolean = false) {
        val delayMs = computeInitialDelayMs(20, 30)
        val request = OneTimeWorkRequestBuilder<DailyReminderWorker>()
            .addTag(WORK_TAG_REMINDERS)
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(KEY_IS_STREAK_NUDGE to true))
            .build()
        workManager.enqueueUniqueWork(
            WORK_NAME_STREAK_NUDGE,
            if (replaceExisting) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
            request
        )
    }

    fun scheduleSnooze(
        habitId: String?,
        reminderId: String?,
        delayMinutes: Long = 15L
    ) {
        val uniqueName = if (habitId != null) {
            "snooze_habit_$habitId"
        } else {
            "snooze_global"
        }
        val request = OneTimeWorkRequestBuilder<DailyReminderWorker>()
            .addTag(WORK_TAG_REMINDERS)
            .setInitialDelay(delayMinutes.coerceAtLeast(1L), TimeUnit.MINUTES)
            .setInputData(
                workDataOf(
                    KEY_HABIT_ID to habitId,
                    KEY_REMINDER_ID to reminderId,
                    KEY_IS_SNOOZE to true
                )
            )
            .build()
        workManager.enqueueUniqueWork(uniqueName, ExistingWorkPolicy.REPLACE, request)
    }

    fun cancelAll() {
        workManager.cancelAllWorkByTag(WORK_TAG_REMINDERS)
    }

    internal fun computeInitialDelayMs(
        hour: Int,
        minute: Int,
        now: ZonedDateTime = ZonedDateTime.now()
    ): Long {
        var nextTrigger = now
            .withHour(hour.coerceIn(0, 23))
            .withMinute(minute.coerceIn(0, 59))
            .withSecond(0)
            .withNano(0)
        if (!nextTrigger.isAfter(now)) {
            nextTrigger = nextTrigger.plusDays(1)
        }
        return Duration.between(now, nextTrigger).toMillis().coerceAtLeast(1000L)
    }
}

@HiltWorker
class DailyReminderWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted params: WorkerParameters,
    private val prefsRepository: UserPreferencesRepository,
    private val habitDao: HabitDao,
    private val habitLogDao: HabitLogDao,
    private val habitReminderDao: HabitReminderDao,
    private val skipDayDao: SkipDayDao,
    private val streakFreezeDao: StreakFreezeDao,
    private val reminderScheduler: ReminderScheduler
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val habitId = inputData.getString(ReminderScheduler.KEY_HABIT_ID)
        val reminderId = inputData.getString(ReminderScheduler.KEY_REMINDER_ID)
        val isSnooze = inputData.getBoolean(ReminderScheduler.KEY_IS_SNOOZE, false)
        val isStreakNudge = inputData.getBoolean(ReminderScheduler.KEY_IS_STREAK_NUDGE, false)
        val today = LocalDate.now()
        val todayStr = today.toString()

        if (isStreakNudge) {
            reminderScheduler.scheduleStreakAtRiskCheck(replaceExisting = true)
            handleStreakAtRiskNudge(today = today, nowHour = LocalTime.now().hour)
            return Result.success()
        }

        val isSkipDay = skipDayDao.getAllSkipDaysOnce().any { it.date == todayStr }

        if (habitId != null) {
            val habit = habitDao.getById(habitId)?.toDomain()
            if (habit == null || habit.archivedAt != null) {
                return Result.success()
            }

            // Re-schedule next daily occurrence unless this was just a 15-min snooze
            if (!isSnooze && reminderId != null) {
                val reminder = habitReminderDao.getById(reminderId)?.toDomain()
                if (reminder != null && reminder.enabled) {
                    reminderScheduler.scheduleHabitReminder(reminder)
                }
            }

            if (!isSkipDay && HabitCalculator.isHabitActiveOn(habit, today)) {
                val log = habitLogDao.getLog(habit.id, todayStr)
                if (log?.done != true) {
                    val notificationId = habit.id.hashCode() and 0x7FFFFFFF
                    showNotificationIfPermitted(
                        notificationId = notificationId,
                        title = "${habit.icon} ${habit.name}",
                        body = "Scheduled today · ${habit.formatTargetLabel()}",
                        habitId = habit.id,
                        reminderId = reminderId
                    )
                }
            }
            return Result.success()
        }

        // Global daily reminder
        val prefs = prefsRepository.userPrefs.first()
        if (!prefs.reminderEnabled && !isSnooze) return Result.success()

        if (!isSnooze) {
            reminderScheduler.scheduleDaily(prefs.reminderHour, prefs.reminderMinute)
        }

        if (!isSkipDay) {
            val activeHabits = habitDao.getActiveHabitsOnce().map { it.toDomain() }
            val logsToday = habitLogDao.getLogsForDateOnce(todayStr).associateBy { it.habitId }
            val nextPending = HabitCalculator.scheduledHabitsForDate(activeHabits, today)
                .firstOrNull { logsToday[it.id]?.done != true }

            val title = nextPending?.let { "Next up: ${it.icon} ${it.name}" }
                ?: appContext.getString(R.string.reminder_notification_title)
            val body = if (nextPending != null) {
                "Tap Done to check it off or Snooze for 15 minutes."
            } else {
                appContext.getString(R.string.reminder_notification_body)
            }
            showNotificationIfPermitted(
                notificationId = ReminderScheduler.GLOBAL_NOTIFICATION_ID,
                title = title,
                body = body,
                habitId = nextPending?.id,
                reminderId = null
            )
        }

        return Result.success()
    }

    private suspend fun handleStreakAtRiskNudge(today: LocalDate, nowHour: Int) {
        val prefs = prefsRepository.userPrefs.first()
        val allHabits = habitDao.getAllHabitsOnce().map { it.toDomain() }
        val allLogs = habitLogDao.getAllLogsOnce().map { it.toDomain() }
        val skippedDates = skipDayDao.getAllSkipDaysOnce()
            .mapNotNull { runCatching { LocalDate.parse(it.date) }.getOrNull() }
            .toSet()
        val frozenDates = streakFreezeDao.getAllFreezesOnce()
            .mapNotNull { runCatching { LocalDate.parse(it.date) }.getOrNull() }
            .toSet()

        val logsByKey = HabitCalculator.indexLogs(allLogs)
        val todayCompletion = HabitCalculator.dayCompletion(allHabits, logsByKey, today, skippedDates, frozenDates)
        val currentStreak = HabitCalculator.calculateCurrentStreak(allHabits, allLogs, today, skippedDates, frozenDates)

        val shouldSend = StreakAtRiskNudgeEvaluator.shouldSendNudge(
            hour = nowHour,
            currentStreak = currentStreak,
            scheduledToday = todayCompletion.scheduledCount,
            completedToday = todayCompletion.completedCount,
            isSkippedToday = todayCompletion.isSkipped,
            isFrozenToday = todayCompletion.isFrozen,
            lastNudgeDate = prefs.lastStreakNudgeDate,
            today = today
        )

        if (!shouldSend) {
            if (todayCompletion.scheduledCount > 0 && todayCompletion.completedCount >= todayCompletion.scheduledCount) {
                val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.cancel(ReminderScheduler.STREAK_NUDGE_NOTIFICATION_ID)
            }
            return
        }

        val remainingHabits = HabitCalculator.scheduledHabitsForDate(allHabits, today)
            .filter { logsByKey[it.id to today.toString()]?.done != true }
        val nextHabit: Habit? = remainingHabits.firstOrNull()
        val (title, body) = StreakAtRiskNudgeEvaluator.buildCopy(
            currentStreak = currentStreak,
            remainingCount = remainingHabits.size,
            nextHabit = nextHabit,
            today = today
        )

        showNotificationIfPermitted(
            notificationId = ReminderScheduler.STREAK_NUDGE_NOTIFICATION_ID,
            title = title,
            body = body,
            habitId = nextHabit?.id,
            reminderId = null
        )
        prefsRepository.setLastStreakNudgeDate(today.toString())
    }

    private fun showNotificationIfPermitted(
        notificationId: Int,
        title: String,
        body: String,
        habitId: String?,
        reminderId: String?
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                ReminderScheduler.CHANNEL_ID,
                appContext.getString(R.string.reminder_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = appContext.getString(R.string.reminder_channel_desc)
            }
            manager.createNotificationChannel(channel)
        }

        val launchIntent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            appContext,
            notificationId,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(appContext, ReminderScheduler.CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)

        if (habitId != null) {
            val doneIntent = Intent(appContext, NotificationActionReceiver::class.java).apply {
                action = NotificationActionReceiver.ACTION_DONE
                putExtra(NotificationActionReceiver.EXTRA_HABIT_ID, habitId)
                putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
            }
            val donePending = PendingIntent.getBroadcast(
                appContext,
                notificationId * 31 + 1,
                doneIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(0, appContext.getString(R.string.action_done), donePending)
        }

        val snoozeIntent = Intent(appContext, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_SNOOZE
            putExtra(NotificationActionReceiver.EXTRA_HABIT_ID, habitId)
            putExtra(NotificationActionReceiver.EXTRA_REMINDER_ID, reminderId)
            putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        val snoozePending = PendingIntent.getBroadcast(
            appContext,
            notificationId * 31 + 2,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        builder.addAction(0, appContext.getString(R.string.action_snooze), snoozePending)

        manager.notify(notificationId, builder.build())
    }
}

object StreakAtRiskNudgeEvaluator {

    fun isQuietHours(hour: Int): Boolean = hour >= 22 || hour < 7

    fun shouldSendNudge(
        hour: Int,
        currentStreak: Int,
        scheduledToday: Int,
        completedToday: Int,
        isSkippedToday: Boolean,
        isFrozenToday: Boolean,
        lastNudgeDate: String,
        today: LocalDate
    ): Boolean {
        if (isQuietHours(hour)) return false
        if (currentStreak < 3) return false
        if (scheduledToday <= 0 || completedToday >= scheduledToday) return false
        if (isSkippedToday || isFrozenToday) return false
        if (lastNudgeDate == today.toString()) return false
        return true
    }

    fun buildCopy(
        currentStreak: Int,
        remainingCount: Int,
        nextHabit: Habit?,
        today: LocalDate
    ): Pair<String, String> {
        val nextName = nextHabit?.let { "${it.icon} ${it.name}" } ?: "one quick habit"
        val variant = ((today.toEpochDay() % 6L) + 6L).toInt() % 6
        return when (variant) {
            0 -> "Keep your ${currentStreak}-day spark warm" to
                "$nextName is waiting — even a tiny win counts tonight."
            1 -> "Gentle check-in · ${currentStreak}d streak" to
                "$remainingCount left today. Take 2 minutes for $nextName?"
            2 -> "Your ${currentStreak}-day momentum" to
                "Small steps still move you forward. Ready for $nextName?"
            3 -> "End-of-day rhythm check" to
                "One check-in on $nextName keeps your ${currentStreak}-day streak glowing."
            4 -> "Almost there for today" to
                "Just $remainingCount habit${if (remainingCount == 1) "" else "s"} left — $nextName takes just a moment."
            else -> "${currentStreak} days strong" to
                "Finish strong or do the tiny version of $nextName before turning in."
        }
    }
}
