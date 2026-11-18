package com.tomessa.miff.ui.place

import android.graphics.PointF
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.tomessa.miff.domain.place.PlaceMapZoom
import com.tomessa.miff.ui.theme.ShapeMd
import com.tomessa.miff.ui.theme.Spacing
import com.tomessa.miff.ui.theme.colorActionPrimary
import com.tomessa.miff.ui.theme.colorActionPrimaryTint10
import com.tomessa.miff.ui.theme.colorBgSurface
import com.tomessa.miff.ui.theme.colorBorderDefault
import com.tomessa.miff.ui.theme.colorStatusDanger
import com.tomessa.miff.ui.theme.colorTextDescription
import com.tomessa.miff.ui.theme.textBodyBold16
import com.tomessa.miff.ui.theme.textBodyCaption
import com.tomessa.miff.ui.theme.textBodyRegular14
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.constants.MapLibreConstants
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
 * has no "my position" layer and no permission. In the form (and only there) it can be ZOOMED (pinch, double tap): nothing else
 * moves it (no drag, no rotation, no tilt), so a gesture can never change the place or its radius, and zooming out only asks more
 * tiles of the same provider (same data as before: tiles and IP address). The style address is https only (BuildConfig).
 */

/**
 * What the map draws: a point and a circle. [description] is what a screen reader says instead of the picture. [zoomable]: the
 * person may pinch or double-tap to zoom (the form and the detail of a place); otherwise the map is a still picture.
 */
