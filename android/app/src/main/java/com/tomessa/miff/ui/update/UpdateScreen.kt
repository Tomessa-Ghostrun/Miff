package com.tomessa.miff.ui.update

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tomessa.miff.R
import com.tomessa.miff.data.profile.AvatarCapture
import com.tomessa.miff.data.profile.BitmapPhoto
import com.tomessa.miff.data.update.AndroidPhotoEncoder
import com.tomessa.miff.domain.profile.AvatarCodec
import com.tomessa.miff.domain.update.UpdatePhoto
import com.tomessa.miff.domain.update.UpdatePhotoFormat
import com.tomessa.miff.ui.components.MiffButton
import com.tomessa.miff.ui.components.MiffSecondaryButton
import com.tomessa.miff.ui.components.RecentsPrivacy
import com.tomessa.miff.ui.components.miffTopInsets
import com.tomessa.miff.ui.theme.ShadowDefault
import com.tomessa.miff.ui.theme.ShapeMd
import com.tomessa.miff.ui.theme.Spacing
import com.tomessa.miff.ui.theme.StrokeAccent
import com.tomessa.miff.ui.theme.colorActionPrimary
import com.tomessa.miff.ui.theme.colorActionPrimaryTint30
import com.tomessa.miff.ui.theme.colorBgCanvas
import com.tomessa.miff.ui.theme.colorBgSurface
import com.tomessa.miff.ui.theme.colorBorderAction
import com.tomessa.miff.ui.theme.colorBrandPrimary
import com.tomessa.miff.ui.theme.colorTextOnAction
import com.tomessa.miff.ui.theme.colorTextPrimary
import com.tomessa.miff.ui.theme.colorUpdateScrim
import com.tomessa.miff.ui.theme.colorUpdateStage
import com.tomessa.miff.ui.theme.colorUpdateVeil
import com.tomessa.miff.ui.theme.miffShadow
import com.tomessa.miff.ui.theme.textBodyBold14
import com.tomessa.miff.ui.theme.textBodyLarge
import com.tomessa.miff.ui.theme.textBodyRegular
import com.tomessa.miff.ui.theme.textBodyRegular14
import com.tomessa.miff.ui.theme.textTitleBold20
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/*
 * The Update screen (tickets UPDATE-1 and UPDATE-2, decisions D16 to D26 of the PM, design/screens/update.md): the explanation and the permission of the
 * camera, the viewfinder, the preview of the photo, the publication and its calm messages. It is a TAB (the central button of the bar, which stays
 * visible: the bar in glass is another ticket, TABBAR-GLASS).
 *
 * The photo is taken IN MEMORY and never reaches the disk (the camera of the phone's own application writes to ONE file of the private cache, read and
 * deleted at once, like the photo of the profile). Nothing leaves before "Publier". The viewfinder only exists while this screen is visible.
 */

/** What the Update asks of the rest of the app. */
class UpdateActions(
    /** Back to the Feed (the "Fermer", "Pas maintenant" and the end of a publication). */
    val onClose: () -> Unit = {},
    /** "Reprendre le partage": the screen of the Miff holds the dialog and the consent (nothing is written from here). */
    val onResumeSharing: () -> Unit = {},
    /** "Revoir et accepter": the renewal screen of the agreement (the permission gate listens to it). */
    val onReviewAgreement: () -> Unit = {},
)

