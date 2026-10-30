package com.tomessa.miff.domain.navigation

/**
 * The three destinations of the tab bar (Onboarding ticket 6.7, decision Q1 of the feed / profile / settings ticket): the
 * feed on the left, the round button in the middle, the profile on the right (the order is reversed by the system in a
 * right-to-left language: the bar is laid out with start and end, never left and right).
 */
enum class MainTab {
    FEED,

    /** "Donner des nouvelles": the manual update of the UPDATE ticket, which does not exist yet. */
    UPDATE,
    PROFILE,
}

/**
 * What the bar shows. The central button has no destination until the UPDATE ticket exists (decision Q1): it is then
 * VISIBLE BUT DISABLED in the builds the team tests itself (debug), and ABSENT from the builds meant for outside testers
 * (release; the bar then has two tabs). A button that does nothing must never reach someone who does not know why.
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

    /** Tabs that can be opened. The central button stays a placeholder until its ticket exists. */
    fun isEnabled(tab: MainTab): Boolean = tab != MainTab.UPDATE

    companion object {
        /** [isInternalBuild]: the debug build (`BuildConfig.DEBUG`). */
        fun forBuild(isInternalBuild: Boolean) = TabBarConfig(showUpdateButton = isInternalBuild)
    }
}
