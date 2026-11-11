package com.tomessa.miff.ui.update

import androidx.camera.core.ImageCapture

/**
 * The flash of the viewfinder, as a pure rule (testable without a camera).
 *
 * - It is OFF by default and the choice is NEVER kept: a new viewfinder, a new camera (the lens switch) and the return from the background all give an
 *   extinguished flash ([withCamera], [reset]).
 * - It exists only when the active camera has a flash unit ([available], from `cameraInfo.hasFlashUnit()`; the front camera has none in general): without
 *   one the button is hidden and [toggled] changes nothing.
 * - It only fires when the photo is taken (CameraX flash mode ON); there is no continuous torch.
 */
data class FlashState(
    val available: Boolean = false,
    val on: Boolean = false,
) {
    /** The button is shown only when there is a flash to drive. */
    val visible: Boolean get() = available

    /** The CameraX flash mode for the next photo: never ON without a flash unit. */
    val flashMode: Int get() = if (available && on) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF

    fun toggled(): FlashState = if (available) copy(on = !on) else this

    /** A (new) camera is bound: its capability, and always an extinguished flash. */
    fun withCamera(hasFlashUnit: Boolean): FlashState = FlashState(available = hasFlashUnit, on = false)

    /** Back from the background (or the screen locked): the flash is off again, the camera keeps its capability. */
    fun reset(): FlashState = copy(on = false)
}