@Composable
fun UpdateTab(
    viewModel: UpdateViewModel,
    avatarCodec: AvatarCodec,
    sharingStopped: Boolean,
    miffCount: Int,
    actions: UpdateActions,
    modifier: Modifier = Modifier,
) {
    RecentsPrivacy()
    LightStatusBarIconsOff()
    val context = LocalContext.current
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()
    val stage by viewModel.stage.collectAsStateWithLifecycle()
    val encoder = remember { AndroidPhotoEncoder() }

    fun granted() =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    fun rationale() = activity?.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) == true

    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            viewModel.onPermissionAnswered(isGranted, rationale())
        }
    // On the way in, and when the person comes back from the settings of the phone (the permission may have changed).
    LaunchedEffect(sharingStopped) { viewModel.onOpened(granted(), rationale(), sharingStopped) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onOpened(granted(), rationale(), sharingStopped) }
    // Leaving the Update forgets the photo, whatever the way out (another tab, the back gesture): nothing stays in memory.
    DisposableEffect(Unit) { onDispose { viewModel.onClosed() } }

    var aspect by remember { mutableStateOf(UpdatePhotoFormat.MAX_ASPECT) }
    // A photo from the camera application of the phone (the way out when the camera of Miff cannot be used, V10): the same preview, the same re-encoding.
    var captureUri by rememberSaveable { mutableStateOf<String?>(null) }
    val systemCamera =
        rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
            val source = captureUri
            captureUri = null
            if (!taken || source == null) {
                AvatarCapture.deleteAll(context)
            } else {
                scope.launch {
                    val decoded = avatarCodec.decode(source) as? BitmapPhoto
                    val photo = decoded?.let { encoder.encode(it.bitmap, aspect) }
                    decoded?.bitmap?.recycle()
                    if (photo != null) viewModel.onPhotoReady(photo) else viewModel.onPhotoFailed()
                }
            }
        }
    val openPhoneCamera: () -> Unit = {
        val uri = AvatarCapture.newCaptureUri(context)
        captureUri = uri.toString()
        try {
            systemCamera.launch(uri)
        } catch (e: ActivityNotFoundException) {
            captureUri = null
            Toast.makeText(context, R.string.update_no_app, Toast.LENGTH_SHORT).show()
        }
    }
    val openSettings: () -> Unit = { openAppSettings(context) }
    val paneName = stringResource(R.string.update_pane_a11y)

    BoxWithConstraints(modifier = modifier.fillMaxSize().semantics { paneTitle = paneName }) {
        val widthPx = with(LocalDensity.current) { maxWidth.roundToPx() }
        val heightPx = with(LocalDensity.current) { maxHeight.roundToPx() }
        aspect = UpdatePhotoFormat.boundedAspect(widthPx, heightPx)
        when (val current = stage) {
            UpdateStage.Idle -> {
                Box(Modifier.fillMaxSize().background(colorBgCanvas))
            }

            UpdateStage.Explain -> {
                PopInStage(
                    art = R.drawable.ic_update_camera,
                    title = stringResource(R.string.update_permission_title),
                    body = stringResource(R.string.update_permission_body),
                    primary =
                        stringResource(R.string.update_permission_continue) to {
                            viewModel.onExplanationAccepted()
                            permissionLauncher.launch(Manifest.permission.CAMERA)
                        },
                    tertiary = stringResource(R.string.update_permission_not_now) to actions.onClose,
                )
            }

            UpdateStage.RetryPermission -> {
                PopInStage(
                    art = R.drawable.ic_update_camera,
                    title = stringResource(R.string.update_retry_title),
                    body = stringResource(R.string.update_retry_body),
                    primary =
                        stringResource(R.string.update_action_retry) to
                            { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    tertiary = stringResource(R.string.update_permission_not_now) to actions.onClose,
                )
            }

            UpdateStage.PermissionDisabled -> {
                PopInStage(
                    art = R.drawable.ic_update_camera_off,
                    title = stringResource(R.string.update_disabled_title),
                    body = stringResource(R.string.update_disabled_body),
                    primary = stringResource(R.string.update_action_open_settings) to openSettings,
                    // No "camera application of the phone" here: Android refuses to start it from an app that declares CAMERA and was not granted it.
                    tertiary = stringResource(R.string.update_action_close) to actions.onClose,
                )
            }

            UpdateStage.CameraUnavailable -> {
                PopInStage(
                    art = R.drawable.ic_update_camera_off,
                    title = stringResource(R.string.update_unavailable_title),
                    body = stringResource(R.string.update_unavailable_body),
                    primary =
                        stringResource(R.string.update_action_retry) to
                            { viewModel.onRetryCamera(granted(), rationale()) },
                    // The camera application of the phone only works when CAMERA is granted (always the case here, unless it was taken back meanwhile).
                    secondary =
                        if (granted()) {
                            stringResource(
                                R.string.update_action_phone_camera,
                            ) to openPhoneCamera
                        } else {
                            null
                        },
                    tertiary = stringResource(R.string.update_action_close) to actions.onClose,
                )
            }

            UpdateStage.SharingStopped -> {
                PopInStage(
                    art = R.drawable.ic_update_camera_off,
                    title = stringResource(R.string.update_stopped_title),
                    body = stringResource(R.string.update_stopped_body),
                    primary = stringResource(R.string.update_action_resume_sharing) to actions.onResumeSharing,
                    tertiary = stringResource(R.string.update_action_close) to actions.onClose,
                )
            }

            is UpdateStage.Viewfinder -> {
                ViewfinderStage(
                    showTip = current.showTip,
                    onDoubleTapSwitch = viewModel::onSwitchedByDoubleTap,
                    prepareFailed = current.prepareFailed,
                    encoder = encoder,
                    onPhoto = viewModel::onPhotoReady,
                    onPhotoFailed = viewModel::onPhotoFailed,
                    onUnavailable = viewModel::onCameraUnavailable,
                )
            }

            is UpdateStage.Preview -> {
                PreviewStage(
                    onOpenSettings = openSettings,
                    stage = current,
                    miffCount = miffCount,
                    onRetake = viewModel::onRetake,
                    onPublish = viewModel::onPublish,
                    actions = actions,
                )
            }
        }
    }
}

