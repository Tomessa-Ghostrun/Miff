package com.tomessa.miff

import android.graphics.Color
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
import com.tomessa.miff.ui.navigation.MiffNavHost
import com.tomessa.miff.ui.splash.ReduceMotion
import com.tomessa.miff.ui.splash.SplashHost
import com.tomessa.miff.ui.theme.MiffTheme

class MainActivity : ComponentActivity() {
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
}
