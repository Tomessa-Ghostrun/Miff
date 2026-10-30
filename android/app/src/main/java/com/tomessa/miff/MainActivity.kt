package com.tomessa.miff

import android.content.Context
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.tomessa.miff.data.settings.AndroidLanguageStore
import com.tomessa.miff.ui.navigation.MiffNavHost
import com.tomessa.miff.ui.splash.ReduceMotion
import com.tomessa.miff.ui.splash.SplashHost
import com.tomessa.miff.ui.theme.MiffTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    /** Android 12 and earlier: the screen is built in the language chosen in Settings (from Android 13 the system does it). */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AndroidLanguageStore.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // System launch screen (blue, "M + i" without the heart), then the animation of ui/splash takes over.
        // Nothing is held back: the system screen goes away as soon as the first frame is drawn. Removing it at once
        // (no system exit animation) keeps the hand-over to the Compose animation free of any visible jump.
        installSplashScreen().setOnExitAnimationListener { it.remove() }
        // Light app: dark status/navigation bar icons whatever the system theme is.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        val container = (application as MiffApplication).container
        // Read once per launch; the person's "remove animations" choice gives a still logo instead of the animation.
        val reduceMotion = ReduceMotion.isRequested(this)
        recreateWhenLanguageChanges(container)
        setContent {
            MiffTheme {
                SplashHost(reduceMotion = reduceMotion) {
                    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        MiffNavHost(container = container)
                    }
                }
            }
        }
    }

    /**
     * Android 12 and earlier have no system mechanism for the language of one app: when the choice of Settings > Langue
     * changes, the screen is built again to apply it (the state of the app is kept, see rememberSaveable). From Android 13
     * the system itself builds the screen again.
     */
    private fun recreateWhenLanguageChanges(container: AppContainer) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return
        val applied = AndroidLanguageStore.read(this)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                container.languageStore.selected.collect { if (it != applied) recreate() }
            }
        }
    }
}
