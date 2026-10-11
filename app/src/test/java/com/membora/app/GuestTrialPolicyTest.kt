package com.membora.app

import com.membora.app.ui.GUEST_TRIAL_DURATION_MILLIS
import com.membora.app.ui.guestTrialStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuestTrialPolicyTest {
    private val day = 24L * 60L * 60L * 1_000L

    @Test
    fun newGuestReceivesFifteenDays() {
        val startedAt = 1_000_000L
        val status = guestTrialStatus(startedAt, null, startedAt)

        assertFalse(status.isExpired)
        assertEquals(15, status.daysRemaining)
        assertEquals(startedAt + GUEST_TRIAL_DURATION_MILLIS, status.expiresAtMillis)
    }

    @Test
    fun partialDaysRoundUpForTheUserFacingCountdown() {
        val startedAt = 1_000_000L
        val status = guestTrialStatus(startedAt, null, startedAt + 14L * day + 1L)

        assertFalse(status.isExpired)
        assertEquals(1, status.daysRemaining)
    }

    @Test
    fun guestExpiresAtExactlyFifteenDays() {
        val startedAt = 1_000_000L
        val status = guestTrialStatus(
            startedAt,
            null,
            startedAt + GUEST_TRIAL_DURATION_MILLIS
        )

        assertTrue(status.isExpired)
        assertEquals(0, status.daysRemaining)
    }

    @Test
    fun earlierInstallationStartPreventsSignOutFromRestartingTrial() {
        val installationStart = 1_000_000L
        val replacementAnonymousAccountCreatedAt = installationStart + 10L * day
        val status = guestTrialStatus(
            replacementAnonymousAccountCreatedAt,
            installationStart,
            installationStart + 15L * day
        )

        assertTrue(status.isExpired)
        assertEquals(installationStart, status.startedAtMillis)
    }
}
