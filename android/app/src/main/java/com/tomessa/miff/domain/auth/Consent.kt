package com.tomessa.miff.domain.auth

import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.FieldValue

/**
 * Versions of the texts the person saw when tapping the main button (there is no box to tick since the PM decision of
 * 2026-10-28). Shared constants with iOS: change them with the text.
 */
object ConsentVersions {
    /** Terms of service and privacy policy: the published version of docs/legal (1 to 20 characters, rules). */
    const val TERMS = "0.1"

    /** Text of the line about statistics / crash reports. Format AAAA-MM-vNN (rules: the version cannot go back). */
    const val ANALYTICS = "2026-10-v01"
}

/**
 * What the person accepted by tapping the main button of the sign-up screen ("Rejoindre", "Continuer avec Google") or
 * of the consent panel ("Continuer"). There is no box: the tap accepts the terms, the privacy policy AND the sharing of
 * anonymous statistics and crash reports (one single consent), so there is nothing else to choose. The legal and store
 * risk of this choice is assumed by the PM.
 */
data class ConsentChoice(
    val termsAccepted: Boolean,
) {
    companion object {
        val IMPLICIT = ConsentChoice(termsAccepted = true)
    }
}

/**
 * What the device remembers between the sign-up (or the consent panel) and the creation of the profile:
 * the version of the terms shown, and the statistics consent that goes with it (always true for what the app records
 * today; `false` stays possible in the document so that the state is explicit). Tied to the account so that it can
 * never be applied to another person. Contains no password, no token, no e-mail.
 */
data class PendingConsent(
    val uid: String,
    val termsVersion: String,
    val analyticsAccepted: Boolean,
)

/**
 * The ONE write that creates `users/{uid}` (backend/data-model.md section 8, rules AUTH-B): every key at once, server
 * time for the dates, and `analyticsConsent` ALWAYS written so that the state is explicit. Nothing else is accepted by
 * the rules (no e-mail, no age, no birth date, no phone).
 *
 * The profile screens belong to the Onboarding ticket, which calls [UserProfileRepository.create]; the shape of the
 * document lives here, with its tests.
 */
object ProfileDocument {
    const val MAX_NAME_LENGTH = 50
    const val MAX_AVATAR_BYTES = 20_000

    fun isNameValid(name: String): Boolean = name.trim().length in 1..MAX_NAME_LENGTH

    fun forCreation(
        firstName: String,
        lastName: String,
        avatarJpeg: ByteArray?,
        consent: PendingConsent,
    ): Map<String, Any?> {
        require(isNameValid(firstName) && isNameValid(lastName)) { "name length" }
        require(avatarJpeg == null || avatarJpeg.size <= MAX_AVATAR_BYTES) { "avatar size" }
        val analyticsConsent: Map<String, Any?> =
            if (consent.analyticsAccepted) {
                mapOf(
                    "enabled" to true,
                    "consentedAt" to FieldValue.serverTimestamp(),
                    "consentVersion" to ConsentVersions.ANALYTICS,
                    "revokedAt" to null,
                )
            } else {
                mapOf(
                    "enabled" to false,
                    "consentedAt" to null,
                    "consentVersion" to null,
                    "revokedAt" to null,
                )
            }
        val fields =
            mutableMapOf<String, Any?>(
                "firstName" to firstName.trim(),
                "lastName" to lastName.trim(),
                "createdAt" to FieldValue.serverTimestamp(),
                "termsAcceptedAt" to FieldValue.serverTimestamp(),
                "termsVersion" to consent.termsVersion,
                "analyticsConsent" to analyticsConsent,
            )
        if (avatarJpeg != null) fields["avatar"] = Blob.fromBytes(avatarJpeg)
        return fields
    }
}
