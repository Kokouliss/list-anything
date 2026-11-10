package com.kokouliss.listanything.feature.auth

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** The signed-in person's account and the app settings. */
@Serializable
data object ProfileKey : NavKey

fun EntryProviderScope<NavKey>.profileEntries(onBack: () -> Unit) {
    entry<ProfileKey> {
        ProfileRoute(onBack = onBack)
    }
}
