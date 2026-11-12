package com.kokouliss.listanything

import android.app.Application
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import com.kokouliss.listanything.feature.auth.restoreNightMode
import dagger.hilt.android.HiltAndroidApp

/** The channel of the notifications that firebase/functions sends. The manifest names it as well. */
private const val UPDATES_CHANNEL = "updates"

@HiltAndroidApp
class ListAnythingApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // Before any screen exists, so the first one already has the chosen light or dark look.
        restoreNightMode(this)
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
