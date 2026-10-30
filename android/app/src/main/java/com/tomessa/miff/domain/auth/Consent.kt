package com.tomessa.miff.domain.auth

import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.FieldValue

/**
 * Versions of the texts the person saw when ticking the two boxes. Shared constants with iOS: change them with the text.
 */
object ConsentVersions {
    /** (a) Terms of service and privacy policy: the published version of docs/legal (1 to 20 characters, rules). */
    const val TERMS = "0.1"

    /** (b) Text of the optional analytics / crash reports box. Format AAAA-MM-vNN (rules: the version cannot go back). */
    const val ANALYTICS = "2026-10-v01"
}

/** What the person accepted by tapping the main button of the sign-up screen (or of the consent panel). */
data class ConsentChoice(
    val termsAccepted: Boolean,
    val analyticsAccepted: Boolean,
) {
    companion object {
        /**
         * Since the PM decision of 2026-10-28 there is no box: tapping "Rejoindre" / "Continuer" accepts the terms (a)
         * AND the sharing of anonymous statistics (b). The legal and store risk is assumed by the PM.
         */
        val IMPLICIT = ConsentChoice(termsAccepted = true, analyticsAccepted = true)
    }
}

/**
 * What the device remembers between the sign-up (or the consent panel) and the creation of the profile:
 * the version of the terms shown, and the choice for statistics. Tied to the account so that it can never be
 * applied to another person. Contains no password, no token, no e-mail.
 */
data class PendingConsent(
    val uid: String,
    val termsVersion: String,
    val analyticsAccepted: Boolean,
)

/**
 * The ONE write that creates `users/{uid}` (backend/data-model.md section 8, rules AUTH-B and ONB-B): every key at once,
 * server time for the dates, `analyticsConsent` ALWAYS written so that the state is explicit, and the phone number in
 * E.164 (REQUIRED by the rules since ONB-B: a profile without it is refused). Nothing else is accepted by the rules (no
 * e-mail, no age, no birth date, no country: the country is deduced from the number's prefix).
 *
 * The profile screens (Onboarding ticket, ONB-2) call [UserProfileRepository.create]; the shape of the document lives
 * here, with its tests.
 */
object ProfileDocument {
    const val MAX_NAME_LENGTH = 50
    const val MAX_AVATAR_BYTES = 20_000

    fun isNameValid(name: String): Boolean = name.trim().length in 1..MAX_NAME_LENGTH

    /** Same shape as the rules: "+" then 7 to 15 digits, the first one not zero (8 to 16 characters in all). */
    val E164 = Regex("^[+][1-9][0-9]{6,14}$")

    fun forCreation(
        firstName: String,
        lastName: String,
        avatarJpeg: ByteArray?,
        phone: String,
        consent: PendingConsent,
    ): Map<String, Any?> {
        require(isNameValid(firstName) && isNameValid(lastName)) { "name length" }
        require(avatarJpeg == null || avatarJpeg.size <= MAX_AVATAR_BYTES) { "avatar size" }
        // The value is not repeated in the message: a phone number never goes into a log or a crash report.
        require(E164.matches(phone)) { "phone is not E.164" }
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
                "phone" to phone,
                "createdAt" to FieldValue.serverTimestamp(),
                "termsAcceptedAt" to FieldValue.serverTimestamp(),
                "termsVersion" to consent.termsVersion,
                "analyticsConsent" to analyticsConsent,
            )
        if (avatarJpeg != null) fields["avatar"] = Blob.fromBytes(avatarJpeg)
        return fields
    }
}
