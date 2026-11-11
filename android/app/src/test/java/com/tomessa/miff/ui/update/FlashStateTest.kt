package com.tomessa.miff.ui.update

import androidx.camera.core.ImageCapture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The flash rule of the viewfinder: off by default, never kept, only when the camera has a flash, only at the photo. */
class FlashStateTest {
    @Test
    fun byDefault_theFlashIsOffAndTheButtonHidden() {
        val state = FlashState()
        assertFalse(state.on)
        assertFalse(state.visible)
        assertEquals(ImageCapture.FLASH_MODE_OFF, state.flashMode)
    }

    @Test
    fun aCameraWithAFlash_showsTheButton_stillOff() {
        val state = FlashState().withCamera(hasFlashUnit = true)
        assertTrue(state.visible)
        assertFalse(state.on)
        assertEquals(ImageCapture.FLASH_MODE_OFF, state.flashMode)
    }

    @Test
    fun toggling_switchesOnThenOff_andOnMeansFlashModeOn() {
        val on = FlashState().withCamera(true).toggled()
        assertTrue(on.on)
        assertEquals(ImageCapture.FLASH_MODE_ON, on.flashMode)
        val off = on.toggled()
        assertFalse(off.on)
        assertEquals(ImageCapture.FLASH_MODE_OFF, off.flashMode)
    }

    @Test
    fun aCameraWithoutFlash_hidesTheButton_andTogglingChangesNothing() {
        val state = FlashState().withCamera(false)
        assertFalse(state.visible)
        assertEquals(state, state.toggled())
        assertEquals(ImageCapture.FLASH_MODE_OFF, state.toggled().flashMode)
    }

    @Test
    fun switchingCamera_putsTheFlashBackOff_whateverTheNewCamera() {
        val on = FlashState().withCamera(true).toggled()
        assertFalse(on.withCamera(true).on)
        assertFalse(on.withCamera(false).on)
    }

    @Test
    fun backFromTheBackground_theFlashIsOffAgain_butTheCameraKeepsItsFlash() {
        val reset = FlashState().withCamera(true).toggled().reset()
        assertFalse(reset.on)
        assertTrue(reset.visible)
        assertEquals(ImageCapture.FLASH_MODE_OFF, reset.flashMode)
    }

    @Test
    fun anOnFlashWithoutAFlashUnit_neverAsksCameraXForTheFlash() {
        assertEquals(ImageCapture.FLASH_MODE_OFF, FlashState(available = false, on = true).flashMode)
    }
}
