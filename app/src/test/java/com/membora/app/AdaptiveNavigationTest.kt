package com.membora.app

import androidx.compose.ui.unit.dp
import com.membora.app.ui.Destination
import com.membora.app.ui.RootTab
import com.membora.app.ui.backDestination
import com.membora.app.ui.shouldUseNavigationRail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveNavigationTest {
    @Test
    fun navigationRailRequiresEnoughWidthAndHeight() {
        assertTrue(shouldUseNavigationRail(width = 800.dp, height = 600.dp))
        assertFalse(shouldUseNavigationRail(width = 900.dp, height = 411.dp))
        assertFalse(shouldUseNavigationRail(width = 599.dp, height = 800.dp))
    }

    @Test
    fun memberDetailReturnsToItsSourceTab() {
        RootTab.entries.forEach { origin ->
            val detail = Destination.MemberDetail(memberId = "m-1", origin = origin)
            assertEquals(Destination.Root(origin), detail.backDestination())
        }
    }
}
