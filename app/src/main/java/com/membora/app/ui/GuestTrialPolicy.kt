package com.membora.app.ui

import kotlin.math.ceil

internal const val GUEST_TRIAL_DAYS = 15
internal const val GUEST_TRIAL_DURATION_MILLIS = GUEST_TRIAL_DAYS * 24L * 60L * 60L * 1_000L

internal data class GuestTrialStatus(
    val startedAtMillis: Long,
    val expiresAtMillis: Long,
    val remainingMillis: Long,
    val daysRemaining: Int,
    val isExpired: Boolean
)

/**
 * Uses the earliest known start so signing out and receiving a new anonymous UID on the same
 * installation cannot restart the guest trial. Firebase's user creation time remains the primary
 * server-issued start for a normal anonymous session.
 */
internal fun guestTrialStatus(
    anonymousUserCreatedAtMillis: Long?,
    installationTrialStartedAtMillis: Long?,
    nowMillis: Long
): GuestTrialStatus {
    val validStarts = listOfNotNull(
        anonymousUserCreatedAtMillis?.takeIf { it > 0L },
        installationTrialStartedAtMillis?.takeIf { it > 0L }
    )
    val startedAt = validStarts.minOrNull() ?: nowMillis
    val expiresAt = startedAt + GUEST_TRIAL_DURATION_MILLIS
    val remaining = (expiresAt - nowMillis).coerceAtLeast(0L)
    val daysRemaining = if (remaining == 0L) 0 else {
        ceil(remaining.toDouble() / (24L * 60L * 60L * 1_000L)).toInt()
    }
    return GuestTrialStatus(
        startedAtMillis = startedAt,
        expiresAtMillis = expiresAt,
        remainingMillis = remaining,
        daysRemaining = daysRemaining,
        isExpired = nowMillis >= expiresAt
    )
}
