package com.membora.app

import com.membora.app.data.MemboraRepository
import com.membora.app.data.DataSyncStatus
import com.membora.app.data.WorkspaceProvisioningState
import com.membora.app.domain.MemberStatus
import com.membora.app.domain.MembershipStatus
import com.membora.app.domain.NewMemberRequest
import com.membora.app.domain.PaymentMethod
import com.membora.app.domain.PaymentStatus
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class MemboraRepositoryTest {
    @Test
    fun localWorkspaceReportsSyncStateAndManualSyncRequiresFirebaseSession() {
        val repository = MemboraRepository()
        var result: Result<Unit>? = null

        assertEquals(DataSyncStatus.LOCAL_ONLY, repository.syncDetails.status)
        repository.syncNow { result = it }

        assertTrue(result!!.isFailure)
        assertEquals(DataSyncStatus.LOCAL_ONLY, repository.syncDetails.status)
        assertNotNull(repository.syncDetails.lastAttemptAt)
        assertTrue(repository.syncDetails.errorMessage!!.contains("Firebase"))
    }

    @Test
    fun seededMembershipsResolveLifecycleStatus() {
        val repository = MemboraRepository()

        assertEquals(MemberStatus.EXPIRING_SOON, repository.memberStatus("m-2"))
        assertEquals(MemberStatus.EXPIRED, repository.memberStatus("m-5"))
        assertEquals(MemberStatus.ACTIVE, repository.memberStatus("m-3"))
    }

    @Test
    fun onboardingCreatesMemberMembershipPaymentAndAuditHistory() {
        val repository = MemboraRepository()
        val originalMemberCount = repository.members.size
        val originalPaymentCount = repository.payments.size

        val memberId = repository.addMember(
            NewMemberRequest(
                firstName = "Ishaan",
                lastName = "Verma",
                mobile = "99999 00000",
                email = "ishaan@example.com",
                city = "Bengaluru",
                emergencyContact = "",
                planId = "plan-flex",
                paymentAmount = 1999.0,
                paymentMethod = PaymentMethod.UPI
            )
        )

        assertEquals(originalMemberCount + 1, repository.members.size)
        assertEquals(originalPaymentCount + 1, repository.payments.size)
        assertNotNull(repository.membershipFor(memberId))
        assertTrue(repository.activitiesFor(memberId).any { it.title == "Member registered" })
        assertTrue(repository.activitiesFor(memberId).any { it.title == "Payment received" })
    }

    @Test
    fun onboardingStoresCompleteMemberProfileAndCustomFields() {
        val repository = MemboraRepository()
        val birthDate = LocalDate.of(1994, 5, 12)

        val memberId = repository.addMember(
            NewMemberRequest(
                firstName = "  Aanya ",
                lastName = " Shah ",
                mobile = "9876543210",
                email = "aanya@example.com",
                city = "Pune",
                emergencyContact = "",
                planId = null,
                paymentAmount = 0.0,
                paymentMethod = PaymentMethod.UPI,
                gender = "Female",
                dateOfBirth = birthDate,
                alternatePhone = "9123456780",
                addressLine = "12 Park Road",
                postalCode = "411001",
                notes = "Prefers evening sessions",
                emergencyContactName = "Rohan Shah",
                emergencyContactPhone = "9988776655",
                emergencyContactRelationship = "Brother",
                customValues = mapOf("Blood group" to "O+")
            )
        )

        val member = repository.member(memberId)!!
        assertEquals("Aanya Shah", member.fullName)
        assertEquals(birthDate, member.dateOfBirth)
        assertEquals("12 Park Road", member.addressLine)
        assertEquals("Rohan Shah", member.emergencyContactName)
        assertEquals("Rohan Shah • 9988776655", member.emergencyContact)
        assertEquals("O+", member.customValues["Blood group"])
    }

    @Test
    fun memberRegistrationRejectsDuplicateMobileAndEmail() {
        val repository = MemboraRepository()
        val originalCount = repository.members.size

        repository.addMember(
            NewMemberRequest(
                firstName = "Unique",
                lastName = "Member",
                mobile = "+91 91234 56789",
                email = "unique.member@example.com",
                city = "Mumbai",
                emergencyContact = "",
                planId = null,
                paymentAmount = 0.0,
                paymentMethod = PaymentMethod.UPI
            )
        )

        assertThrows(IllegalArgumentException::class.java) {
            repository.addMember(
                NewMemberRequest(
                    firstName = "Duplicate",
                    lastName = "Phone",
                    mobile = "9123456789",
                    email = "different@example.com",
                    city = "Mumbai",
                    emergencyContact = "",
                    planId = null,
                    paymentAmount = 0.0,
                    paymentMethod = PaymentMethod.UPI
                )
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            repository.addMember(
                NewMemberRequest(
                    firstName = "Duplicate",
                    lastName = "Email",
                    mobile = "9000012345",
                    email = "UNIQUE.MEMBER@EXAMPLE.COM",
                    city = "Mumbai",
                    emergencyContact = "",
                    planId = null,
                    paymentAmount = 0.0,
                    paymentMethod = PaymentMethod.UPI
                )
            )
        }

        assertEquals(originalCount + 1, repository.members.size)
    }

    @Test
    fun profileEditingPersistsDetailsAndRejectsInvalidContactData() {
        val repository = MemboraRepository()

        val success = repository.updateMemberProfile(
            memberId = "m-1",
            firstName = "Rahul",
            lastName = "Kumar",
            mobile = "9876512340",
            email = "rahul.updated@example.com",
            alternatePhone = "9000011111",
            gender = "Male",
            dateOfBirth = LocalDate.of(1990, 1, 1),
            addressLine = "42 Residency Road",
            city = "Bengaluru",
            postalCode = "560001",
            notes = "Updated at front desk",
            emergencyContactName = "Anita",
            emergencyContactPhone = "9876512000",
            emergencyContactRelationship = "Spouse",
            customValues = mapOf("Goal" to "Strength", "Locker" to "A-12")
        )

        assertTrue(success.isSuccess)
        assertEquals("42 Residency Road", repository.member("m-1")!!.addressLine)
        assertEquals("A-12", repository.member("m-1")!!.customValues["Locker"])
        assertTrue(repository.activitiesFor("m-1").any { it.title == "Member profile updated" })

        val invalid = repository.updateMemberProfile(
            memberId = "m-1",
            firstName = "Rahul",
            lastName = "Kumar",
            mobile = "12",
            email = "not-an-email",
            alternatePhone = "",
            gender = "",
            dateOfBirth = null,
            addressLine = "",
            city = "",
            postalCode = "",
            notes = "",
            emergencyContactName = "",
            emergencyContactPhone = "",
            emergencyContactRelationship = "",
            customValues = emptyMap()
        )
        assertTrue(invalid.isFailure)
    }

    @Test
    fun expiredMembershipCannotCheckIn() {
        val repository = MemboraRepository()

        val result = repository.checkIn("m-5")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("expired") == true)
    }

    @Test
    fun renewalStartsAfterCurrentTerm() {
        val repository = MemboraRepository()
        val currentEnd = repository.membershipFor("m-1")!!.endDate
        val entitlementCount = repository.entitlements.count { it.memberId == "m-1" }

        val renewalId = repository.renewMembership("m-1", "plan-flex")
        val renewal = repository.memberships.first { it.id == renewalId }

        assertEquals(currentEnd.plusDays(1), renewal.startDate)
        assertEquals(currentEnd.plusMonths(1), renewal.endDate)
        assertEquals("ms-1", repository.membershipFor("m-1")!!.id)
        assertEquals(entitlementCount + 1, repository.entitlements.count { it.memberId == "m-1" })
    }

    @Test
    fun membershipSupportsExplicitStartAndExpiryDates() {
        val repository = MemboraRepository()
        val start = repository.currentDate().plusDays(10)
        val end = start.plusMonths(2)

        val id = repository.activateMembership("m-1", "plan-flex", startDate = start, endDate = end)
        val membership = repository.memberships.first { it.id == id }

        assertEquals(start, membership.startDate)
        assertEquals(end, membership.endDate)
        assertEquals(MembershipStatus.UPCOMING, membership.status)
        assertThrows(IllegalArgumentException::class.java) {
            repository.activateMembership("m-1", "plan-flex", startDate = start, endDate = start.minusDays(1))
        }
    }

    @Test
    fun exactDuplicateMembershipTermIsRejected() {
        val repository = MemboraRepository()
        val start = repository.currentDate().plusYears(2)
        val end = start.plusMonths(1).minusDays(1)

        repository.activateMembership("m-1", "plan-flex", startDate = start, endDate = end)

        assertThrows(IllegalArgumentException::class.java) {
            repository.activateMembership("m-1", "plan-flex", startDate = start, endDate = end)
        }
        assertEquals(
            1,
            repository.memberships.count {
                it.memberId == "m-1" && it.planId == "plan-flex" &&
                    it.startDate == start && it.endDate == end
            }
        )
    }

    @Test
    fun paymentsUpdatePartialAndPaidMembershipStatusAndRejectOverpayment() {
        val repository = MemboraRepository()
        val membership = repository.memberships.first { it.id == "ms-3" }

        assertEquals(PaymentStatus.PARTIAL, membership.paymentStatus)
        assertEquals(4999.0, repository.outstandingBalance("ms-3"), 0.001)
        repository.recordPayment("m-3", "ms-3", 4999.0, PaymentMethod.CARD)

        assertEquals(PaymentStatus.PAID, repository.memberships.first { it.id == "ms-3" }.paymentStatus)
        assertEquals(0.0, repository.outstandingBalance("ms-3"), 0.001)
        assertThrows(IllegalArgumentException::class.java) {
            repository.recordPayment("m-3", "ms-3", 1.0, PaymentMethod.CASH)
        }
    }

    @Test
    fun rapidDuplicatePaymentIsRejected() {
        val repository = MemboraRepository()

        repository.recordPayment("m-3", "ms-3", 100.0, PaymentMethod.UPI, "Front desk")

        assertThrows(IllegalArgumentException::class.java) {
            repository.recordPayment("m-3", "ms-3", 100.0, PaymentMethod.UPI, "Front desk")
        }
        assertEquals(
            1,
            repository.payments.count {
                it.memberId == "m-3" && it.membershipId == "ms-3" &&
                    it.amount == 100.0 && it.method == PaymentMethod.UPI && it.notes == "Front desk"
            }
        )
    }

    @Test
    fun freezeBlocksAccessAndResumeRestoresMembership() {
        val repository = MemboraRepository()

        assertTrue(repository.freezeMembership("ms-1", repository.currentDate().plusDays(14), "Medical leave").isSuccess)
        assertEquals(MembershipStatus.FROZEN, repository.memberships.first { it.id == "ms-1" }.status)
        assertTrue(repository.checkIn("m-1").isFailure)

        assertTrue(repository.resumeMembership("ms-1").isSuccess)
        assertEquals(MembershipStatus.ACTIVE, repository.memberships.first { it.id == "ms-1" }.status)
        assertTrue(repository.activitiesFor("m-1").any { it.title == "Membership resumed" })
    }

    @Test
    fun cancellationStopsAccessRetainsHistoryAndAllowsFreshRenewal() {
        val repository = MemboraRepository()

        assertTrue(repository.cancelMembership("ms-4", "Member requested cancellation").isSuccess)
        assertEquals(MembershipStatus.CANCELLED, repository.membershipFor("m-4")!!.status)
        assertEquals("Member requested cancellation", repository.membershipFor("m-4")!!.statusReason)
        assertTrue(repository.checkIn("m-4").isFailure)
        assertThrows(IllegalArgumentException::class.java) {
            repository.recordPayment("m-4", "ms-4", 1.0, PaymentMethod.CASH)
        }

        val renewalId = repository.renewMembership("m-4", "plan-flex")
        val renewal = repository.memberships.first { it.id == renewalId }
        assertEquals(repository.currentDate(), renewal.startDate)
        assertEquals(MembershipStatus.ACTIVE, renewal.status)
        assertEquals(2, repository.membershipsFor("m-4").size)
    }

    @Test
    fun plansValidateConfigurationAndCanBePaused() {
        val repository = MemboraRepository()
        val result = repository.addPlan("Weekly", "Seven-day access", 1, "WEEK", 499.0, 25.0)

        assertTrue(result.isSuccess)
        val planId = result.getOrThrow()
        assertEquals(repository.currentDate().plusDays(6), repository.calculateMembershipEndDate(planId, repository.currentDate()))
        assertTrue(repository.setPlanActive(planId, false).isSuccess)
        assertFalse(repository.plan(planId)!!.active)
        assertThrows(IllegalArgumentException::class.java) { repository.activateMembership("m-1", planId) }
        assertTrue(repository.addPlan("", "", 0, "MONTH", -1.0).isFailure)
        assertTrue(repository.addPlan("weekly", "Duplicate", 1, "WEEK", 499.0).isFailure)
    }

    @Test
    fun reminderEngineCreatesSevenThreeOneDayAndExpiredNotifications() {
        val repository = MemboraRepository()
        val membership = repository.memberships.first { it.id == "ms-1" }

        repository.processDueReminders(membership.endDate.minusDays(7))
        repository.processDueReminders(membership.endDate.minusDays(3))
        repository.processDueReminders(membership.endDate.minusDays(1))
        repository.processDueReminders(membership.endDate.plusDays(1))

        val reminders = repository.communicationsFor("m-1").filter { it.membershipId == "ms-1" }
        assertEquals(setOf("rule-7", "rule-3", "rule-1", "rule-expired", "rule-payment-due"), reminders.mapNotNull { it.automationRuleId }.toSet())
        assertTrue(reminders.all { it.status == "READY" })
        assertTrue(reminders.all { it.deliveryKey.isNotBlank() })
        assertTrue(reminders.none { it.message.contains("{{") })
        assertTrue(reminders.any { it.message.contains("Rahul Kumar") && it.message.contains("Growth Quarterly") })
    }

    @Test
    fun reminderEngineIsIdempotentAndHonorsDisabledRules() {
        val repository = MemboraRepository()
        val membership = repository.memberships.first { it.id == "ms-1" }
        repository.toggleRule("rule-3")

        val firstRun = repository.processDueReminders(membership.endDate.minusDays(7))
        val repeatRun = repository.processDueReminders(membership.endDate.minusDays(7))
        repository.processDueReminders(membership.endDate.minusDays(3))

        assertTrue(firstRun >= 1)
        assertEquals(0, repeatRun)
        assertEquals(1, repository.communicationsFor("m-1").count { it.automationRuleId == "rule-7" && it.membershipId == "ms-1" })
        assertFalse(repository.communicationsFor("m-1").any { it.automationRuleId == "rule-3" && it.membershipId == "ms-1" })
    }

    @Test
    fun birthdayAutomationTriggersOnceAndChannelCanBeChanged() {
        val repository = MemboraRepository()
        val today = repository.currentDate()
        val memberId = repository.addMember(
            NewMemberRequest(
                firstName = "Birthday",
                lastName = "Member",
                mobile = "9777766666",
                email = "birthday@example.com",
                city = "Pune",
                emergencyContact = "",
                planId = null,
                paymentAmount = 0.0,
                paymentMethod = PaymentMethod.UPI,
                dateOfBirth = today.minusYears(25)
            )
        )
        repository.updateRuleChannel("rule-birthday", com.membora.app.domain.CommunicationChannel.SMS)
        repository.toggleRule("rule-birthday")

        assertTrue(repository.lastAutomationCreatedCount >= 1)
        assertEquals(0, repository.processDueReminders(today))
        val birthday = repository.communicationsFor(memberId).single { it.automationRuleId == "rule-birthday" }
        assertEquals(com.membora.app.domain.CommunicationChannel.SMS, birthday.channel)
        assertTrue(birthday.message.contains("Birthday Member"))
    }

    @Test
    fun regionalSettingsUpdateCurrencyTimezoneAndBranch() {
        val repository = MemboraRepository()

        val result = repository.updateOrganization(
            name = repository.organization.name,
            businessType = repository.organization.businessType,
            memberLabel = repository.organization.memberLabel,
            phone = repository.organization.phone,
            email = repository.organization.email,
            currencyCode = "USD",
            timezone = "America/New_York",
            branchName = "Manhattan"
        )

        assertTrue(result.isSuccess)
        assertEquals("USD", repository.organization.currencyCode)
        assertEquals("America/New_York", repository.organization.timezone)
        assertEquals("Manhattan", repository.organization.branchName)
        assertEquals(LocalDate.now(ZoneId.of("America/New_York")), repository.currentDate())
    }

    @Test
    fun invalidRegionalSettingsAreRejectedWithoutChangingOrganization() {
        val repository = MemboraRepository()
        val original = repository.organization

        val result = repository.updateOrganization(
            name = original.name,
            businessType = original.businessType,
            memberLabel = original.memberLabel,
            phone = original.phone,
            email = original.email,
            currencyCode = "INVALID",
            timezone = "Not/A_Timezone",
            branchName = original.branchName
        )

        assertTrue(result.isFailure)
        assertEquals(original, repository.organization)
    }

    @Test
    fun organizationProfileStoresBusinessTypeAndFullAddress() {
        val repository = MemboraRepository()

        val result = repository.updateOrganization(
            name = "Acme Academy",
            businessType = "Education & Academy",
            memberLabel = "Student",
            phone = "+1 555 0100",
            email = "office@acme.example",
            currencyCode = "USD",
            timezone = "America/New_York",
            branchName = "Manhattan",
            addressLine1 = "100 Learning Avenue",
            addressLine2 = "Floor 4",
            city = "New York",
            state = "NY",
            postalCode = "10001",
            countryCode = "US"
        )

        assertTrue(result.isSuccess)
        assertEquals("Education & Academy", repository.organization.businessType)
        assertEquals("100 Learning Avenue", repository.organization.addressLine1)
        assertEquals("New York", repository.organization.city)
        assertEquals("US", repository.organization.countryCode)
    }

    @Test
    fun createOrganizationCompletesWorkspaceProvisioning() {
        val repository = MemboraRepository()

        val result = repository.createOrganization(
            name = "Membora Test Org",
            businessType = "Professional Association",
            email = "owner@example.com",
            phone = "1234567890",
            branchName = "Head Office",
            addressLine1 = "1 Main Street",
            city = "Mumbai",
            state = "Maharashtra",
            postalCode = "400001",
            countryCode = "IN",
            currencyCode = "INR",
            timezone = "Asia/Kolkata"
        )

        assertTrue(result.isSuccess)
        assertEquals(WorkspaceProvisioningState.READY, repository.workspaceProvisioningState)
        assertEquals("Membora Test Org", repository.organization.name)
    }

    @Test
    fun staffInvitationsValidateDuplicatesAndProtectOwnerAccess() {
        val repository = MemboraRepository()

        val invitation = repository.inviteStaff(
            name = "Operations User",
            email = "operations@example.com",
            phone = "9999999999",
            role = "Manager",
            branchName = "Indiranagar"
        )

        assertTrue(invitation.isSuccess)
        val invited = repository.staff.first { it.id == invitation.getOrThrow() }
        assertEquals("PENDING", invited.invitationStatus)
        assertTrue(repository.inviteStaff("Duplicate", "operations@example.com", "", "Viewer", "Main").isFailure)
        assertTrue(repository.setStaffActive("staff-1", false).isFailure)
        assertTrue(repository.setStaffActive(invited.id, false).isSuccess)
        assertFalse(repository.staff.first { it.id == invited.id }.active)
    }

    @Test
    fun memberMessagingDraftsContainCurrentPaymentAndDueDetails() {
        val repository = MemboraRepository()
        val paymentId = repository.recordPayment("m-3", "ms-3", 100.0, PaymentMethod.UPI, "Message test")

        val payment = repository.paymentConfirmationDraft(paymentId)
        val reminder = repository.dueReminderDraft("m-3")

        assertEquals(repository.member("m-3")!!.mobile, payment.recipient)
        assertTrue(payment.message.contains("100"))
        assertTrue(payment.message.contains(repository.payments.first { it.id == paymentId }.receiptNumber))
        assertTrue(reminder.message.contains("due", ignoreCase = true))
        assertTrue(reminder.message.contains(repository.organization.name))
    }

    @Test
    fun welcomeAndRenewalDraftsUseMemberAndMembershipDetails() {
        val repository = MemboraRepository()
        val memberId = repository.addMember(
            NewMemberRequest(
                firstName = "Message",
                lastName = "Member",
                mobile = "9888877777",
                email = "message.member@example.com",
                city = "Mumbai",
                emergencyContact = "",
                planId = "plan-flex",
                paymentAmount = 1999.0,
                paymentMethod = PaymentMethod.UPI
            )
        )
        val welcome = repository.welcomeMessageDraft(memberId)
        val renewalId = repository.renewMembership(memberId, "plan-growth")
        val renewal = repository.renewalThankYouDraft(memberId, renewalId)

        assertTrue(welcome.message.contains("Welcome"))
        assertTrue(welcome.message.contains(repository.member(memberId)!!.code))
        assertTrue(renewal.message.contains("Thank you for renewing"))
        assertTrue(renewal.message.contains("Growth Quarterly"))
    }
}
