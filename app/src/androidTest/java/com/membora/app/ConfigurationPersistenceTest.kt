package com.membora.app

import android.Manifest
import android.os.Build
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.membora.app.data.MemboraViewModel
import com.membora.app.domain.NewMemberRequest
import com.membora.app.domain.PaymentMethod
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConfigurationPersistenceTest {
    @Test
    fun repositoryDataSurvivesActivityRecreation() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            instrumentation.uiAutomation.grantRuntimePermission(
                instrumentation.targetContext.packageName,
                Manifest.permission.POST_NOTIFICATIONS
            )
        }

        lateinit var memberId: String
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->

            scenario.onActivity { activity ->
                val viewModel = ViewModelProvider(activity)[MemboraViewModel::class.java]
                memberId = viewModel.repository.addMember(
                    NewMemberRequest(
                        firstName = "Rotation",
                        lastName = "Test",
                        mobile = "9999900000",
                        email = "rotation@example.com",
                        city = "Bengaluru",
                        emergencyContact = "",
                        planId = null,
                        paymentAmount = 0.0,
                        paymentMethod = PaymentMethod.UPI
                    )
                )
            }

            scenario.recreate()

            scenario.onActivity { activity ->
                val viewModel = ViewModelProvider(activity)[MemboraViewModel::class.java]
                assertNotNull(viewModel.repository.member(memberId))
            }
        }
    }
}
