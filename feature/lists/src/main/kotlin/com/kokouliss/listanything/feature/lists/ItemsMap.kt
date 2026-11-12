package com.kokouliss.listanything.feature.lists

import android.graphics.Color as AndroidColor
import android.graphics.RectF
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LifecycleStartEffect
import com.kokouliss.listanything.core.domain.model.ItemLocation
import com.kokouliss.listanything.core.domain.model.ListItem
import com.kokouliss.listanything.core.ui.R as CoreR
import com.kokouliss.listanything.core.ui.component.AppIcon
import com.kokouliss.listanything.core.ui.component.AppIcons
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

// OpenStreetMap data served by OpenFreeMap: no API key, no account, no usage limit.
private const val STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"

private const val PIN_SOURCE = "list-anything-pins"
private const val PIN_LAYER = "list-anything-pin-circles"
private const val LABEL_LAYER = "list-anything-pin-labels"
private const val PROP_ITEM_ID = "itemId"
private const val PROP_TITLE = "title"

// shortcut: the app does not ask for the location permission, so a map with no pins opens on
// Greece instead of where the user is. Add the permission and a "my location" button when wanted.
private val DEFAULT_CENTER = LatLng(38.3, 24.0)
private const val DEFAULT_ZOOM = 5.0
private const val SINGLE_PIN_ZOOM = 15.0
private const val BOUNDS_PADDING_PX = 140

private class MapHandle(val map: MapLibreMap, val pins: GeoJsonSource)

/** The items of a list that have a place, as pins. Tapping a pin reports its item. */
@Composable
internal fun PinsMap(items: List<ListItem>, onPinClick: (itemId: String) -> Unit, modifier: Modifier = Modifier) {
    var handle by remember { mutableStateOf<MapHandle?>(null) }
    val currentOnPinClick by rememberUpdatedState(onPinClick)
    val currentItems by rememberUpdatedState(items)
    val description = stringResource(R.string.feature_lists_map_description)
    // A pin is 18 dp wide, so a tap counts within the 48 dp a finger needs.
    val reach = with(LocalDensity.current) { 24.dp.toPx() }

    MapContainer(modifier.semantics { contentDescription = description }) { map, pins ->
        map.addOnMapClickListener { point ->
            val at = map.projection.toScreenLocation(point)
            val hit = map.queryRenderedFeatures(RectF(at.x - reach, at.y - reach, at.x + reach, at.y + reach), PIN_LAYER, LABEL_LAYER)
                .firstOrNull { it.hasNonNullValueForProperty(PROP_ITEM_ID) }
            hit?.let { currentOnPinClick(it.getStringProperty(PROP_ITEM_ID)) }
            hit != null
        }
        // Framed once: later edits by other people must not move the map under the user's finger.
        map.frame(currentItems.mapNotNull { it.location?.toLatLng() })
        handle = MapHandle(map, pins)
    }

    LaunchedEffect(handle, items) {
        handle?.pins?.setGeoJson(
            FeatureCollection.fromFeatures(
                items.mapNotNull { item -> item.location?.toFeature(itemId = item.id, title = item.text) },
            ),
        )
    }
}

/**
 * Full-screen map to choose the place of an item. Tapping a named spot on the map (a shop, a
 * restaurant, a village) keeps that name with the pin.
 */
