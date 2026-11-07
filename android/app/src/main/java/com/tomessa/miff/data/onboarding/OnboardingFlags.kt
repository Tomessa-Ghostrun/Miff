package com.tomessa.miff.data.onboarding

import com.tomessa.miff.data.privacy.BooleanStore

/**
 * Local flags of the onboarding. Not personal data, never sent anywhere, kept on the device only (reinstalling the app
 * may reset them: no consequence). The welcome carousel is shown on the first opening only (decision Q11 of the
 * Authentification ticket): the flag is set when the person taps "Commencer" or "J'ai déjà un compte".
 */
class OnboardingFlags(
    private val store: BooleanStore,
) {
    val carouselSeen: Boolean get() = store.get(KEY_CAROUSEL_SEEN, false)

    fun markCarouselSeen() {
        store.put(KEY_CAROUSEL_SEEN, true)
    }

    companion object {
        const val KEY_CAROUSEL_SEEN = "welcome_carousel_seen"
    }
}
