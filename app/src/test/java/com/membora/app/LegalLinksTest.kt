package com.membora.app

import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Test

class LegalLinksTest {
    @Test
    fun playPolicyLinksUsePublicHttpsHosting() {
        listOf(LegalLinks.PRIVACY_POLICY, LegalLinks.ACCOUNT_DELETION).forEach { value ->
            val uri = URI(value)
            assertEquals("https", uri.scheme)
            assertEquals("membora-app.web.app", uri.host)
        }
    }
}
