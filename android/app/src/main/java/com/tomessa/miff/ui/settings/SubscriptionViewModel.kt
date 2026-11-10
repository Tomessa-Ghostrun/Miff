package com.tomessa.miff.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tomessa.miff.data.subscription.SubscriptionRepository
import com.tomessa.miff.domain.subscription.SubscriptionInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The "Abonnement" line of the Settings (ticket ABO-1). Reads `entitlements/{uid}` ONCE when the Settings open (a ViewModel
 * lives as long as that screen is in the back stack: going to Mon compte and back does not read again, nor does a rotation).
 * Until the answer comes, and whenever there is no active Miff+ (or no network, or an error), [subscription] is null and the
 * line is simply not there: no spinner, no message.
 */
class SubscriptionViewModel(
    repository: SubscriptionRepository,
) : ViewModel() {
    private val _subscription = MutableStateFlow<SubscriptionInfo?>(null)
    val subscription: StateFlow<SubscriptionInfo?> = _subscription.asStateFlow()

    init {
        viewModelScope.launch { _subscription.value = repository.load() }
    }
}