@Composable
internal fun LocationPicker(
    initial: ItemLocation?,
    /** Where to open the map when the item has no place yet, for example another pin of the list. */
    nearby: ItemLocation?,
    onConfirm: (ItemLocation) -> Unit,
    onCancel: () -> Unit,
) {
    var picked by rememberSaveable(stateSaver = LocationSaver) { mutableStateOf(initial) }
    var handle by remember { mutableStateOf<MapHandle?>(null) }
    BackHandler(onBack = onCancel)
    val title = stringResource(R.string.feature_lists_pick_place_title)

    Scaffold(
        modifier = Modifier.semantics { paneTitle = title },
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        AppIcon(AppIcons.Close, stringResource(CoreR.string.core_ui_cancel))
                    }
                },
                actions = {
                    TextButton(onClick = { picked?.let(onConfirm) }, enabled = picked != null) {
                        Text(stringResource(CoreR.string.core_ui_save))
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            MapContainer(Modifier.fillMaxSize()) { map, pins ->
                map.addOnMapClickListener { point ->
                    picked = ItemLocation(point.latitude, point.longitude, map.placeNameAt(point))
                    true
                }
                map.frame(listOfNotNull((initial ?: nearby)?.toLatLng()))
                handle = MapHandle(map, pins)
            }
            Surface(
                shape = MaterialTheme.shapes.large,
                tonalElevation = 3.dp,
                shadowElevation = 3.dp,
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
            ) {
                Column(
                    Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(
                            if (picked == null) R.string.feature_lists_pick_place_hint else R.string.feature_lists_pin_dropped,
                        ),
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                    // The name can be typed as well as taken from the map.
                    picked?.let { place ->
                        OutlinedTextField(
                            value = place.name.orEmpty(),
                            onValueChange = { picked = place.copy(name = it.take(MAX_PLACE_NAME_LENGTH).ifBlank { null }) },
                            label = { Text(stringResource(R.string.feature_lists_place_name_label)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }

    LaunchedEffect(handle, picked) {
        handle?.pins?.setGeoJson(
            FeatureCollection.fromFeatures(listOfNotNull(picked?.toFeature(itemId = null, title = picked?.name))),
        )
    }
}

/** Keeps the picked place across a rotation. */
private val LocationSaver = listSaver<ItemLocation?, Any?>(
    save = { place -> place?.let { listOf(it.latitude, it.longitude, it.name) } ?: emptyList() },
    restore = { saved -> if (saved.isEmpty()) null else ItemLocation(saved[0] as Double, saved[1] as Double, saved[2] as String?) },
)

/**
 * Hosts a MapLibre [MapView] and forwards the lifecycle to it. [onReady] runs once, after the map
 * style and the pin layers are in place. Without a connection the style never loads and the map
 * stays empty.
 */
@Composable
private fun MapContainer(modifier: Modifier, onReady: (MapLibreMap, GeoJsonSource) -> Unit) {
    val context = LocalContext.current
    val pinColor = MaterialTheme.colorScheme.primary.toArgb()
    val currentOnReady by rememberUpdatedState(onReady)
    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context).also { it.onCreate(null) }
    }

    // Declared first so that it is disposed last: pause and stop must come before destroy.
    DisposableEffect(mapView) {
        mapView.getMapAsync { map ->
            map.setStyle(Style.Builder().fromUri(STYLE_URL)) { style ->
                currentOnReady(map, style.addPinLayers(pinColor))
            }
        }
        onDispose { mapView.onDestroy() }
    }
    LifecycleStartEffect(mapView) {
        mapView.onStart()
        onStopOrDispose { mapView.onStop() }
    }
    LifecycleResumeEffect(mapView) {
        mapView.onResume()
        onPauseOrDispose { mapView.onPause() }
    }
    AndroidView(factory = { mapView }, modifier = modifier)
}

private fun Style.addPinLayers(pinColor: Int): GeoJsonSource {
    val source = GeoJsonSource(PIN_SOURCE)
    addSource(source)
    addLayer(
        CircleLayer(PIN_LAYER, PIN_SOURCE).withProperties(
            PropertyFactory.circleRadius(9f),
            PropertyFactory.circleColor(pinColor),
            PropertyFactory.circleStrokeColor(AndroidColor.WHITE),
            PropertyFactory.circleStrokeWidth(2.5f),
        ),
    )
    addLayer(
        SymbolLayer(LABEL_LAYER, PIN_SOURCE).withProperties(
            PropertyFactory.textField(Expression.get(PROP_TITLE)),
            // A font the map style already ships, so no extra download is needed.
            PropertyFactory.textFont(arrayOf("Noto Sans Regular")),
            PropertyFactory.textSize(13f),
            PropertyFactory.textMaxWidth(9f),
            PropertyFactory.textAnchor(Property.TEXT_ANCHOR_TOP),
            PropertyFactory.textOffset(arrayOf(0f, 0.9f)),
            PropertyFactory.textHaloColor(AndroidColor.WHITE),
            PropertyFactory.textHaloWidth(1.5f),
            // The circle always shows; its label gives way when pins crowd each other.
            PropertyFactory.textOptional(true),
        ),
    )
    return source
}

private fun MapLibreMap.frame(points: List<LatLng>) {
    when (points.size) {
        0 -> moveCamera(CameraUpdateFactory.newLatLngZoom(DEFAULT_CENTER, DEFAULT_ZOOM))
        1 -> moveCamera(CameraUpdateFactory.newLatLngZoom(points.first(), SINGLE_PIN_ZOOM))
        else -> {
            moveCamera(
                CameraUpdateFactory.newLatLngBounds(LatLngBounds.Builder().includes(points).build(), BOUNDS_PADDING_PX),
            )
            // Pins that share one spot would otherwise zoom in to the pavement.
            if (cameraPosition.zoom > SINGLE_PIN_ZOOM) moveCamera(CameraUpdateFactory.zoomTo(SINGLE_PIN_ZOOM))
        }
    }
}

/** Name of the map label or point of interest under [point], if there is one. */
private fun MapLibreMap.placeNameAt(point: LatLng): String? =
    queryRenderedFeatures(projection.toScreenLocation(point))
        // Points only: roads and areas also carry names, but "the pin is on Main Street" is not a place.
        .firstOrNull { it.geometry() is Point && it.hasNonNullValueForProperty("name") }
        ?.getStringProperty("name")
        ?.take(MAX_PLACE_NAME_LENGTH)

private fun ItemLocation.toLatLng() = LatLng(latitude, longitude)

private fun ItemLocation.toFeature(itemId: String?, title: String?): Feature =
    Feature.fromGeometry(Point.fromLngLat(longitude, latitude)).apply {
        itemId?.let { addStringProperty(PROP_ITEM_ID, it) }
        title?.let { addStringProperty(PROP_TITLE, it) }
    }