// --- Pop-ins (permission, refusal, camera unavailable, sharing stopped) --------------------------------------------------------

/** A pop-in of the PM (design/exports/pop-ins): white card, blue border, yellow-green pictogram, centred text, one blue button. The calm background is the app's. */
@Composable
private fun PopInStage(
    art: Int,
    title: String,
    body: String,
    primary: Pair<String, () -> Unit>,
    tertiary: Pair<String, () -> Unit>,
    secondary: Pair<String, () -> Unit>? = null,
) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    colorBgCanvas,
                ).miffTopInsets()
                .padding(horizontal = Spacing.l, vertical = Spacing.m),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier =
                Modifier
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .miffShadow(ShadowDefault, ShapeMd)
                    .clip(ShapeMd)
                    .background(colorBgSurface)
                    .border(StrokeAccent, colorBorderAction, ShapeMd),
        ) {
            Column(
                modifier =
                    Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(start = Spacing.m, end = Spacing.m, top = Spacing.l, bottom = Spacing.s),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                Text(
                    text = title,
                    style = textTitleBold20,
                    color = colorActionPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().semantics { heading() },
                )
                Icon(
                    painter = painterResource(art),
                    contentDescription = null,
                    tint = colorBrandPrimary,
                    modifier = Modifier.size(72.dp),
                )
                Text(text = body, style = textBodyRegular, color = colorTextPrimary, textAlign = TextAlign.Center)
            }
            Column(
                modifier = Modifier.fillMaxWidth().padding(start = Spacing.m, end = Spacing.m, bottom = Spacing.m),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                MiffButton(text = primary.first, onClick = primary.second, modifier = Modifier.fillMaxWidth())
                secondary?.let {
                    MiffSecondaryButton(
                        text = it.first,
                        onClick = it.second,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                TextButton(onClick = tertiary.second, modifier = Modifier.heightIn(min = Spacing.minTouchTarget)) {
                    Text(text = tertiary.first, style = textBodyBold14, color = colorActionPrimary)
                }
            }
        }
    }
}

// --- Viewfinder ---------------------------------------------------------------------------------------------------------------

private val ShutterSize = 80.dp
private val ShutterAboveBar = 48.dp
private val FlipSize = 48.dp