class PlaceMapSpec(
    val lat: Double,
    val lng: Double,
    val radiusM: Int,
    val danger: Boolean,
    val description: String,
    val zoomable: Boolean = false,
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
    // "Réessayer": the map is built again from scratch (a new view, a new request of the style).
    var attempt by remember { mutableIntStateOf(0) }
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
                    TextButton(
                        onClick = {
                            attempt++
                            failed = false
                        },
                        modifier = Modifier.heightIn(min = Spacing.minTouchTarget),
                    ) {
                        Text(
                            stringResource(R.string.place_map_retry_action),
                            style = textBodyBold16,
                            color = colorActionPrimary,
                        )
                    }
                }
            }

            else -> {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .clearAndSetSemantics { contentDescription = spec.description },
                ) {
                    key(attempt) { LocalPlaceMapRenderer.current(spec, { failed = true }, Modifier.fillMaxSize()) }
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

/** What the map was last framed for: when it changes (address, radius), the map is framed again; a zoom alone does not change it. */
private class FrameKey(
    val lat: Double,
    val lng: Double,
    val radiusM: Int,
) {
    override fun equals(other: Any?): Boolean =
        other is FrameKey && other.lat == lat && other.lng == lng && other.radiusM == radiusM

    override fun hashCode(): Int = 31 * (31 * lat.hashCode() + lng.hashCode()) + radiusM

    override fun toString(): String = "FrameKey(redacted)"
}

/**
 * What the map of one composable holds between two redraws. [zoomOffset] is the zoom the person chose, in levels above (+) or below
 * (-) the framing of the circle: it survives a rotation or a trip to another screen (saved by the composable), and goes back to 0
 * when the circle is framed again.
 */
private class MapHolder(
    val zoomOffset: MutableFloatState,
) {
    var map: MapLibreMap? = null
    var styleReady = false
    var styleRequested = false
    var spec: PlaceMapSpec? = null
    var framedKey: FrameKey? = null
    var fitZoom = Double.NaN

    /** The box of the map: a map of size 0 cannot be framed (the layout listener tries again when it has a size). */
    var view: android.view.View? = null

    /** The zoom limits in force (the ones of the last framing), given back if a new framing cannot be measured. */
    var range: PlaceMapZoom.Range? = null
}

@Composable
private fun MapLibrePlaceMap(
    spec: PlaceMapSpec,
    onFailed: () -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val savedOffset = rememberSaveable { mutableFloatStateOf(0f) }
    val holder = remember { MapHolder(savedOffset) }
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
    val frame =
        remember(mapView) {
            PinchFriendlyFrame(context).apply {
                addView(mapView, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
                holder.view = this
                // The first layout (or a rotation) gives the map its size: the circle is framed then if it could not be before, and
                // the zoom stays centred on the place (the focal point of the gestures is the middle of the view).
                addOnLayoutChangeListener { _, left, top, right, bottom, _, _, _, _ ->
                    val map = holder.map ?: return@addOnLayoutChangeListener
                    map.uiSettings.focalPoint = PointF((right - left) / 2f, (bottom - top) / 2f)
                    if (holder.framedKey == null) render(holder, map)
                }
            }
        }
    AndroidView(
        factory = { frame },
        modifier = modifier,
        update = {
            holder.spec = spec
            val ready = holder.map
            if (ready != null) {
                render(holder, ready)
            } else {
                mapView.getMapAsync { map ->
                    holder.map = map
                    map.uiSettings.isAttributionEnabled = false
                    map.uiSettings.isLogoEnabled = false
                    map.uiSettings.isCompassEnabled = false
                    map.uiSettings.focalPoint = PointF(frame.width / 2f, frame.height / 2f)
                    map.addOnCameraIdleListener { rememberZoom(holder, map) }
                    render(holder, map)
                }
            }
        },
    )
}

/**
 * The gestures of the map. Zoom only (pinch, double tap; the two-finger tap that zooms out comes with it): no drag (the place stays in
 * the middle, nothing can move it by accident), no rotation, no tilt, and no "double tap then slide" (it would fight the scrolling
 * of the page). Without [zoomable], the map is a still picture.
 */
private fun applyGestures(
    map: MapLibreMap,
    zoomable: Boolean,
) {
    val ui = map.uiSettings
    ui.setAllGesturesEnabled(false)
    if (zoomable) {
        ui.isZoomGesturesEnabled = true
        ui.isDoubleTapGesturesEnabled = true
    }
}

/** Keeps the zoom the person chose, relative to the framing, so that it comes back after a rotation. */
private fun rememberZoom(
    holder: MapHolder,
    map: MapLibreMap,
) {
    if (holder.fitZoom.isNaN()) return
    holder.zoomOffset.floatValue = PlaceMapZoom.offsetOf(map.cameraPosition.zoom, holder.fitZoom).toFloat()
}

/** Draws the current spec: loads the style once, then only moves the shape and the camera. */
private fun render(
    holder: MapHolder,
    map: MapLibreMap,
) {
    val spec = holder.spec ?: return
    applyGestures(map, spec.zoomable)
    if (!holder.styleReady) {
        if (holder.styleRequested) return
        holder.styleRequested = true
        map.setStyle(Style.Builder().fromUri(BuildConfig.PLACE_MAP_STYLE_URL)) { style ->
            holder.styleReady = true
            val current = holder.spec ?: spec
            installLayers(style, current)
            frameCircle(holder, map, current)
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
    frameCircle(holder, map, spec)
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

/**
 * Frames the whole circle, centred on the place (design 20C: the map moves away by itself when the radius grows), then sets how far
 * the person may zoom out and in around that framing ([PlaceMapZoom]). Done once per (place, radius): a zoom is never undone by a
 * redraw. The first time (a new view after a rotation) the zoom chosen before is given back.
 */
private fun frameCircle(
    holder: MapHolder,
    map: MapLibreMap,
    spec: PlaceMapSpec,
) {
    val key = FrameKey(spec.lat, spec.lng, spec.radiusM)
    if (holder.framedKey == key) return
    // A map of size 0 cannot be measured: leave everything as it is, the layout listener tries again when it has a size.
    val view = holder.view
    if (view == null || view.width <= 0 || view.height <= 0) return
    val edges = PlaceMapGeometry.bounds(PlaceMapGeometry.ring(spec.lat, spec.lng, spec.radiusM))
    val bounds =
        LatLngBounds
            .Builder()
            .include(LatLng(edges[0], edges[1]))
            .include(LatLng(edges[2], edges[3]))
            .build()
    val previous = holder.range
    try {
        // Free range first: the framing must be measured without the limits of the previous circle.
        map.setMinZoomPreference(MapLibreConstants.MINIMUM_ZOOM.toDouble())
        map.setMaxZoomPreference(MapLibreConstants.MAXIMUM_ZOOM.toDouble())
        val padding = CAMERA_PADDING_PX
        val measured = map.getCameraForLatLngBounds(bounds, intArrayOf(padding, padding, padding, padding))?.zoom
        val plan = PlaceMapZoom.plan(measured, restoring = holder.framedKey == null, savedOffset = holder.zoomOffset.floatValue.toDouble())
        if (plan == null) {
            // Not measurable (no size yet, or an absurd answer): NOTHING is recorded, so that the next layout or redraw tries again,
            // and the limits that were in force stay in force.
            previous?.let {
                map.setMinZoomPreference(it.min)
                map.setMaxZoomPreference(it.max)
            }
            return
        }
        map.setMinZoomPreference(plan.range.min)
        map.setMaxZoomPreference(plan.range.max)
        map.moveCamera(
            CameraUpdateFactory.newCameraPosition(
                CameraPosition
                    .Builder()
                    .target(LatLng(spec.lat, spec.lng))
                    .zoom(plan.wanted)
                    .bearing(0.0)
                    .tilt(0.0)
                    .build(),
            ),
        )
        val first = holder.framedKey == null
        holder.range = plan.range
        holder.fitZoom = plan.range.fit
        holder.framedKey = key
        if (!first) holder.zoomOffset.floatValue = 0f
    } catch (e: IllegalStateException) {
        // The view has no size yet: nothing is recorded, the layout listener frames it as soon as it has one.
        previous?.let {
            map.setMinZoomPreference(it.min)
            map.setMaxZoomPreference(it.max)
        }
    }
}

private const val FILL_OPACITY = 0.18f
private const val CENTER_RADIUS = 6f
