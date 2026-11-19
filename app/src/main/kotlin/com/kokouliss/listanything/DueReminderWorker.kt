package com.kokouliss.listanything

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.kokouliss.listanything.core.data.isBackendConfigured
import com.kokouliss.listanything.core.domain.model.ListItem
import com.kokouliss.listanything.core.domain.repository.AuthRepository
import com.kokouliss.listanything.core.domain.repository.ItemRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import com.kokouliss.listanything.core.ui.R as CoreR

/** Once a day, one notification that names the open items that are due today or earlier. */
internal class DueReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    // WorkManager creates the worker itself, so the worker asks Hilt for what it needs.
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun itemRepository(): ItemRepository
        fun authRepository(): AuthRepository
    }

    override suspend fun doWork(): Result {
        val context = applicationContext
        val notifications = NotificationManagerCompat.from(context)
        if (!isBackendConfigured(context) || !notifications.areNotificationsEnabled()) return Result.success()

        val due = try {
            EntryPointAccessors.fromApplication(context, Dependencies::class.java).itemRepository()
                .loadDueItems(LocalDate.now().toEpochDay())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The connection dropped while reading. WorkManager tries again a little later.
            return Result.retry()
        }
        if (due.isEmpty()) {
            notifications.cancel(NOTIFICATION_ID)
        } else {
            show(notifications, due)
        }
        return Result.success()
    }

    @SuppressLint("MissingPermission") // doWork stops early when notifications are off.
    private fun show(notifications: NotificationManagerCompat, due: List<ListItem>) {
        val context = applicationContext
        // The same intent as the app icon, so a tap returns to the app as it was left.
        val launcher = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
        val openApp = PendingIntent.getActivity(context, 0, launcher, PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(CoreR.drawable.core_ui_ic_checklist)
            .setContentTitle(context.resources.getQuantityString(R.plurals.due_reminder_title, due.size, due.size))
            .setContentText(due.joinToString { it.text })
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()
        notifications.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val CHANNEL = "due"
        private const val NOTIFICATION_ID = 1

        private const val PREFERENCES = "reminders"
        private const val KEY_HOUR = "daily_hour"

        /** The hour of the day the reminder comes at: nine in the morning until Settings says otherwise. */
        fun hour(context: Context): Int =
            context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getInt(KEY_HOUR, 9)

        /** Stores [hour] and plans the reminder again, for its next time at that hour. */
        fun setHour(context: Context, hour: Int) {
            context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit { putInt(KEY_HOUR, hour.coerceIn(0, 23)) }
            schedule(context, replace = true)
        }

        fun schedule(context: Context, replace: Boolean = false) {
            val now = LocalDateTime.now()
            val next = now.toLocalDate().atTime(hour(context), 0).let { if (it.isAfter(now)) it else it.plusDays(1) }
            val request = PeriodicWorkRequestBuilder<DueReminderWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(Duration.between(now, next))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            // KEEP: the reminder that is already planned stays on its hour, until the hour is changed.
            val policy = if (replace) ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE else ExistingPeriodicWorkPolicy.KEEP
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("due-reminder", policy, request)
        }
    }
}
