package com.kokouliss.listanything

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.kokouliss.listanything.core.data.isBackendConfigured
import com.kokouliss.listanything.core.domain.model.ListRef
import com.kokouliss.listanything.core.domain.repository.ReminderScheduler
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import com.kokouliss.listanything.core.ui.R as CoreR

/** Rings once about one item at the moment its reminder was set for, unless the item is done or gone by then. */
internal class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    @SuppressLint("MissingPermission") // Stops early when notifications are off.
    override suspend fun doWork(): Result {
        val context = applicationContext
        val spaceId = inputData.getString(SPACE_ID) ?: return Result.failure()
        val listId = inputData.getString(LIST_ID) ?: return Result.failure()
        val itemId = inputData.getString(ITEM_ID) ?: return Result.failure()
        val notifications = NotificationManagerCompat.from(context)
        if (!isBackendConfigured(context) || !notifications.areNotificationsEnabled()) return Result.success()

        val item = try {
            EntryPointAccessors.fromApplication(context, DueReminderWorker.Dependencies::class.java)
                .itemRepository()
                .loadItem(ListRef(spaceId, listId), itemId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
        // Done, gone, or set for another moment since: nothing to say.
        if (item == null || item.isChecked || item.isDeleted || item.remindAtMillis != inputData.getLong(REMIND_AT, 0)) {
            return Result.success()
        }
        val openList = Intent(context, MainActivity::class.java).putExtra("spaceId", spaceId).putExtra("listId", listId)
        val tap = PendingIntent.getActivity(
            context,
            itemId.hashCode(),
            openList,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, DueReminderWorker.CHANNEL)
            .setSmallIcon(CoreR.drawable.core_ui_ic_alarm)
            .setContentTitle(item.text)
            .setContentText(context.getString(R.string.reminder_body))
            .setContentIntent(tap)
            .setAutoCancel(true)
            .build()
        notifications.notify(itemId, 0, notification)
        return Result.success()
    }

    companion object {
        const val SPACE_ID = "spaceId"
        const val LIST_ID = "listId"
        const val ITEM_ID = "itemId"
        const val REMIND_AT = "remindAt"
    }
}

// shortcut: WorkManager can be minutes late when the phone sleeps deeply. Move to AlarmManager's
// exact alarms, which need their own permission, if someone needs the minute.
internal class WorkManagerReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : ReminderScheduler {

    override fun schedule(list: ListRef, itemId: String, atMillis: Long) {
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(Duration.ofMillis((atMillis - System.currentTimeMillis()).coerceAtLeast(0)))
            .setInputData(
                workDataOf(
                    ReminderWorker.SPACE_ID to list.spaceId,
                    ReminderWorker.LIST_ID to list.listId,
                    ReminderWorker.ITEM_ID to itemId,
                    ReminderWorker.REMIND_AT to atMillis,
                ),
            )
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(workName(itemId), ExistingWorkPolicy.REPLACE, request)
    }

    override fun cancel(itemId: String) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(itemId))
    }

    private fun workName(itemId: String) = "remind-$itemId"
}
