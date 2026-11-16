package com.kokouliss.listanything.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.CheckBox
import androidx.glance.appwidget.CheckboxDefaults
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.material3.ColorProviders
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.kokouliss.listanything.MainActivity
import com.kokouliss.listanything.R
import com.kokouliss.listanything.core.data.isBackendConfigured
import com.kokouliss.listanything.core.domain.model.ListItem
import com.kokouliss.listanything.core.domain.model.ListRef
import com.kokouliss.listanything.core.domain.model.SharedList
import com.kokouliss.listanything.core.domain.repository.ItemRepository
import com.kokouliss.listanything.core.domain.repository.ListRepository
import com.kokouliss.listanything.core.ui.theme.DarkColors
import com.kokouliss.listanything.core.ui.theme.LightColors
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import com.kokouliss.listanything.core.ui.R as CoreR

// Which list a widget shows. Glance keeps one such record per widget.
private val SpaceKey = stringPreferencesKey("spaceId")
private val ListKey = stringPreferencesKey("listId")

/** Opens the app on [ref], with the same extras MainActivity reads from a tapped notification. */
private fun Context.openList(ref: ListRef, adding: Boolean) = Intent(this, MainActivity::class.java)
    .putExtra("spaceId", ref.spaceId)
    .putExtra("listId", ref.listId)
    .putExtra("adding", adding)
    // Into the app as it is, not a second copy of it: a running MainActivity gets onNewIntent.
    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)

// Glance and WorkManager create these objects themselves, so they ask Hilt for what they need.
@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface WidgetDependencies {
    fun itemRepository(): ItemRepository
    fun listRepository(): ListRepository
}

private fun Context.dependencies() = EntryPointAccessors.fromApplication(this, WidgetDependencies::class.java)

/** A widget's list once it is read, with the items still to do. */
private class Loaded(val list: SharedList, val items: List<ListItem>)

