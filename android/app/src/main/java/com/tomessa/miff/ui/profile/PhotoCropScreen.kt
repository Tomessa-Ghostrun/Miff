package com.tomessa.miff.ui.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.tomessa.miff.R
import com.tomessa.miff.data.profile.BitmapPhoto
import com.tomessa.miff.domain.profile.CropMath
import com.tomessa.miff.ui.auth.LoadingIndicator
import com.tomessa.miff.ui.components.MiffButton
import com.tomessa.miff.ui.components.miffScreenInsets
import com.tomessa.miff.ui.theme.GradientAuthBackground
import com.tomessa.miff.ui.theme.ShapeMd
import com.tomessa.miff.ui.theme.Spacing
import com.tomessa.miff.ui.theme.colorActionPrimary
import com.tomessa.miff.ui.theme.colorActionPrimaryTint30
import com.tomessa.miff.ui.theme.colorTextDescription
import com.tomessa.miff.ui.theme.colorTextForm
import com.tomessa.miff.ui.theme.textBodyBold16
import com.tomessa.miff.ui.theme.textBodyRegular14
import com.tomessa.miff.ui.theme.textTitleBold20

/** Share of the square moved by one accessibility action ("move left"...). */
private const val MOVE_STEP = 0.1f

/** The crop is shown as a square window; the white veil and the ring show the circle the avatar will have in the app. */
private val VIEWPORT_MAX = 360.dp

/**
 * Square crop of the profile photo (Onboarding ticket 6.3: crop REQUIRED before validating; not drawn in the mockups, so
 * built with the same look as the other screens). Drag to place, pinch or slide to zoom; the square never leaves the photo.
 * For the screen reader the same moves are available as actions and as a slider. The state lives in the view model: a
 * rotation of the phone keeps it. The system "back" closes this screen without keeping anything.
 */
@Composable
fun PhotoCropScreen(
    crop: CropState,
    onChanged: (zoom: Float, centerX: Float, centerY: Float) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    BackHandler(onBack = onCancel)
    val photo = crop.photo
    val current by rememberUpdatedState(crop)
    val title = stringResource(R.string.profile_crop_title)
    val image = remember(photo) { (photo as? BitmapPhoto)?.bitmap?.asImageBitmap() }
    val moveLeft = stringResource(R.string.profile_crop_move_left)
    val moveRight = stringResource(R.string.profile_crop_move_right)
    val moveUp = stringResource(R.string.profile_crop_move_up)
    val moveDown = stringResource(R.string.profile_crop_move_down)
    val area = stringResource(R.string.profile_crop_area_a11y)
    val zoomLabel = stringResource(R.string.profile_crop_zoom_a11y)

    fun move(
        dx: Float,
        dy: Float,
    ): Boolean {
        val c = current
        val step = CropMath.sideAtZoom(photo.width, photo.height, c.zoom) * MOVE_STEP
        onChanged(c.zoom, c.centerX + dx * step, c.centerY + dy * step)
        return true
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                // Absorbs the touches: nothing under this screen can be reached (it is also hidden from TalkBack).
                .pointerInput(Unit) {}
                .background(GradientAuthBackground)
                .miffScreenInsets()
                .verticalScroll(rememberScrollState())
                .semantics { paneTitle = title }
                .padding(horizontal = Spacing.l, vertical = Spacing.m),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Text(
            text = title,
            style = textTitleBold20,
            color = colorTextForm,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().semantics { heading() },
        )
        Text(
            text = stringResource(R.string.profile_crop_hint),
            style = textBodyRegular14,
            color = colorTextDescription,
            textAlign = TextAlign.Center,
        )
        BoxWithConstraints(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val side = minOf(maxWidth, VIEWPORT_MAX)
            Canvas(
                modifier =
                    Modifier
                        .size(side)
                        .clip(ShapeMd)
                        .semantics {
                            contentDescription = area
                            customActions =
                                listOf(
                                    CustomAccessibilityAction(moveLeft) { move(-1f, 0f) },
                                    CustomAccessibilityAction(moveRight) { move(1f, 0f) },
                                    CustomAccessibilityAction(moveUp) { move(0f, -1f) },
                                    CustomAccessibilityAction(moveDown) { move(0f, 1f) },
                                )
                        }.pointerInput(photo) {
                            detectTransformGestures { _, pan, zoomChange, _ ->
                                val c = current
                                val zoom = CropMath.clampZoom(c.zoom * zoomChange)
                                // Screen pixels -> photo pixels: the square of the photo fills the whole window.
                                val sourcePerScreenPixel =
                                    CropMath.sideAtZoom(photo.width, photo.height, zoom) / size.width
                                onChanged(
                                    zoom,
                                    c.centerX - pan.x * sourcePerScreenPixel,
                                    c.centerY - pan.y * sourcePerScreenPixel,
                                )
                            }
                        },
            ) {
                val bitmap = image
                if (bitmap != null) {
                    val zone = CropMath.toCrop(photo.width, photo.height, crop.zoom, crop.centerX, crop.centerY)
                    drawImage(
                        image = bitmap,
                        srcOffset = IntOffset(zone.left, zone.top),
                        srcSize = IntSize(zone.size, zone.size),
                        dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                        filterQuality = FilterQuality.Medium,
                    )
                }
                val circle = Path().apply { addOval(Rect(Offset.Zero, size)) }
                clipPath(circle, clipOp = ClipOp.Difference) { drawRect(Color.White.copy(alpha = 0.6f)) }
                drawCircle(
                    color = Color.White,
                    radius = size.minDimension / 2f,
                    style = Stroke(width = 2.dp.toPx()),
                )
            }
        }
        Slider(
            value = crop.zoom,
            onValueChange = { onChanged(it, crop.centerX, crop.centerY) },
            valueRange = CropMath.MIN_ZOOM..CropMath.MAX_ZOOM,
            enabled = !crop.isEncoding,
            colors =
                SliderDefaults.colors(
                    thumbColor = colorActionPrimary,
                    activeTrackColor = colorActionPrimary,
                    inactiveTrackColor = colorActionPrimaryTint30,
                ),
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = zoomLabel },
        )
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (crop.isEncoding) LoadingIndicator()
        }
        MiffButton(
            text = stringResource(R.string.profile_crop_confirm),
            onClick = onConfirm,
            enabled = !crop.isEncoding && image != null,
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(onClick = onCancel, enabled = !crop.isEncoding) {
            Text(
                text = stringResource(R.string.profile_crop_cancel),
                style = textBodyBold16,
                color = colorActionPrimary,
            )
        }
        Spacer(Modifier.size(Spacing.s))
    }
}
