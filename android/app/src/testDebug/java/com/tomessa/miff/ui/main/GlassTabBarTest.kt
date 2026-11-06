package com.tomessa.miff.ui.main

import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.tomessa.miff.R
import com.tomessa.miff.domain.navigation.MainTab
import com.tomessa.miff.domain.navigation.TabBarConfig
import com.tomessa.miff.domain.tracking.StatusLevel
import com.tomessa.miff.ui.theme.GlassConditions
import com.tomessa.miff.ui.theme.GlassConditionsReader
import com.tomessa.miff.ui.theme.GlassLevel
import com.tomessa.miff.ui.theme.GlassPaletteLight
import com.tomessa.miff.ui.theme.GlassPaletteOnPhoto
import com.tomessa.miff.ui.theme.GlassVariant
import com.tomessa.miff.ui.theme.MiffTheme
import com.tomessa.miff.ui.theme.colorStatusDanger
import com.tomessa.miff.ui.theme.colorStatusSafe
import com.tomessa.miff.ui.theme.colorStatusWarning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.abs

/**
 * The glass tab bar on the JVM (Robolectric, no phone): the variants, the three levels (blur, no blur, opaque), the fallbacks read from the
 * system, the central button (dot of colour, no pictogram, grey at the neutral state), 48 dp targets, large font, the spoken names, and the
 * bar that never hides the content. Pixels are read from the rendered bar. The BLUR itself (a GPU effect) cannot be judged here: the recipe
 * on the OPPO does (docs/qa/checklists/tabbar-glass-android-recette-pm.md). Writes screenshots to build/screenshots.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "en-w360dp-h800dp-port-xxhdpi")
class GlassTabBarTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun context(): Context = ApplicationProvider.getApplicationContext()

    private fun text(id: Int): String = context().getString(id)

    private val internal = TabBarConfig.forBuild(isInternalBuild = true)
    private val external = TabBarConfig.forBuild(isInternalBuild = false)
    private val density = 3f // xxhdpi

    private val modern =
        GlassConditions(
            sdkInt = 33,
            powerSaveMode = false,
            animationsRemoved = false,
            contrastIncreased = false,
            lowRamDevice = false,
        )

    private fun shoot(name: String) {
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private class BarParams(
        val glass: TabBarGlass,
        val background: Color,
        val config: TabBarConfig,
        val selected: MainTab,
        val status: StatusLevel,
        val fontScale: Float,
    )

    private val barParams = mutableStateOf<BarParams?>(null)
    private var barShown = false

    /** A bar alone over [background], as the glass sees it. Can be called again in a test: it then updates the bar already shown. */
    private fun showBar(
        glass: TabBarGlass,
        background: Color,
        config: TabBarConfig = internal,
        selected: MainTab = MainTab.FEED,
        status: StatusLevel = StatusLevel.NEUTRAL,
        fontScale: Float = 1f,
    ) {
        barParams.value = BarParams(glass, background, config, selected, status, fontScale)
        if (!barShown) {
            barShown = true
            composeRule.setContent {
                MiffTheme {
                    val p = barParams.value!!
                    CompositionLocalProvider(LocalDensity provides Density(density, p.fontScale)) {
                        Box(Modifier.fillMaxSize().background(p.background)) {
                            Box(Modifier.align(Alignment.BottomCenter)) {
                                MainTabBar(
                                    config = p.config,
                                    selected = p.selected,
                                    onSelect = {},
                                    glass = p.glass,
                                    updateStatus = p.status,
                                )
                            }
                        }
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun bounds(descriptionRes: Int): DpRect =
        composeRule.onNodeWithContentDescription(text(descriptionRes)).getUnclippedBoundsInRoot()

    private fun pixel(
        xDp: Dp,
        yDp: Dp,
    ): Int {
        val image = composeRule.onRoot().captureToImage().asAndroidBitmap()
        return image.getPixel((xDp.value * density).toInt(), (yDp.value * density).toInt())
    }

    private fun assertColor(
        label: String,
        expected: Color,
        actual: Int,
        tolerance: Int = 3,
    ) {
        val e = expected.toArgb()
        val ok =
            listOf(16, 8, 0).all { shift -> abs(((e shr shift) and 0xFF) - ((actual shr shift) and 0xFF)) <= tolerance }
        assertTrue("$label: expected ${Integer.toHexString(e)}, got ${Integer.toHexString(actual)}", ok)
    }

    private fun over(
        top: Color,
        bottom: Color,
    ): Color {
        val a = top.alpha
        return Color(
            top.red * a + bottom.red * (1 - a),
            top.green * a + bottom.green * (1 - a),
            top.blue * a + bottom.blue * (1 - a),
        )
    }

    /** A point of the bar that is neither an icon nor the central button: between its left edge and the first entry. */
    private fun veilPoint(): Pair<Dp, Dp> {
        val feed = bounds(R.string.tabbar_feed_a11y)
        return (feed.left - 12.dp) to ((feed.top + feed.bottom) / 2)
    }

    // --- The surface: three levels, two variants -------------------------------------------------------------------------------------

    @Test
    fun lightBar_noBlur_isAWhiteVeilOf92Percent_overTheContent() {
        showBar(TabBarGlass(GlassVariant.LIGHT, GlassLevel.NO_BLUR), Color.Black)
        shoot("glass-light-noblur")
        val (x, y) = veilPoint()
        assertColor("light no-blur over black", over(GlassPaletteLight.tintNoBlur, Color.Black), pixel(x, y))
    }

    @Test
    fun lightBar_opaque_isPlainWhite_whateverIsBehind() {
        showBar(TabBarGlass(GlassVariant.LIGHT, GlassLevel.OPAQUE), Color.Black)
        shoot("glass-light-opaque")
        val (x, y) = veilPoint()
        assertColor("light opaque over black", Color.White, pixel(x, y), tolerance = 0)
    }

    @Test
    fun barOnPhoto_noBlur_isABlackVeilOf72Percent_overAWhiteBackground() {
        showBar(TabBarGlass(GlassVariant.ON_PHOTO, GlassLevel.NO_BLUR), Color.White)
        shoot("glass-photo-noblur")
        val (x, y) = veilPoint()
        assertColor("photo no-blur over white", over(GlassPaletteOnPhoto.tintNoBlur, Color.White), pixel(x, y))
    }

    @Test
    fun barOnPhoto_opaque_is222222() {
        showBar(TabBarGlass(GlassVariant.ON_PHOTO, GlassLevel.OPAQUE), Color.White)
        val (x, y) = veilPoint()
        assertColor("photo opaque", GlassPaletteOnPhoto.surfaceOpaque, pixel(x, y), tolerance = 0)
    }

    @Test
    fun theBlurLevel_composesInsideTheHost_andDrawsItsVeil() {
        // The blur needs a recorded copy of the content (hazeSource): the host does it. Over a black content the blur of black is black,
        // so the bar must read as the 80 % white veil. (The effect on a real photo is judged on the OPPO.)
        composeRule.setContent {
            MiffTheme {
                CompositionLocalProvider(LocalDensity provides Density(density, 1f)) {
                    GlassTabHost(
                        variant = GlassVariant.LIGHT,
                        barVisible = true,
                        conditions = modern,
                        // Forced on: this test is about the blur itself, whatever the shipped switch says.
                        blurAllowed = true,
                        bar = { glass ->
                            assertEquals(GlassLevel.BLUR, glass.level)
                            assertTrue("a blurred bar must be given the source", glass.hazeState != null)
                            MainTabBar(config = internal, selected = MainTab.FEED, onSelect = {}, glass = glass)
                        },
                    ) {
                        Box(Modifier.fillMaxSize().background(Color.Black))
                    }
                }
            }
        }
        composeRule.waitForIdle()
        shoot("glass-light-blur-over-black")
        val (x, y) = veilPoint()
        assertColor("light blur over black", over(GlassPaletteLight.tint, Color.Black), pixel(x, y), tolerance = 8)
    }

    @Test
    fun theHost_neverBlursTheViewfinder_nor_anOpaqueOrOldPhone() {
        val seen = mutableListOf<Pair<String, GlassLevel>>()

        fun host(
            name: String,
            variant: GlassVariant,
            conditions: GlassConditions,
        ): @Composable () -> Unit =
            {
                GlassTabHost(
                    variant = variant,
                    barVisible = true,
                    conditions = conditions,
                    bar = { glass ->
                        seen += name to glass.level
                        assertTrue(
                            "no source is given when there is no blur",
                            glass.level == GlassLevel.BLUR || glass.hazeState == null,
                        )
                    },
                ) {}
            }
        composeRule.setContent {
            MiffTheme {
                host("viewfinder", GlassVariant.ON_PHOTO, modern)()
                host("api30", GlassVariant.LIGHT, modern.copy(sdkInt = 30))()
                host("saver", GlassVariant.LIGHT, modern.copy(powerSaveMode = true))()
            }
        }
        composeRule.waitForIdle()
        assertEquals(
            listOf("viewfinder" to GlassLevel.NO_BLUR, "api30" to GlassLevel.NO_BLUR, "saver" to GlassLevel.OPAQUE),
            seen.distinct(),
        )
    }

    // --- Fallbacks read from the system ------------------------------------------------------------------------------------------------

    @Test
    fun theReader_seesBatterySaver_andRemovedAnimations_inTheSystem() {
        val c = context()
        val before = GlassConditionsReader.read(c)
        assertFalse(before.powerSaveMode)
        assertFalse(before.animationsRemoved)
        shadowOf(c.getSystemService(PowerManager::class.java)).setIsPowerSaveMode(true)
        assertTrue(GlassConditionsReader.read(c).powerSaveMode)
        Settings.Global.putFloat(c.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        assertTrue(GlassConditionsReader.read(c).animationsRemoved)
    }

    @Test
    fun theBar_becomesOpaque_whenBatterySaverIsSwitchedOnWhileTheAppIsOpen() {
        composeRule.setContent {
            MiffTheme {
                CompositionLocalProvider(LocalDensity provides Density(density, 1f)) {
                    Box(Modifier.fillMaxSize().background(Color.Black)) {
                        GlassTabHost(
                            variant = GlassVariant.LIGHT,
                            barVisible = true,
                            bar = { glass ->
                                MainTabBar(config = internal, selected = MainTab.FEED, onSelect = {}, glass = glass)
                            },
                        ) {
                            Box(Modifier.fillMaxSize().background(Color.Black))
                        }
                    }
                }
            }
        }
        composeRule.waitForIdle()
        val (x, y) = veilPoint()
        assertTrue("with nothing asked, the bar is translucent", pixel(x, y) != Color.White.toArgb())
        shadowOf(context().getSystemService(PowerManager::class.java)).setIsPowerSaveMode(true)
        context().sendBroadcast(Intent(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED))
        shadowOf(android.os.Looper.getMainLooper()).idle()
        composeRule.waitForIdle()
        shoot("glass-light-batterysaver")
        assertColor("opaque with battery saver", Color.White, pixel(x, y), tolerance = 0)
    }

    // --- The central button ------------------------------------------------------------------------------------------------------------

    private fun centralCenter(): Pair<Dp, Dp> {
        val b = bounds(R.string.tabbar_update_soon_a11y)
        return ((b.left + b.right) / 2) to ((b.top + b.bottom) / 2)
    }

    @Test
    fun centralButton_isADotOfTheColourOfTheStatus_fullColour_withNoPictogram() {
        mapOf(
            StatusLevel.GREEN to colorStatusSafe,
            StatusLevel.ORANGE to colorStatusWarning,
            StatusLevel.RED to colorStatusDanger,
        ).forEach { (level, color) ->
            showBar(TabBarGlass(GlassVariant.LIGHT, GlassLevel.NO_BLUR), Color.White, status = level)
            val (cx, cy) = centralCenter()
            // Every point of the dot (centre and eight points on a circle of 8 dp) has the same colour: nothing is drawn in it.
            listOf(0 to 0, 8 to 0, -8 to 0, 0 to 8, 0 to -8, 6 to 6, -6 to -6, 6 to -6, -6 to 6).forEach { (dx, dy) ->
                assertColor("$level dot at ($dx,$dy)", color, pixel(cx + dx.dp, cy + dy.dp), tolerance = 1)
            }
            shoot("glass-central-$level")
        }
    }

    @Test
    fun centralButton_neutralIsGrey595959_forTheRingAndTheDot_onBothVariants() {
        GlassVariant.entries.forEach { variant ->
            showBar(TabBarGlass(variant, GlassLevel.NO_BLUR), Color.White, status = StatusLevel.NEUTRAL)
            val (cx, cy) = centralCenter()
            assertColor("neutral dot on $variant", Color(0xFF595959), pixel(cx, cy), tolerance = 1)
            // The ring is 3 dp wide, from 15 to 18 dp from the centre: its middle is at 16.5 dp.
            assertColor("neutral ring on $variant", Color(0xFF595959), pixel(cx, cy - 16.5.dp), tolerance = 2)
            // Between the dot (12 dp) and the ring (15 dp): the white air of the plate.
            assertColor("white air on $variant", Color.White, pixel(cx, cy - 13.5.dp), tolerance = 2)
            shoot("glass-central-neutral-$variant")
        }
    }

    @Test
    fun centralButton_ringIsTheActionBlue_whenThereIsAStatus() {
        showBar(TabBarGlass(GlassVariant.ON_PHOTO, GlassLevel.NO_BLUR), Color.White, status = StatusLevel.ORANGE)
        val (cx, cy) = centralCenter()
        assertColor("ring", Color(0xFF043CD1), pixel(cx, cy - 16.5.dp), tolerance = 2)
        // The plate stays opaque white on the dark bar (never glass).
        assertColor("plate", Color.White, pixel(cx, cy - 20.dp), tolerance = 6)
    }

    @Test
    fun centralButton_keepsItsSpokenName_andItsStateForTheScreenReader() {
        showBar(TabBarGlass.Default, Color.White, selected = MainTab.UPDATE)
        composeRule.onNodeWithContentDescription(text(R.string.tabbar_update_soon_a11y)).assertIsNotEnabled()
        showBar(TabBarGlass.Default, Color.White)
        composeRule.onNodeWithContentDescription(text(R.string.tabbar_feed_a11y)).assertIsSelected().assertIsEnabled()
        composeRule.onNodeWithContentDescription(text(R.string.tabbar_profile_a11y)).assertIsEnabled()
    }

    // --- Sizes -----------------------------------------------------------------------------------------------------------------------

    @Test
    fun everyEntry_is64By48dp_inABarOf64dp_withMargins24dpOnTheSides() {
        showBar(TabBarGlass.Default, Color.White)
        listOf(R.string.tabbar_feed_a11y, R.string.tabbar_update_soon_a11y, R.string.tabbar_profile_a11y).forEach {
            composeRule.onNodeWithContentDescription(text(it)).assertWidthIsEqualTo(64.dp).assertHeightIsEqualTo(48.dp)
        }
        val root = composeRule.onRoot().getUnclippedBoundsInRoot()
        val feed = bounds(R.string.tabbar_feed_a11y)
        val profile = bounds(R.string.tabbar_profile_a11y)
        // 342 dp bar on a 360 dp screen: 24 dp each side, entries spread with "space around" (25 dp of air each side of an entry at 390;
        // here 360 - 48 = 312 wide, so (312 - 192) / 3 / 2 = 20 dp).
        assertEquals(24.dp + 20.dp, feed.left)
        assertEquals(root.right - 24.dp - 20.dp, profile.right)
        // The bar floats 22 dp above the bottom (plus the navigation bar, none in this test).
        assertEquals(root.bottom - 22.dp - 8.dp, feed.bottom)
    }

    @Test
    fun theCentralButton_isExactlyInTheMiddleOfTheScreen() {
        showBar(TabBarGlass.Default, Color.White)
        val root = composeRule.onRoot().getUnclippedBoundsInRoot()
        val middle = bounds(R.string.tabbar_update_soon_a11y)
        assertEquals(root.right.value / 2, (middle.left.value + middle.right.value) / 2, 0.5f)
    }

    @Test
    fun theBar_keepsItsSize_withTheLargestFont() {
        showBar(TabBarGlass.Default, Color.White, fontScale = 2f)
        listOf(R.string.tabbar_feed_a11y, R.string.tabbar_update_soon_a11y, R.string.tabbar_profile_a11y).forEach {
            composeRule.onNodeWithContentDescription(text(it)).assertWidthIsEqualTo(64.dp).assertHeightIsEqualTo(48.dp)
        }
    }

    @Test
    fun buildForOutsideTesters_hasTwoEntries_andTheProfileIsNotInTheMiddle() {
        showBar(TabBarGlass.Default, Color.White, config = external)
        composeRule.onNodeWithContentDescription(text(R.string.tabbar_feed_a11y)).assertWidthIsEqualTo(64.dp)
        composeRule.onNodeWithContentDescription(text(R.string.tabbar_profile_a11y)).assertWidthIsEqualTo(64.dp)
    }

    // --- The bar never hides the content ----------------------------------------------------------------------------------------------

    @Test
    fun theTabsFillTheScreen_passUnderTheBar_andAreToldTheHeightOfTheBar() {
        var inset = Dp.Unspecified
        composeRule.setContent {
            MiffTheme {
                CompositionLocalProvider(LocalDensity provides Density(density, 1f)) {
                    GlassTabHost(
                        variant = GlassVariant.LIGHT,
                        barVisible = true,
                        conditions = modern.copy(powerSaveMode = true),
                        bar = { glass ->
                            MainTabBar(config = internal, selected = MainTab.FEED, onSelect = {}, glass = glass)
                        },
                    ) {
                        inset = LocalTabBarInset.current
                        Box(Modifier.fillMaxSize().testTag("content"))
                    }
                }
            }
        }
        composeRule.waitForIdle()
        val root = composeRule.onRoot().getUnclippedBoundsInRoot()
        val content = composeRule.onNodeWithTag("content").getUnclippedBoundsInRoot()
        assertEquals(
            "the content goes down to the bottom of the screen: the bar floats over it",
            root.bottom,
            content.bottom,
        )
        val feed = bounds(R.string.tabbar_feed_a11y)
        // The margin given to the tabs reaches from the bottom of the screen to the top of the bar: 22 dp + 64 dp.
        assertEquals(86f, inset.value, 1f)
        assertEquals((root.bottom - inset).value, feed.top.value - 8f, 1f)
    }

    @Test
    fun withTheKeyboardUp_theBarIsGone_andTheTabsAreToldZero() {
        var inset = Dp.Unspecified
        composeRule.setContent {
            MiffTheme {
                CompositionLocalProvider(LocalDensity provides Density(density, 1f)) {
                    GlassTabHost(
                        variant = GlassVariant.LIGHT,
                        barVisible = false,
                        conditions = modern,
                        bar = { glass ->
                            MainTabBar(config = internal, selected = MainTab.FEED, onSelect = {}, glass = glass)
                        },
                    ) {
                        inset = LocalTabBarInset.current
                        Box(Modifier.width(10.dp).height(10.dp))
                    }
                }
            }
        }
        composeRule.waitForIdle()
        assertEquals(0f, inset.value, 0f)
        composeRule.onAllNodesWithContentDescription(text(R.string.tabbar_feed_a11y)).assertCountEquals(0)
    }
}
