package com.membora.app.data

enum class WorkspacePermission {
    READ_MEMBERS,
    MANAGE_MEMBERS,
    MANAGE_MEMBERSHIPS,
    RECORD_PAYMENTS,
    CHECK_IN_MEMBERS,
    MANAGE_CATALOG,
    VIEW_REPORTS,
    MANAGE_AUTOMATIONS,
    MANAGE_STAFF,
    MANAGE_ORGANIZATION
}

data class WorkspaceAccess(
    val ownerUid: String,
    val businessId: String,
    val role: String,
    val staffId: String = "",
    val active: Boolean = true
)

internal fun permissionsForRole(role: String): Set<WorkspacePermission> = when (role) {
    "Owner", "Administrator" -> WorkspacePermission.entries.toSet()
    "Manager" -> setOf(
        WorkspacePermission.READ_MEMBERS,
        WorkspacePermission.MANAGE_MEMBERS,
        WorkspacePermission.MANAGE_MEMBERSHIPS,
        WorkspacePermission.RECORD_PAYMENTS,
        WorkspacePermission.CHECK_IN_MEMBERS,
        WorkspacePermission.MANAGE_CATALOG,
        WorkspacePermission.VIEW_REPORTS,
        WorkspacePermission.MANAGE_AUTOMATIONS
    )
    "Front Desk" -> setOf(
        WorkspacePermission.READ_MEMBERS,
        WorkspacePermission.MANAGE_MEMBERS,
        WorkspacePermission.MANAGE_MEMBERSHIPS,
        WorkspacePermission.RECORD_PAYMENTS,
        WorkspacePermission.CHECK_IN_MEMBERS
    )
    "Accountant" -> setOf(
        WorkspacePermission.READ_MEMBERS,
        WorkspacePermission.RECORD_PAYMENTS,
        WorkspacePermission.VIEW_REPORTS
    )
    "Viewer" -> setOf(
        WorkspacePermission.READ_MEMBERS,
        WorkspacePermission.VIEW_REPORTS
    )
    else -> emptySet()
}
