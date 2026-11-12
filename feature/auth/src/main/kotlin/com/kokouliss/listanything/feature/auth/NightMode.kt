package com.kokouliss.listanything.feature.auth

import android.app.UiModeManager
import android.content.Context
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit

private const val PREFERENCES = "appearance"
private const val KEY_NIGHT_MODE = "night_mode"

/**
 * Applies the light or dark choice made on the profile page. Call it when the app starts, before
 * any screen: AppCompat forgets the choice when the app closes.
 */
fun restoreNightMode(context: Context) {
    val mode = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        .getInt(KEY_NIGHT_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
    AppCompatDelegate.setDefaultNightMode(mode)
}

/** Stores [mode], one of the AppCompatDelegate.MODE_NIGHT values, and applies it at once. */
internal fun saveNightMode(context: Context, mode: Int) {
    context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit { putInt(KEY_NIGHT_MODE, mode) }
    // Android 12 and later draw the start screen before the app runs, so they keep the choice too.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(UiModeManager::class.java).setApplicationNightMode(
            when (mode) {
                AppCompatDelegate.MODE_NIGHT_YES -> UiModeManager.MODE_NIGHT_YES
                AppCompatDelegate.MODE_NIGHT_NO -> UiModeManager.MODE_NIGHT_NO
                // Any other value takes the app's own choice away, so the phone decides again.
                else -> UiModeManager.MODE_NIGHT_AUTO
            },
        )
    }
    AppCompatDelegate.setDefaultNightMode(mode)
}