@Composable
private fun ViewfinderStage(
    showTip: Boolean,
    onDoubleTapSwitch: () -> Unit,
    prepareFailed: Boolean,
    encoder: AndroidPhotoEncoder,
    onPhoto: (UpdatePhoto) -> Unit,
    onPhotoFailed: () -> Unit,
    onUnavailable: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // A new handle at each opening: the selfie camera is the one the screen starts with, every time.
    val handle = remember { CameraHandle() }
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val density = LocalDensity.current
        val aspect =
            UpdatePhotoFormat.boundedAspect(
                with(density) {
                    maxWidth.roundToPx()
                },
                with(density) { maxHeight.roundToPx() },
            )
        // The zone saved is the zone seen: when the screen is outside 9:21 to 9:16 (tablet, split screen) the viewfinder is limited to the bounded zone, centred.
        val (boxWidth, boxHeight) = boundedBox(maxWidth, maxHeight, aspect)
        val switchName = stringResource(R.string.update_flip_a11y)
        Box(
            modifier =
                Modifier
                    .align(Alignment.Center)
                    .size(boxWidth, boxHeight)
                    // Two quick taps OUTSIDE the buttons switch the camera, and back. The buttons keep their own taps (they sit above this).
                    .pointerInput(handle) {
                        detectTapGestures(
                            onDoubleTap = {
                                if (handle.canSwitch) {
                                    handle.switchLens()
                                    // The tip is useless from now on, for good.
                                    onDoubleTapSwitch()
                                }
                            },
                        )
                    }.semantics {
                        if (handle.canSwitch) {
                            customActions =
                                listOf(CustomAccessibilityAction(switchName) { handle.switchLens().let { true } })
                        }
                    },
        ) {
            CameraSurface(handle = handle, onUnavailable = onUnavailable, modifier = Modifier.fillMaxSize())
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent))),
            )
            if (handle.canSwitch) {
                RoundOverlayButton(
                    icon = R.drawable.ic_update_flip,
                    description = switchName,
                    onClick = handle::switchLens,
                    modifier = Modifier.align(Alignment.TopEnd).miffTopInsets().padding(Spacing.m),
                )
            }
            if (showTip) {
                Text(
                    text = stringResource(R.string.update_viewfinder_tip),
                    style = textBodyRegular14,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier =
                        Modifier
                            .align(Alignment.TopCenter)
                            .miffTopInsets()
                            .padding(top = Spacing.s, start = FlipSize + Spacing.m, end = FlipSize + Spacing.m)
                            .clip(RoundedCornerShape(12.dp))
                            .background(colorUpdateScrim)
                            .padding(horizontal = Spacing.m, vertical = Spacing.s),
                )
            }
            Column(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = ShutterAboveBar).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                Text(
                    text =
                        stringResource(
                            if (prepareFailed) R.string.update_prepare_failed else R.string.update_viewfinder_help,
                        ),
                    style = textBodyRegular14,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier =
                        Modifier.padding(horizontal = Spacing.l).semantics {
                            if (prepareFailed) {
                                liveRegion =
                                    LiveRegionMode.Polite
                            }
                        },
                )
                Shutter {
                    // The front camera: the photo is the reflection of the scene, as the viewfinder shows it; the back camera is left as it is.
                    val front = handle.lens == CameraLens.FRONT
                    handle.takePhoto(context) { bitmap, rotation ->
                        if (bitmap == null) {
                            onPhotoFailed()
                        } else {
                            scope.launch {
                                val upright =
                                    withContext(
                                        Dispatchers.Default,
                                    ) { encoder.upright(bitmap, rotation, mirror = front) }
                                val photo = encoder.encode(upright, aspect)
                                upright.recycle()
                                if (photo != null) onPhoto(photo) else onPhotoFailed()
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The ratio-bounded box of the viewfinder inside the room that the screen leaves: as large as possible at ratio [aspect] (width / height). */
private fun boundedBox(
    maxWidth: Dp,
    maxHeight: Dp,
    aspect: Float,
): Pair<Dp, Dp> =
    if (maxWidth.value / maxHeight.value >
        aspect
    ) {
        (maxHeight * aspect) to maxHeight
    } else {
        maxWidth to (maxWidth / aspect)
    }

/** The round shutter: 80 dp, a white ring and a blue ring inside (the blue alone vanishes on a dark background), transparent centre. */
@Composable
private fun Shutter(onClick: () -> Unit) {
    val name = stringResource(R.string.update_shutter_a11y)
    Box(
        modifier =
            Modifier
                .size(ShutterSize)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onClick)
                .border(4.dp, Color.White, CircleShape)
                .semantics { contentDescription = name },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(ShutterSize - 14.dp).border(3.dp, colorActionPrimary, CircleShape))
    }
}

/** A round button on a photo: 48 dp, a translucent dark disc (a white icon alone vanishes on a light photo), the icon in white. */
@Composable
private fun RoundOverlayButton(
    icon: Int,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .size(FlipSize)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(40.dp).background(colorUpdateScrim, CircleShape), contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

// --- Preview, publication -----------------------------------------------------------------------------------------------------

@Composable
private fun PreviewStage(
    onOpenSettings: () -> Unit,
    stage: UpdateStage.Preview,
    miffCount: Int,
    onRetake: () -> Unit,
    onPublish: () -> Unit,
    actions: UpdateActions,
) {
    val photo = stage.photo
    // What is drawn is the very JPEG that will be published (selfie: the reflection, as in the viewfinder; the same crop): the preview is the truth.
    val bitmap = remember(photo) { BitmapFactory.decodeByteArray(photo.jpeg, 0, photo.jpeg.size)?.asImageBitmap() }
    val takenAnnouncement = stringResource(R.string.update_preview_taken_a11y)
    val canPublish = !stage.publishing && stage.notice != PreviewNotice.Stale && stage.notice != PreviewNotice.TooMany
    Column(
        modifier = Modifier.fillMaxSize().background(colorUpdateStage).miffTopInsets(),
    ) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(horizontal = Spacing.m, vertical = Spacing.s)) {
            val density = LocalDensity.current
            val frameWidth = with(density) { maxWidth.roundToPx() }
            val frameHeight = with(density) { maxHeight.roundToPx() }
            val (w, h) = UpdatePhotoFormat.fitInside(photo.width, photo.height, frameWidth, frameHeight)
            val shownWidth = with(density) { w.toDp() }
            val shownHeight = with(density) { h.toDp() }
            Box(Modifier.align(Alignment.Center).size(shownWidth, shownHeight).clip(RoundedCornerShape(20.dp))) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = null,
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                // The cross does what "Recommencer" does (D22): back to the viewfinder, the photo forgotten, nothing published, nothing asked.
                RoundOverlayButton(
                    icon = R.drawable.ic_loc_close,
                    description = stringResource(R.string.update_close_a11y),
                    onClick = onRetake,
                    modifier = Modifier.align(Alignment.TopStart).padding(Spacing.xs),
                )
                stage.notice?.let { notice ->
                    NoticeCard(
                        onOpenSettings = onOpenSettings,
                        notice = notice,
                        canRetry = !stage.publishing,
                        onRetry = onPublish,
                        actions = actions,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(Spacing.s),
                    )
                }
                if (stage.publishing) PublishingVeil()
            }
        }
        // One announcement when the preview appears.
        Box(
            Modifier.size(0.dp).semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = takenAnnouncement
            },
        )
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(
                text =
                    if (miffCount >= 2) {
                        stringResource(R.string.update_preview_visibility_many, miffCount)
                    } else {
                        stringResource(R.string.update_preview_visibility)
                    },
                style = textBodyRegular14,
                color = Color.White,
            )
            // The photo always goes with the position (decision of the PM, 2026-11-06): said plainly and soberly, no option "without place".
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_update_pin),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = stringResource(R.string.update_preview_place),
                    style = textBodyRegular14,
                    color = Color.White,
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.m, vertical = Spacing.s),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RetakeButton(enabled = !stage.publishing, onClick = onRetake)
            PublishButton(enabled = canPublish, onClick = onPublish)
        }
    }
}

