package com.tomessa.miff.domain.paywall

/**
 * Told by every overlay that sits over the tabs and is NOT a screen of the navigation (a dialog, a bottom sheet, a drop-down menu, the full-screen photo): while one is open the periodic
 * offer never comes (ticket docs/tickets/paywall-periodique.md, 4.1 "no sheet", PP9 and PP12). An overlay is a separate window: it does not change the state of the screen behind it and its
 * touches never reach the root, so it has to say itself that it is there. Implemented by [PeriodicPaywallRules]; the screens reach it through `LocalOverlayPresence`.
 */
interface OverlayPresence {
    /** An overlay appeared. */
    fun onOverlayOpened()

    /** An overlay went away (the person closed it, or the screen under it left). Counts as a touch: the offer waits for a calm moment AFTER it. */
    fun onOverlayClosed()
}
