package com.tomessa.miff.domain.privacy

import com.google.firebase.firestore.FieldValue
import com.tomessa.miff.domain.auth.ConsentVersions

/**
 * What to write in `users/{uid}.analyticsConsent` when the person switches "statistiques et rapports de plantage" on or
 * off in Settings > Confidentialité (ticket ONB-3b, criterion RG6). Pure logic: the transitions are those allowed by the
 * Firestore rules (`validAnalyticsConsentChange`, backend/data-model.md section 2.1), nothing else would be accepted:
 *
 * - **activate / reactivate** (from "absent", "refused" or "withdrawn"): `enabled` true, `consentedAt` = server time,
 *   `consentVersion` = the version of the text shown, `revokedAt` null; the version can never go back;
 * - **withdraw** (from "active" only): `enabled` false, `revokedAt` = server time, and the TRACE of the consent (`consentedAt`
 *   and `consentVersion`) kept as it was;
 * - nothing to write when the account already is in the wanted state.
 *
 * No location, no personal data: four keys about a choice.
 */
object AnalyticsConsentTransition {
    sealed interface Change {
        /** The account already is in the wanted state: no write. */
        data object None : Change

        /** The block to write as the new `analyticsConsent`. */
        data class Write(
            val block: Map<String, Any?>,
        ) : Change

        /** A newer text was already accepted than the one this app knows: the app must not write an older version. */
        data object Refused : Change
    }

    /** [previous] is the block as the server holds it now (null = absent), as read in the same transaction. */
    fun change(
        previous: Map<String, Any?>?,
        enable: Boolean,
        version: String = ConsentVersions.ANALYTICS,
    ): Change {
        val wasEnabled = previous?.get("enabled") == true
        if (enable) {
            if (wasEnabled) return Change.None
            val lastVersion = previous?.get("consentVersion") as? String
            if (lastVersion != null && version < lastVersion) return Change.Refused
            return Change.Write(
                mapOf(
                    "enabled" to true,
                    "consentedAt" to FieldValue.serverTimestamp(),
                    "consentVersion" to version,
                    "revokedAt" to null,
                ),
            )
        }
        // Only an ACTIVE consent can be withdrawn.
        val active = previous?.takeIf { wasEnabled } ?: return Change.None
        return Change.Write(
            mapOf(
                "enabled" to false,
                "consentedAt" to active["consentedAt"],
                "consentVersion" to active["consentVersion"],
                "revokedAt" to FieldValue.serverTimestamp(),
            ),
        )
    }
}
