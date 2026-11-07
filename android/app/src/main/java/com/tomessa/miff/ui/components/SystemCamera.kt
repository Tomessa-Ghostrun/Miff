package com.tomessa.miff.ui.components

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.tomessa.miff.data.profile.AvatarCapture

/*
 * THE CAMERA APPLICATION OF THE PHONE, for the photo of the profile (Onboarding ticket 6.3). Until UPDATE-1 it needed no permission. Since the Update screen
 * declares CAMERA (its viewfinder), ANDROID REFUSES to start the camera application of the phone ("ACTION_IMAGE_CAPTURE") from an app that declares CAMERA
 * and has not been granted it: it throws a SecurityException (documentation of MediaStore.ACTION_IMAGE_CAPTURE). So, before the camera application is
 * started, the permission is asked with the box of the system (a person who just touched "Prendre une photo" is not asked "cold"), and a refusal gives
 * the calm "camera unavailable" message of the screen, the Photo Picker staying available. Written ONCE here for the two screens that take a photo.
 */

/**
 * The action "take a photo with the camera application of the phone": returns what to call from the button. [onPhoto] receives the address of the
 * private-cache file the camera wrote (null if the person gave up: the file is deleted), [onUnavailable] the calm message.
 */
@Composable
fun rememberSystemCamera(
    onPhoto: (String?) -> Unit,
    onUnavailable: () -> Unit,
): () -> Unit {
    val context = LocalContext.current
    // The address of the camera file survives the destruction of the screen while the camera application is open.
    var captureUri by rememberSaveable { mutableStateOf<String?>(null) }
    val camera =
        rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
            if (taken) onPhoto(captureUri) else AvatarCapture.deleteAll(context)
        }

    fun launchCamera() {
        val uri = AvatarCapture.newCaptureUri(context)
        captureUri = uri.toString()
        try {
            camera.launch(uri)
        } catch (e: ActivityNotFoundException) {
            onUnavailable()
        } catch (e: SecurityException) {
            onUnavailable()
        }
    }
    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) launchCamera() else onUnavailable()
        }
    return {
        val granted =
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (granted) launchCamera() else permission.launch(Manifest.permission.CAMERA)
    }
}
