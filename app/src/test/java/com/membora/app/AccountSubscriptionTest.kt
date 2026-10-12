package com.membora.app

import com.membora.app.billing.AccountSubscription
import com.membora.app.billing.SubscriptionTerm
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountSubscriptionTest {
    @Test
    fun playBasePlanIdsMatchTheFourSupportedTerms() {
        assertEquals(
            listOf("monthly", "three-month", "six-month", "annual"),
            SubscriptionTerm.entries.map { it.basePlanId }
        )
        assertEquals(listOf(1, 3, 6, 12), SubscriptionTerm.entries.map { it.months })
    }

    @Test
    fun accessRequiresBothServerEntitlementAndFutureExpiry() {
        assertTrue(AccountSubscription(
            entitled = true,
            expiresAt = Instant.now().plusSeconds(60)
        ).hasAccess)
        assertFalse(AccountSubscription(
            entitled = false,
            expiresAt = Instant.now().plusSeconds(60)
        ).hasAccess)
        assertFalse(AccountSubscription(
            entitled = true,
            expiresAt = Instant.now().minusSeconds(1)
        ).hasAccess)
    }
}
