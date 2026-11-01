package com.tomessa.miff.ui.place

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.tomessa.miff.BuildConfig
import com.tomessa.miff.R
import com.tomessa.miff.domain.place.PlaceMapGeometry
import com.tomessa.miff.ui.theme.ShapeMd
import com.tomessa.miff.ui.theme.Spacing
import com.tomessa.miff.ui.theme.colorActionPrimary
import com.tomessa.miff.ui.theme.colorActionPrimaryTint10
import com.tomessa.miff.ui.theme.colorBgSurface
import com.tomessa.miff.ui.theme.colorBorderDefault
import com.tomessa.miff.ui.theme.colorStatusDanger
import com.tomessa.miff.ui.theme.colorTextDescription
import com.tomessa.miff.ui.theme.textBodyCaption
import com.tomessa.miff.ui.theme.textBodyRegular14
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource

/*
 * SECURITY, for this file: the position of a place is sensitive. It is given to the map (tiles from OpenFreeMap: the server sees
 * which tiles are asked, that is the area on screen, and the IP address of the phone) and nowhere else; nothing is logged. The map
 * is a still picture: no gesture, no "my position" layer, no permission. The style address is https only (BuildConfig).
 */

/** What the map draws: a point and a circle. [description] is what a screen reader says instead of the picture. */
class PlaceMapSpec(
    val lat: Double,
    val lng: Double,
    val radiusM: Int,
    val danger: Boolean,
    val description: String,
) {
    override fun toString(): String = "PlaceMapSpec(redacted)"
}

/**
 * How the map is drawn. The real one is MapLibre; the screen tests (Robolectric, no native library) give a stand-in. One place to
 * replace the map provider (ticket 12: "le fournisseur de tuiles doit être remplaçable par un réglage": [BuildConfig.PLACE_MAP_STYLE_URL]).
 */
val LocalPlaceMapRenderer =
    staticCompositionLocalOf<@Composable (spec: PlaceMapSpec, onFailed: () -> Unit, modifier: Modifier) -> Unit> {
        { spec, onFailed, modifier -> MapLibrePlaceMap(spec, onFailed, modifier) }
    }

/**
 * The map of a place with its perimeter, in a box of [height]: the picture, or (no address chosen yet) a striped background that
 * says so, or (the provider does not answer) a calm sentence. The attribution required by OpenFreeMap is always drawn.
 * The place stays usable without the map: the address and the slider are enough (criterion TRA2).
 */
@Composable
fun PlaceMapBox(
    spec: PlaceMapSpec?,
    height: Dp,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var failed by remember(spec?.lat, spec?.lng) { mutableStateOf(false) }
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(height)
                .clip(ShapeMd)
                .background(colorActionPrimaryTint10),
    ) {
        when {
            spec == null -> {
                Hatched()
                Text(
                    text = stringResource(R.string.place_radius_pick_address),
                    style = textBodyRegular14,
                    color = colorTextDescription,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(horizontal = Spacing.l),
                )
            }

            failed -> {
                Hatched()
                Column(
                    modifier = Modifier.align(Alignment.Center).padding(horizontal = Spacing.l),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_loc_cloud_off),
                        contentDescription = null,
                        tint = colorTextDescription,
                        modifier = Modifier.size(Spacing.l),
                    )
                    Text(
                        text = stringResource(R.string.place_map_unavailable),
                        style = textBodyRegular14,
                        color = colorTextDescription,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            else -> {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .clearAndSetSemantics { contentDescription = spec.description },
                ) {
                    LocalPlaceMapRenderer.current(spec, { failed = true }, Modifier.fillMaxSize())
                }
                Text(
                    text = stringResource(R.string.place_map_attribution),
                    style = textBodyCaption,
                    color = colorTextDescription,
                    modifier =
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(Spacing.xs)
                            .clip(ShapeMd)
                            .background(colorBgSurface.copy(alpha = 0.85f))
                            .padding(horizontal = Spacing.xs, vertical = 2.dp)
                            .clearAndSetSemantics {},
                )
            }
        }
        if (!enabled) {
            // The map stays visible but waits for the address (greyed, ticket design: "carte et curseur grisés").
            Box(Modifier.fillMaxSize().background(colorBgSurface.copy(alpha = 0.5f)))
        }
    }
}

/** Diagonal stripes: "nothing to show yet" (same look as the mockups). */
@Composable
private fun Hatched() {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .drawBehind {
                    val step = 14.dp.toPx()
                    var x = -size.height
                    while (x < size.width) {
                        drawLine(
                            color = colorBorderDefault,
                            start = Offset(x, size.height),
                            end = Offset(x + size.height, 0f),
                            strokeWidth = 2.dp.toPx(),
                        )
                        x += step
                    }
                },
    )
}

// ---- MapLibre --------------------------------------------------------------------------------------------------------

private const val SOURCE_AREA = "miff-place-area"
private const val SOURCE_CENTER = "miff-place-center"
private const val LAYER_FILL = "miff-place-fill"
private const val LAYER_LINE = "miff-place-line"
private const val LAYER_CENTER = "miff-place-center-dot"
private const val CAMERA_PADDING_PX = 48

/** What the map of one composable holds between two redraws. */
private class MapHolder {
    var map: MapLibreMap? = null
    var styleReady = false
    var styleRequested = false
    var spec: PlaceMapSpec? = null
}

