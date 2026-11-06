package com.tomessa.miff.data.auth

import com.tomessa.miff.domain.auth.AuthFailure
import com.tomessa.miff.domain.auth.SessionCheck
import com.tomessa.miff.domain.auth.SignInProvider
import org.junit.Assert.assertEquals
import org.junit.Test

/** The two readings the deletion of the account makes of Firebase: which methods are linked, and what a `reload` answer means. */
class AuthGatewayHelpersTest {
    @Test
    fun theLinkedProviders_arePasswordGoogleAndApple_andNothingElse() {
        assertEquals(
            setOf(SignInProvider.PASSWORD, SignInProvider.GOOGLE, SignInProvider.APPLE),
            FirebaseAuthGateway.providersFrom(
                listOf("firebase", "password", "google.com", "apple.com", "facebook.com"),
            ),
        )
        assertEquals(emptySet<SignInProvider>(), FirebaseAuthGateway.providersFrom(emptyList()))
        // "firebase" alone is the base provider of the user, not a method to prove who the person is.
        assertEquals(emptySet<SignInProvider>(), FirebaseAuthGateway.providersFrom(listOf("firebase")))
    }

    @Test
    fun aReload_saysGoneOnlyWhenTheUserDoesNotExist() {
        assertEquals(SessionCheck.VALID, FirebaseAuthGateway.sessionCheckOf(null))
        assertEquals(SessionCheck.GONE, FirebaseAuthGateway.sessionCheckOf(AuthFailure.ACCOUNT_GONE))
        assertEquals(SessionCheck.EXPIRED, FirebaseAuthGateway.sessionCheckOf(AuthFailure.SESSION_EXPIRED))
        assertEquals(SessionCheck.UNKNOWN, FirebaseAuthGateway.sessionCheckOf(AuthFailure.NETWORK))
        assertEquals(SessionCheck.UNKNOWN, FirebaseAuthGateway.sessionCheckOf(AuthFailure.UNKNOWN))
    }
}
