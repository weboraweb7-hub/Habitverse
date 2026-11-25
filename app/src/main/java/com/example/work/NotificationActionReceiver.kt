package com.example.work

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.repository.HabitRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@AndroidEntryPoint
class NotificationActionReceiver : BroadcastReceiver() {

    @Inject
    lateinit var habitRepository: HabitRepository

    @Inject
    lateinit var reminderScheduler: ReminderScheduler

    companion object {
        const val ACTION_DONE = "com.example.habitworks.ACTION_REMINDER_DONE"
        const val ACTION_SNOOZE = "com.example.habitworks.ACTION_REMINDER_SNOOZE"
        const val EXTRA_HABIT_ID = "extra_habit_id"
        const val EXTRA_REMINDER_ID = "extra_reminder_id"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
        val habitId = intent.getStringExtra(EXTRA_HABIT_ID)
        val reminderId = intent.getStringExtra(EXTRA_REMINDER_ID)

        if (notificationId != -1) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.cancel(notificationId)
        }

        when (intent.action) {
            ACTION_DONE -> {
                if (habitId != null) {
                    val pendingResult = goAsync()
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            habitRepository.markHabitDone(habitId, LocalDate.now())
                        } finally {
                            pendingResult.finish()
                        }
                    }
                }
            }
            ACTION_SNOOZE -> {
                reminderScheduler.scheduleSnooze(
                    habitId = habitId,
                    reminderId = reminderId,
                    delayMinutes = 15L
                )
            }
        }
    }
}