/** "Recommencer": a round-cornered white button, blue icon and blue outline (the PM's mockup: icon only, so it has a spoken name). */
@Composable
private fun RetakeButton(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val name = stringResource(R.string.update_retake_a11y)
    Box(
        modifier =
            Modifier
                .size(width = 56.dp, height = Spacing.minTouchTarget)
                .clip(ShapeMd)
                .background(if (enabled) colorBgCanvas else colorBgCanvas.copy(alpha = 0.6f))
                .border(1.5.dp, colorBorderAction, ShapeMd)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .semantics { contentDescription = name },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_update_retake),
            contentDescription = null,
            tint = colorActionPrimary,
        )
    }
}

/** "Publier": the blue button (48 dp tall), the word and the paper plane of the mockup; at 30 % when it cannot be pressed. */
@Composable
private fun PublishButton(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val label = stringResource(R.string.update_publish_action)
    Row(
        modifier =
            Modifier
                .heightIn(min = Spacing.minTouchTarget)
                .clip(ShapeMd)
                .background(if (enabled) colorActionPrimary else colorActionPrimaryTint30)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .padding(horizontal = Spacing.m)
                .semantics(mergeDescendants = true) { contentDescription = label },
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = textBodyLarge, color = colorTextOnAction)
        Icon(painter = painterResource(R.drawable.ic_update_send), contentDescription = null, tint = colorTextOnAction)
    }
}

@Composable
private fun PublishingVeil() {
    val text = stringResource(R.string.update_publishing)
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(colorUpdateVeil)
                // Nothing under the veil answers while the photo leaves: no second tap, no cancellation once sent.
                .pointerInput(Unit) { detectTapGestures {} },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier =
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(colorBgSurface)
                    .padding(horizontal = Spacing.m, vertical = Spacing.s + Spacing.xs)
                    .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = colorActionPrimary)
            Text(text = text, style = textBodyRegular14, color = colorTextPrimary)
        }
    }
}

