package com.membora.app.data

import java.time.LocalDateTime

enum class DataSyncStatus { LOCAL_ONLY, CONNECTING, SYNCING, SYNCED, OFFLINE, ERROR }

data class DataSyncDetails(
    val status: DataSyncStatus = DataSyncStatus.LOCAL_ONLY,
    val usingCachedData: Boolean = false,
    val pendingWrites: Int = 0,
    val manualSyncInProgress: Boolean = false,
    val activeListeners: Int = 0,
    val expectedListeners: Int = 13,
    val lastServerSyncAt: LocalDateTime? = null,
    val lastAttemptAt: LocalDateTime? = null,
    val errorMessage: String? = null
)
