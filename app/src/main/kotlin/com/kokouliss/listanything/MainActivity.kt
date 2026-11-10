package com.kokouliss.listanything

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.kokouliss.listanything.core.data.isBackendConfigured
import com.kokouliss.listanything.core.domain.model.InviteCode
import com.kokouliss.listanything.core.domain.model.SharedContent
import com.kokouliss.listanything.core.domain.model.parseSharedText
import com.kokouliss.listanything.core.ui.theme.ListAnythingTheme
import com.kokouliss.listanything.feature.lists.ListDetailKey
import dagger.hilt.android.AndroidEntryPoint

/** What the app was opened with, besides its icon. Each part is handled once and then cleared. */
internal data class Launch(
    /** Text or a link that another app shared. */
    val shared: SharedContent? = null,
    /** The code of a tapped or scanned invite link. */
    val inviteCode: String? = null,
    /** The list a tapped notification is about. */
    val list: ListDetailKey? = null,
)

// Everything in an intent comes from outside the app. It is only ever shown, parsed into a code or
// a web link, or used to open a list, which the security rules guard.
private fun Intent.toLaunch() = Launch(
    shared = if (action == Intent.ACTION_SEND) {
        parseSharedText(getStringExtra(Intent.EXTRA_TEXT), getStringExtra(Intent.EXTRA_SUBJECT))
    } else {
        null
    },
    inviteCode = if (action == Intent.ACTION_VIEW) dataString?.let(InviteCode::fromLink) else null,
    // firebase/functions puts these two on its notifications.
    list = getStringExtra("spaceId")?.let { spaceId -> getStringExtra("listId")?.let { ListDetailKey(spaceId, it) } },
)

// AppCompatActivity, because it is what applies the language chosen on the profile page on Android 9 to 12.
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private var launch by mutableStateOf(Launch())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Not after a rotation: by then the screen it asked for is already open.
        if (savedInstanceState == null) launch = intent.toLaunch()
        // Checked before any screen asks Hilt for Firebase, which would crash without a configuration.
        val isConfigured = isBackendConfigured(this)
        setContent {
            ListAnythingTheme {
                if (isConfigured) {
                    ListAnythingApp(launch = launch, onLaunchHandled = { launch = Launch() })
                } else {
                    SetupNeededScreen()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        launch = intent.toLaunch()
    }
}
