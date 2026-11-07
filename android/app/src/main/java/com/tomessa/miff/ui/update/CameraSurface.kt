package com.tomessa.miff.ui.update

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Size
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/*
 * The viewfinder (CameraX, ticket Update 5.3). PRIVACY and SECURITY, for this file:
 * - the camera is bound to the screen: it starts when this composable enters and is released when it leaves (another tab, the back gesture) AND when the app
 *   goes to the background or the screen locks (the lifecycle of the screen). Miff never opens the camera by itself and nothing can trigger it from afar
 *   (criterion P1);
 * - the photo is captured IN MEMORY ([ImageCapture.OnImageCapturedCallback]): no file is created, nothing goes to the gallery, no storage permission;
 * - no flash, no zoom, no filter, no timer: not asked for (V4). Nothing is logged here.
 */

enum class CameraLens { FRONT, BACK }

/** What the screen needs to drive the viewfinder: which lens, whether there are two, and "take the photo". */
class CameraHandle {
    var lens by mutableStateOf(CameraLens.FRONT)
        internal set

    /** Both a front and a back camera exist: the button and the double tap switch (else the button is hidden). */
    var canSwitch by mutableStateOf(false)
        internal set

    @Volatile
    internal var capture: ImageCapture? = null

    @Volatile
    private var busy = false

    internal val executor: ExecutorService by lazy { Executors.newSingleThreadExecutor() }

    fun switchLens() {
        if (!canSwitch) return
        lens = if (lens == CameraLens.FRONT) CameraLens.BACK else CameraLens.FRONT
    }

    /**
     * Takes ONE photo (a second request while one is under way is ignored: two taps on the shutter give one photo, criterion V1). [onResult] is called on the
     * main thread with the upright-ready bitmap and the rotation the sensor asks for, or null bitmap when it failed.
     */
    fun takePhoto(
        context: Context,
        onResult: (bitmap: Bitmap?, rotationDegrees: Int) -> Unit,
    ): Boolean {
        val capture = capture ?: return false
        if (busy) return false
        busy = true
        val main = Handler(Looper.getMainLooper())
        capture.takePicture(
            executor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val rotation = image.imageInfo.rotationDegrees
                    val bitmap =
                        try {
                            image.toBitmap()
                        } catch (e: RuntimeException) {
                            null
                        } catch (e: OutOfMemoryError) {
                            null
                        } finally {
                            image.close()
                        }
                    main.post {
                        busy = false
                        onResult(bitmap, rotation)
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    main.post {
                        busy = false
                        onResult(null, 0)
                    }
                }
            },
        )
        return true
    }

    internal fun release() {
        capture = null
        executor.shutdown()
    }
}

private suspend fun Context.cameraProvider(): ProcessCameraProvider =
    suspendCancellableCoroutine { continuation ->
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener(
            {
                try {
                    continuation.resume(future.get())
                } catch (e: Exception) {
                    continuation.resumeWithException(e)
                }
            },
            ContextCompat.getMainExecutor(this),
        )
    }

/**
 * The live image, filling its box ("cover": what is seen is what will be saved, the box having the bounded ratio of the photo). [onUnavailable] is called when
 * no camera can be used (another application holds it, a policy, no camera at all): the screen then says so calmly, never a technical message.
 */
@Composable
fun CameraSurface(
    handle: CameraHandle,
    onUnavailable: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val unavailable by rememberUpdatedState(onUnavailable)
    val previewView =
        remember {
            PreviewView(context).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                // TextureView: the controls drawn over it stay above, and it clips like any other view.
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            }
        }
    DisposableEffect(Unit) {
        onDispose {
            // The camera is released the moment the viewfinder leaves the screen (another tab, retake, publication): never kept bound by the lifecycle of the app.
            handle.release()
            val future = ProcessCameraProvider.getInstance(context)
            if (future.isDone) runCatching { future.get().unbindAll() }
        }
    }
    LaunchedEffect(handle.lens) {
        try {
            val provider = context.cameraProvider()
            val hasFront = provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
            val hasBack = provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)
            handle.canSwitch = hasFront && hasBack
            // Selfie by default; without a front camera, the back one; without any, the calm "camera unavailable".
            val lens =
                when {
                    handle.lens == CameraLens.FRONT && hasFront -> {
                        CameraLens.FRONT
                    }

                    hasBack -> {
                        CameraLens.BACK
                    }

                    hasFront -> {
                        CameraLens.FRONT
                    }

                    else -> {
                        unavailable()
                        return@LaunchedEffect
                    }
                }
            if (lens != handle.lens) {
                handle.lens = lens
                return@LaunchedEffect
            }
            val ratio = AspectRatioStrategy(AspectRatio.RATIO_4_3, AspectRatioStrategy.FALLBACK_RULE_AUTO)
            val preview =
                Preview
                    .Builder()
                    .setResolutionSelector(ResolutionSelector.Builder().setAspectRatioStrategy(ratio).build())
                    .build()
                    .also { it.surfaceProvider = previewView.surfaceProvider }
            val capture =
                ImageCapture
                    .Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .setFlashMode(ImageCapture.FLASH_MODE_OFF)
                    .setResolutionSelector(
                        ResolutionSelector
                            .Builder()
                            .setAspectRatioStrategy(ratio)
                            // About 2 megapixels: more than the 1 024 px of the saved photo needs, and light on memory.
                            .setResolutionStrategy(
                                ResolutionStrategy(
                                    Size(CAPTURE_LONG_SIDE, CAPTURE_SHORT_SIDE),
                                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                                ),
                            ).build(),
                    ).build()
            val selector =
                if (lens ==
                    CameraLens.FRONT
                ) {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                } else {
                    CameraSelector.DEFAULT_BACK_CAMERA
                }
            provider.unbindAll()
            provider.bindToLifecycle(owner, selector, preview, capture)
            handle.capture = capture
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            unavailable()
        }
    }
    AndroidView(factory = { previewView }, modifier = modifier)
}

private const val CAPTURE_LONG_SIDE = 1920
private const val CAPTURE_SHORT_SIDE = 1440