@Composable
private fun MapLibrePlaceMap(
    spec: PlaceMapSpec,
    onFailed: () -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val holder = remember { MapHolder() }
    val mapView =
        remember {
            // One call per process: sets the application context (no key, no account, no telemetry in MapLibre Native).
            MapLibre.getInstance(context.applicationContext)
            MapView(
                context,
                MapLibreMapOptions
                    .createFromAttributes(context)
                    .textureMode(true) // drawn in the view hierarchy: it scrolls and clips like any other element
                    .attributionEnabled(false) // the attribution is drawn by PlaceMapBox (always visible)
                    .logoEnabled(false)
                    .compassEnabled(false),
            )
        }
    DisposableEffect(lifecycle, mapView) {
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_CREATE -> mapView.onCreate(null)
                    Lifecycle.Event.ON_START -> mapView.onStart()
                    Lifecycle.Event.ON_RESUME -> mapView.onResume()
                    Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                    Lifecycle.Event.ON_STOP -> mapView.onStop()
                    Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                    else -> Unit
                }
            }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            // The screen leaves while the activity lives on: bring the view down in order (nothing more to do after DESTROYED).
            val state = lifecycle.currentState
            if (state != Lifecycle.State.DESTROYED) {
                if (state.isAtLeast(Lifecycle.State.RESUMED)) mapView.onPause()
                if (state.isAtLeast(Lifecycle.State.STARTED)) mapView.onStop()
                mapView.onDestroy()
            }
        }
    }
    DisposableEffect(mapView) {
        val failure = MapView.OnDidFailLoadingMapListener { onFailed() }
        mapView.addOnDidFailLoadingMapListener(failure)
        onDispose { mapView.removeOnDidFailLoadingMapListener(failure) }
    }
    AndroidView(
        factory = { mapView },
        modifier = modifier,
        update = { view ->
            holder.spec = spec
            val ready = holder.map
            if (ready != null) {
                render(holder, ready)
            } else {
                view.getMapAsync { map ->
                    holder.map = map
                    map.uiSettings.setAllGesturesEnabled(false)
                    map.uiSettings.isAttributionEnabled = false
                    map.uiSettings.isLogoEnabled = false
                    map.uiSettings.isCompassEnabled = false
                    render(holder, map)
                }
            }
        },
    )
}

/** Draws the current spec: loads the style once, then only moves the shape and the camera. */
private fun render(
    holder: MapHolder,
    map: MapLibreMap,
) {
    val spec = holder.spec ?: return
    if (!holder.styleReady) {
        if (holder.styleRequested) return
        holder.styleRequested = true
        map.setStyle(Style.Builder().fromUri(BuildConfig.PLACE_MAP_STYLE_URL)) { style ->
            holder.styleReady = true
            val current = holder.spec ?: spec
            installLayers(style, current)
            moveCamera(map, current)
        }
        return
    }
    val style = map.style ?: return
    (
        style.getSource(
            SOURCE_AREA,
        ) as? GeoJsonSource
    )?.setGeoJson(PlaceMapGeometry.areaGeoJson(spec.lat, spec.lng, spec.radiusM))
    (style.getSource(SOURCE_CENTER) as? GeoJsonSource)?.setGeoJson(PlaceMapGeometry.pointGeoJson(spec.lat, spec.lng))
    colorLayers(style, spec)
    moveCamera(map, spec)
}

private fun installLayers(
    style: Style,
    spec: PlaceMapSpec,
) {
    style.addSource(GeoJsonSource(SOURCE_AREA, PlaceMapGeometry.areaGeoJson(spec.lat, spec.lng, spec.radiusM)))
    style.addSource(GeoJsonSource(SOURCE_CENTER, PlaceMapGeometry.pointGeoJson(spec.lat, spec.lng)))
    style.addLayer(FillLayer(LAYER_FILL, SOURCE_AREA).withProperties(PropertyFactory.fillOpacity(FILL_OPACITY)))
    style.addLayer(
        LineLayer(LAYER_LINE, SOURCE_AREA).withProperties(
            PropertyFactory.lineWidth(2f),
            PropertyFactory.lineDasharray(arrayOf(2f, 2f)),
        ),
    )
    style.addLayer(
        CircleLayer(LAYER_CENTER, SOURCE_CENTER).withProperties(
            PropertyFactory.circleRadius(CENTER_RADIUS),
            PropertyFactory.circleStrokeColor(android.graphics.Color.WHITE),
            PropertyFactory.circleStrokeWidth(2f),
        ),
    )
    colorLayers(style, spec)
}

private fun colorLayers(
    style: Style,
    spec: PlaceMapSpec,
) {
    val color = (if (spec.danger) colorStatusDanger else colorActionPrimary).toArgb()
    style.getLayer(LAYER_FILL)?.setProperties(PropertyFactory.fillColor(color))
    style.getLayer(LAYER_LINE)?.setProperties(PropertyFactory.lineColor(color))
    style.getLayer(LAYER_CENTER)?.setProperties(PropertyFactory.circleColor(color))
}

/** Frames the whole circle: the map moves away by itself when the radius grows (design 20C). */
private fun moveCamera(
    map: MapLibreMap,
    spec: PlaceMapSpec,
) {
    val edges = PlaceMapGeometry.bounds(PlaceMapGeometry.ring(spec.lat, spec.lng, spec.radiusM))
    val bounds =
        LatLngBounds
            .Builder()
            .include(LatLng(edges[0], edges[1]))
            .include(LatLng(edges[2], edges[3]))
            .build()
    try {
        map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, CAMERA_PADDING_PX))
    } catch (e: IllegalStateException) {
        // The view has no size yet: the next redraw frames it.
    }
}

private const val FILL_OPACITY = 0.18f
private const val CENTER_RADIUS = 6f