/** A calm card over the photo: what did not happen, in plain words, never "error"; the photo is still there; a way forward when there is one. */
@Composable
private fun NoticeCard(
    onOpenSettings: () -> Unit,
    notice: PreviewNotice,
    canRetry: Boolean,
    onRetry: () -> Unit,
    actions: UpdateActions,
    modifier: Modifier = Modifier,
) {
    val text =
        when (notice) {
            PreviewNotice.Offline -> stringResource(R.string.update_notice_offline)
            PreviewNotice.PositionNotFound -> stringResource(R.string.update_notice_position)
            PreviewNotice.LocationOff -> stringResource(R.string.update_notice_location_off)
            PreviewNotice.Unconfirmed -> stringResource(R.string.update_notice_unconfirmed)
            PreviewNotice.TooMany -> stringResource(R.string.update_notice_too_many)
            PreviewNotice.TooSoon -> stringResource(R.string.update_notice_too_soon)
            PreviewNotice.Stale -> stringResource(R.string.update_preview_stale)
            PreviewNotice.Refused, PreviewNotice.Failed -> stringResource(R.string.update_notice_failed)
            PreviewNotice.NeedsAgreement -> stringResource(R.string.update_notice_agreement)
            PreviewNotice.SharingStopped -> stringResource(R.string.update_stopped_body)
            is PreviewNotice.Partial -> stringResource(R.string.update_notice_partial, notice.sharedIn, notice.total)
        }
    val icon =
        when (notice) {
            PreviewNotice.Offline -> R.drawable.ic_loc_cloud_off
            PreviewNotice.PositionNotFound, PreviewNotice.LocationOff -> R.drawable.ic_update_pin
            else -> R.drawable.ic_feed_clock
        }
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(colorBgSurface)
                .padding(Spacing.m)
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.Top) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = colorTextPrimary,
                modifier = Modifier.size(20.dp),
            )
            Text(text = text, style = textBodyRegular14, color = colorTextPrimary)
        }
        val action: Pair<String, () -> Unit>? =
            when (notice) {
                PreviewNotice.NeedsAgreement -> {
                    stringResource(R.string.update_action_review_agreement) to
                        actions.onReviewAgreement
                }

                PreviewNotice.SharingStopped -> {
                    stringResource(R.string.update_action_resume_sharing) to
                        actions.onResumeSharing
                }

                // The permission of the location was taken back, or the location of the phone is off: only the settings can change that.
                PreviewNotice.LocationOff -> {
                    stringResource(R.string.update_action_open_settings) to onOpenSettings
                }

                // It may have left: the Feed shows my card with the photo if it did ("Publier" stays available: a second sending replaces the first).
                PreviewNotice.Unconfirmed -> {
                    stringResource(R.string.update_action_open_feed) to actions.onClose
                }

                PreviewNotice.Stale, PreviewNotice.TooMany -> {
                    null
                }

                else -> {
                    if (canRetry) stringResource(R.string.update_action_retry) to onRetry else null
                }
            }
        action?.let { (label, run) ->
            TextButton(onClick = run, modifier = Modifier.heightIn(min = Spacing.minTouchTarget).align(Alignment.End)) {
                Text(text = label, style = textBodyBold14, color = colorActionPrimary)
            }
        }
    }
}

// --- Platform helpers ---------------------------------------------------------------------------------------------------------

/** The settings page of this app in the system (permissions). If the phone cannot open it, nothing happens. */
private fun openAppSettings(context: Context) {
    val intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        // Nothing to open: the screen stays, the person can open the settings of the phone by hand.
    }
}

/** White status bar icons while the camera (dark) is behind them; the previous look is restored on the way out. */
@Composable
private fun LightStatusBarIconsOff() {
    val activity = LocalActivity.current
    val view = LocalView.current
    DisposableEffect(activity, view) {
        val window = activity?.window
        if (window == null) {
            onDispose { }
        } else {
            val controller = WindowCompat.getInsetsController(window, view)
            val wasLight = controller.isAppearanceLightStatusBars
            controller.isAppearanceLightStatusBars = false
            onDispose { controller.isAppearanceLightStatusBars = wasLight }
        }
    }
}
