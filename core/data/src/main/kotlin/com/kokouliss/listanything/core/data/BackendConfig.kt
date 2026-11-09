package com.kokouliss.listanything.core.data

import android.content.Context
import com.google.firebase.FirebaseApp

/** False when the app was built without `app/google-services.json`, so no Firebase project is linked. */
fun isBackendConfigured(context: Context): Boolean = FirebaseApp.getApps(context).isNotEmpty()
