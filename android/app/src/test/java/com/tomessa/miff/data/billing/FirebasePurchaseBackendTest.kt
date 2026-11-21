package com.tomessa.miff.data.billing

import com.google.firebase.functions.FirebaseFunctionsException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The contract with the server (backend/functions/src/achatErreurs.ts, ticket premium-achat-reel.md section 22): what a refusal of the callable functions means for the app, and what
 * is accepted from the network as a purchase id.
 */
class FirebasePurchaseBackendTest {
    @Test
    fun theCodesOfTheServer_mapToTheOutcomesOfThePurchase() {
        val expected =
            mapOf(
                // "deja_lie": another Miff account owns this purchase.
                FirebaseFunctionsException.Code.ALREADY_EXISTS to SyncResult.AlreadyLinked,
                // "limite_frequence": one check per minute and per account.
                FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED to SyncResult.RateLimited,
                // "entree_invalide", "jeton_invalide", "signature_invalide" / "achat_inconnu" / "produit_inconnu", "autre_package", "partage_famille".
                FirebaseFunctionsException.Code.INVALID_ARGUMENT to SyncResult.Rejected,
                FirebaseFunctionsException.Code.NOT_FOUND to SyncResult.Rejected,
                FirebaseFunctionsException.Code.FAILED_PRECONDITION to SyncResult.Rejected,
                // "non_configure", "acces_refuse", "indisponible", "reponse_invalide": the store or the service is momentarily out of reach.
                FirebaseFunctionsException.Code.UNAVAILABLE to SyncResult.Unavailable,
                FirebaseFunctionsException.Code.DEADLINE_EXCEEDED to SyncResult.Unavailable,
                FirebaseFunctionsException.Code.INTERNAL to SyncResult.Unavailable,
                FirebaseFunctionsException.Code.UNKNOWN to SyncResult.Unavailable,
                // No App Check, no sign-in: nothing granted, and nothing said about why (the screen shows its calm sentence).
                FirebaseFunctionsException.Code.UNAUTHENTICATED to SyncResult.Unavailable,
                FirebaseFunctionsException.Code.PERMISSION_DENIED to SyncResult.Unavailable,
            )
        expected.forEach { (code, result) ->
            assertEquals(code.toString(), result, FirebasePurchaseBackend.syncFailure(code))
        }
    }

    @Test
    fun aMissingSignInOrAppCheck_isARefusal_notAnOutage() {
        assertTrue(FirebasePurchaseBackend.isRefusal(FirebaseFunctionsException.Code.UNAUTHENTICATED))
        assertTrue(FirebasePurchaseBackend.isRefusal(FirebaseFunctionsException.Code.PERMISSION_DENIED))
        assertFalse(FirebasePurchaseBackend.isRefusal(FirebaseFunctionsException.Code.UNAVAILABLE))
        assertFalse(FirebasePurchaseBackend.isRefusal(FirebaseFunctionsException.Code.INTERNAL))
    }

    @Test
    fun onlyAUuidIsAcceptedAsAPurchaseId_dataFromTheNetworkIsNeverTrusted() {
        assertTrue(FirebasePurchaseBackend.isPurchaseId("7b0c9a3e-1f2d-4c5b-8a6e-9d0f1a2b3c4d"))
        assertTrue(FirebasePurchaseBackend.isPurchaseId("7B0C9A3E-1F2D-4C5B-8A6E-9D0F1A2B3C4D"))
        listOf(
            "",
            "not-a-uuid",
            "7b0c9a3e1f2d4c5b8a6e9d0f1a2b3c4d",
            "7b0c9a3e-1f2d-4c5b-8a6e-9d0f1a2b3c4d ",
            " 7b0c9a3e-1f2d-4c5b-8a6e-9d0f1a2b3c4d",
            "7b0c9a3e-1f2d-4c5b-8a6e-9d0f1a2b3c4d\n",
            "7b0c9a3e-1f2d-4c5b-8a6e-9d0f1a2b3c4dZZ",
            "uid-1234567890",
            "someone@example.com",
        ).forEach { assertFalse("'$it'", FirebasePurchaseBackend.isPurchaseId(it)) }
    }

    @Test
    fun theRegionAndTheNamesAreTheOnesOfTheServer() {
        assertEquals("europe-west9", FirebasePurchaseBackend.REGION)
        assertEquals("preparerAchat", FirebasePurchaseBackend.PREPARE)
        assertEquals("synchroniserAchat", FirebasePurchaseBackend.SYNC)
        assertEquals("supprimerDonneesAbonnement", FirebasePurchaseBackend.PURGE)
    }
}
