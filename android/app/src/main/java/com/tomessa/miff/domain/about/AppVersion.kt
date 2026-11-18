package com.tomessa.miff.domain.about

/**
 * The version of the installed app, as Settings > "À propos et aide" shows and copies it (ticket reglages-a-propos.md, AP3, decisions Q4 and Q7).
 *
 * PRIVACY: this is the version name and the internal build number and NOTHING else: no model of phone, no Android version, no identifier, no account
 * (decision Q7). What a person copies is something they can read on the screen and paste where they want.
 */
data class AppVersion(
    val name: String,
    val build: String,
) {
    /** What is copied on a touch: "Miff 0.39.0 (39000)". The name of the app is not translated; the format is the same in every language. */
    val clipboardText: String get() = "Miff $name ($build)"

    companion object {
        /** From the build: `BuildConfig.VERSION_NAME` and `BuildConfig.VERSION_CODE`. */
        fun of(
            versionName: String?,
            versionCode: Int,
        ): AppVersion = AppVersion(name = versionName?.trim().orEmpty().ifEmpty { "?" }, build = versionCode.toString())
    }
}
