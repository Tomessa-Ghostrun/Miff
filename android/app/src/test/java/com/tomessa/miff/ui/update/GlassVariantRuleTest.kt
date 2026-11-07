package com.tomessa.miff.ui.update

import com.tomessa.miff.domain.update.UpdatePhoto
import com.tomessa.miff.ui.theme.GlassVariant
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The glass of the tab bar follows what is BEHIND it: dark (white icons) only over the viewfinder and the preview, light over the pop-ins of the Update, whose
 * background is light (review of the merge with the glass bar), and light on the Feed and the Profile.
 */
class GlassVariantRuleTest {
    private val photo = UpdatePhoto(ByteArray(10), 461, 1024, takenAtMillis = 0L)

    @Test
    fun overTheViewfinderAndThePreview_theGlassIsTheDarkOne() {
        assertEquals(GlassVariant.ON_PHOTO, glassVariantFor(true, UpdateStage.Viewfinder(showTip = false)))
        assertEquals(GlassVariant.ON_PHOTO, glassVariantFor(true, UpdateStage.Preview(photo)))
    }

    @Test
    fun overTheLightPopIns_theGlassIsTheLightOne() {
        listOf(
            UpdateStage.Idle,
            UpdateStage.Explain,
            UpdateStage.RetryPermission,
            UpdateStage.PermissionDisabled,
            UpdateStage.CameraUnavailable,
            UpdateStage.SharingStopped,
        ).forEach { stage ->
            assertEquals("$stage", GlassVariant.LIGHT, glassVariantFor(true, stage))
        }
    }

    @Test
    fun onTheOtherTabs_theGlassIsAlwaysTheLightOne_whateverTheStageOfTheUpdate() {
        assertEquals(GlassVariant.LIGHT, glassVariantFor(false, UpdateStage.Viewfinder(showTip = true)))
        assertEquals(GlassVariant.LIGHT, glassVariantFor(false, UpdateStage.Preview(photo)))
    }
}
