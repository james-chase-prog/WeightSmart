package com.weightsmart.client.platform.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * NotificationScheduler
 * Manages local push notifications for reminders and celebratory events.
 *
 * Architecture Role:
 * Platform-level service responsible for creating notification channels (Android O+),
 * checking POST_NOTIFICATIONS permission (Android 13+), and posting notifications.
 * Called by HomeViewModel (goal-reached) and future scheduling logic (daily reminders).
 *
 * Lifecycle & Scheduling:
 * Singleton-scoped, injected by Hilt. Currently fires notifications immediately;
 * daily reminder scheduling (AlarmManager or WorkManager) is stubbed for P7.
 *
 * Key Concepts & Documentation:
 * NotificationChannel: Required on Android O+ to categorize notifications for user control.
 * <a href="https://developer.android.com/develop/ui/views/notifications/channels">Reference: Notification Channels</a>
 * POST_NOTIFICATIONS Permission: Runtime permission required on Android 13+ (API 33).
 * <a href="https://developer.android.com/develop/ui/views/notifications/notification-permission">Reference: Notification Permission</a>
 *
 * @author James Chase
 * @version 1.0
 * @since 2026-01-20
 */
@Singleton
class NotificationScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    companion object {
        private const val CHANNEL_ID = "weightsmart.reminders"
        private const val CHANNEL_NAME = "WeightSmart Reminders"
        /** Notification ID for daily weigh-in reminders */
        private const val NOTIF_ID_REMINDER = 1001
        /** Notification ID for goal-reached celebrations */
        private const val NOTIF_ID_GOAL_REACHED = 1002
    }

    /**
     * Posts a "Time to weigh in" reminder notification immediately.
     * Checks POST_NOTIFICATIONS permission on Android 13+ before posting.
     */
    fun showWeighInReminderNow() {
        // Check POST_NOTIFICATIONS permission on Android 13+ (API 33)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                // Permission not granted, cannot show notification
                return
            }
        }

        ensureChannel()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Time to weigh in")
            .setContentText("Log your weight to keep your streak going.")
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIF_ID_REMINDER, notification)
    }

    /**
     * Posts a celebratory "Goal Reached!" notification.
     * Called by HomeViewModel when GoalReachedUseCase returns true.
     * Checks POST_NOTIFICATIONS permission on Android 13+ before posting.
     */
    fun showGoalReachedNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                return
            }
        }

        ensureChannel()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.btn_star_big_on)
            .setContentTitle("Goal Reached!")
            .setContentText("Congratulations! You've hit your goal weight!")
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIF_ID_GOAL_REACHED, notification)
    }

    // --- STUBBED SCHEDULING API (P7) ---
    // These stubs allow callers to compile; actual scheduling (AlarmManager/WorkManager) is deferred.
    //Schedules a daily weigh-in reminder at the user's preferred time (P7 -- no-op stub).
    fun scheduleDailyReminder() { /* no-op for now */ }
    // Cancels the daily weigh-in reminder (P7 -- no-op stub).
    fun cancelDailyReminder() { /* no-op for now */ }

    /**
     * Creates the notification channel if it doesn't already exist (required on Android O+).
     * Safe to call multiple times -- the system ignores duplicate channel creation.
     */
    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = context.getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            )
            mgr.createNotificationChannel(channel)
        }
    }
}