/** One list on the home screen: its open items, each with a box to tick, and a plus that opens the app on it. */
class ListWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)
        val ref = prefs[SpaceKey]?.let { space -> prefs[ListKey]?.let { ListRef(space, it) } }
        val loaded = ref?.let { load(context, it) }
        provideContent {
            GlanceTheme(colors = ColorProviders(light = LightColors, dark = DarkColors)) {
                Widget(ref, loaded)
            }
        }
    }

    /** Null when the list cannot be read now: signed out, deleted, or never seen by this phone while offline. */
    private suspend fun load(context: Context, ref: ListRef): Loaded? {
        if (!isBackendConfigured(context)) return null
        val deps = context.dependencies()
        return try {
            // Firestore answers from its copy on the device first, so this is quick even without a signal.
            withTimeoutOrNull(10_000) {
                val list = deps.listRepository().observeList(ref).first() ?: return@withTimeoutOrNull null
                Loaded(list, deps.itemRepository().observeItems(ref).first().openItems(list.sort))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }
}

@Composable
private fun Widget(ref: ListRef?, loaded: Loaded?) {
    val context = LocalContext.current
    // Material's titleMedium and bodyLarge, in the colours of the theme above: Glance cannot read a MaterialTheme.
    val title = TextStyle(GlanceTheme.colors.onSurface, 16.sp, FontWeight.Medium, fontFamily = FontFamily.Serif)
    val body = TextStyle(GlanceTheme.colors.onSurface, 16.sp)
    val hint = TextStyle(GlanceTheme.colors.onSurfaceVariant, 14.sp)
    Column(
        GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(GlanceTheme.colors.surface)
            .widgetCorners()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        when {
            ref == null -> Text(
                context.getString(R.string.widget_choose),
                GlanceModifier.fillMaxSize().clickable(actionStartActivity(Intent(context, MainActivity::class.java))),
                hint,
            )
            loaded == null -> Text(
                context.getString(R.string.widget_open_app),
                GlanceModifier.fillMaxSize().clickable(actionStartActivity(context.openList(ref, adding = false))),
                hint,
            )
            else -> {
                Row(
                    GlanceModifier.fillMaxWidth().clickable(actionStartActivity(context.openList(ref, adding = false))),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("${loaded.list.emoji} ${loaded.list.name}", GlanceModifier.defaultWeight(), title, maxLines = 1)
                    Image(
                        provider = ImageProvider(CoreR.drawable.core_ui_ic_add),
                        contentDescription = context.getString(R.string.widget_add),
                        modifier = GlanceModifier.padding(8.dp).clickable(actionStartActivity(context.openList(ref, adding = true))),
                        colorFilter = ColorFilter.tint(GlanceTheme.colors.primary),
                    )
                }
                if (loaded.items.isEmpty()) {
                    Text(context.getString(R.string.widget_all_done), GlanceModifier.padding(vertical = 8.dp), hint)
                } else {
                    LazyColumn {
                        items(loaded.items, itemId = { it.id.hashCode().toLong() }) { item ->
                            CheckBox(
                                checked = false,
                                onCheckedChange = actionRunCallback<CheckItemAction>(
                                    actionParametersOf(
                                        CheckItemAction.SpaceParam to ref.spaceId,
                                        CheckItemAction.ListParam to ref.listId,
                                        CheckItemAction.ItemParam to item.id,
                                    ),
                                ),
                                text = item.widgetText(),
                                style = body,
                                colors = CheckboxDefaults.colors(checkedColor = GlanceTheme.colors.primary, uncheckedColor = GlanceTheme.colors.outline),
                                maxLines = 2,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** The corners every widget on the phone has. Before Android 12 there is no such size, and no rounding. */
private fun GlanceModifier.widgetCorners() =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) cornerRadius(android.R.dimen.system_app_widget_background_radius) else this

/** Ticks the item whose box was tapped, then redraws the widget. */
class CheckItemAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val ref = ListRef(parameters[SpaceParam] ?: return, parameters[ListParam] ?: return)
        val itemId = parameters[ItemParam] ?: return
        try {
            context.dependencies().itemRepository().setChecked(ref, itemId, true)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Signed out: the redraw below says to open the app.
        }
        ListWidget().update(context, glanceId)
    }

    companion object {
        val SpaceParam = ActionParameters.Key<String>("spaceId")
        val ListParam = ActionParameters.Key<String>("listId")
        val ItemParam = ActionParameters.Key<String>("itemId")
    }
}

/** The receiver Android talks to about the widget. It also keeps the half-hourly redraw running while a widget exists. */
class ListWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ListWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        ListWidgetWorker.schedule(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WorkManager.getInstance(context).cancelUniqueWork(ListWidgetWorker.PERIODIC)
    }
}

/** Whether "Add to home screen" can do anything on this phone's launcher. */
fun canPinListWidget(context: Context): Boolean = AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported

/** Asks the launcher to place a widget of [ref]. The launcher answers through [PinnedListReceiver]. */
fun pinListWidget(context: Context, ref: ListRef) {
    val placed = Intent(context, PinnedListReceiver::class.java).putExtra("spaceId", ref.spaceId).putExtra("listId", ref.listId)
    // Mutable, because the launcher fills in the id of the new widget.
    val callback = PendingIntent.getBroadcast(context, ref.hashCode(), placed, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
    AppWidgetManager.getInstance(context).requestPinAppWidget(ComponentName(context, ListWidgetReceiver::class.java), null, callback)
}

/** Hears from the launcher that a widget was placed, and tells it which list it shows. */
class PinnedListReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val spaceId = intent.getStringExtra("spaceId") ?: return
        val listId = intent.getStringExtra("listId") ?: return
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
                updateAppWidgetState(context, glanceId) { prefs ->
                    prefs[SpaceKey] = spaceId
                    prefs[ListKey] = listId
                }
                ListWidget().update(context, glanceId)
            } finally {
                pending.finish()
            }
        }
    }
}

/** Redraws every list widget: every half hour while one exists, and soon after the app is left. */
internal class ListWidgetWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        ListWidget().updateAll(applicationContext)
        return Result.success()
    }

    companion object {
        const val PERIODIC = "list-widget"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ListWidgetWorker>(30, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        /** So what was ticked or added inside the app shows on the home screen right away. */
        fun refreshSoon(context: Context) {
            WorkManager.getInstance(context)
                .enqueueUniqueWork("list-widget-now", ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<ListWidgetWorker>().build())
        }
    }
}
