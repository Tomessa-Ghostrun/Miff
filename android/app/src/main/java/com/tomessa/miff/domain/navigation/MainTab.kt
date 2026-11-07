package com.tomessa.miff.domain.navigation

/**
 * The three destinations of the tab bar (Onboarding ticket 6.7, decision Q1 of the feed / profile / settings ticket): the
 * feed on the left, the round button in the middle, the profile on the right (the order is reversed by the system in a
 * right-to-left language: the bar is laid out with start and end, never left and right).
 */
enum class MainTab {
    FEED,

    /** "Donner des nouvelles": the Update screen (ticket UPDATE): viewfinder, preview, publication. */
    UPDATE,
    PROFILE,
}

/**
 * What the bar shows. Since UPDATE-1 the central button HAS a destination (the Update screen), so it is active in every build, the ones meant for
 * outside testers included (decision D14 of the Update ticket is lifted by the ticket itself): the bar always has three tabs.
 */
data class TabBarConfig(
    val showUpdateButton: Boolean,
) {
    /** The tabs drawn, in order. */
    val tabs: List<MainTab> =
        buildList {
            add(MainTab.FEED)
            if (showUpdateButton) add(MainTab.UPDATE)
            add(MainTab.PROFILE)
        }

    /** Every tab drawn can be opened. */
    @Suppress("FunctionOnlyReturningConstant")
    fun isEnabled(tab: MainTab): Boolean = true

    companion object {
        /** [isInternalBuild] is kept for the callers: the central button no longer depends on the build. */
        @Suppress("UNUSED_PARAMETER")
        fun forBuild(isInternalBuild: Boolean) = TabBarConfig(showUpdateButton = true)
    }
}
