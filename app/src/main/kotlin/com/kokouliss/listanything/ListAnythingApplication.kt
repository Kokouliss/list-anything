package com.kokouliss.listanything

import android.app.Application
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.HiltAndroidApp

/** The channel of the notifications that firebase/functions sends. The manifest names it as well. */
private const val UPDATES_CHANNEL = "updates"

@HiltAndroidApp
class ListAnythingApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // Creating a channel that exists only refreshes its name, so this is safe on every start.
        NotificationManagerCompat.from(this).createNotificationChannelsCompat(
            listOf(
                channel(UPDATES_CHANNEL, R.string.channel_updates),
                channel(DueReminderWorker.CHANNEL, R.string.channel_due),
            ),
        )
        DueReminderWorker.schedule(this)
    }

    private fun channel(id: String, name: Int) =
        NotificationChannelCompat.Builder(id, NotificationManagerCompat.IMPORTANCE_DEFAULT).setName(getString(name)).build()
}
