package com.membora.app

import com.membora.app.ui.AuthDestination
import com.membora.app.ui.authDestination
import org.junit.Assert.assertEquals
import org.junit.Test

class AuthDestinationTest {
    @Test
    fun splashWaitsForMinimumDisplayAndFirebaseObservation() {
        assertEquals(
            AuthDestination.SPLASH,
            destination(splashFinished = false, authObserved = true)
        )
        assertEquals(
            AuthDestination.SPLASH,
            destination(splashFinished = true, authObserved = false)
        )
    }

    @Test
    fun signedOutAndUnacceptedAnonymousUsersSeeAuthentication() {
        assertEquals(
            AuthDestination.AUTHENTICATION,
            destination(hasUser = false)
        )
        assertEquals(
            AuthDestination.AUTHENTICATION,
            destination(hasUser = true, isAnonymous = true)
        )
    }

    @Test
    fun acceptedGuestAndPermanentUsersReachWorkspace() {
        assertEquals(
            AuthDestination.WORKSPACE,
            destination(hasUser = true, isAnonymous = true, guestSessionAccepted = true)
        )
        assertEquals(
            AuthDestination.WORKSPACE,
            destination(hasUser = true, isAnonymous = false)
        )
    }

    @Test
    fun upgradingGuestReturnsToAuthenticationWithoutDroppingAnonymousUser() {
        assertEquals(
            AuthDestination.AUTHENTICATION,
            destination(hasUser = true, isAnonymous = true, guestSessionAccepted = false)
        )
    }

    @Test
    fun expiredGuestCannotReachWorkspaceEvenWhenPreviouslyAccepted() {
        assertEquals(
            AuthDestination.AUTHENTICATION,
            destination(
                hasUser = true,
                isAnonymous = true,
                guestSessionAccepted = true,
                guestTrialExpired = true
            )
        )
    }

    @Test
    fun localBuildWithoutFirebaseStillReachesWorkspaceAfterSplash() {
        assertEquals(
            AuthDestination.WORKSPACE,
            destination(firebaseConfigured = false, authObserved = false)
        )
    }

    private fun destination(
        firebaseConfigured: Boolean = true,
        splashFinished: Boolean = true,
        authObserved: Boolean = true,
        hasUser: Boolean = false,
        isAnonymous: Boolean = false,
        guestSessionAccepted: Boolean = false,
        guestTrialExpired: Boolean = false
    ) = authDestination(
        firebaseConfigured = firebaseConfigured,
        splashFinished = splashFinished,
        authObserved = authObserved,
        hasUser = hasUser,
        isAnonymous = isAnonymous,
        guestSessionAccepted = guestSessionAccepted,
        guestTrialExpired = guestTrialExpired
    )
}
