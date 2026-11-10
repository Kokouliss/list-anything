package com.kokouliss.listanything

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.kokouliss.listanything.core.data.isBackendConfigured
import com.kokouliss.listanything.core.domain.model.ListItem
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

        // shortcut: nine in the morning for everyone. Add a setting on the profile page when
        // someone wants another hour or no reminder.
        fun schedule(context: Context) {
            val now = LocalDateTime.now()
            val nextNine = now.toLocalDate().atTime(9, 0).let { if (it.isAfter(now)) it else it.plusDays(1) }
            val request = PeriodicWorkRequestBuilder<DueReminderWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(Duration.between(now, nextNine))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            // KEEP: the reminder that is already planned stays on its hour.
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork("due-reminder", ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
