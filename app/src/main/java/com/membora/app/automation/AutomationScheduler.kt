package com.membora.app.automation

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.membora.app.MemboraApplication
import com.membora.app.data.WorkspaceProvisioningState
import com.membora.app.firebase.FirebaseServices
import com.membora.app.firebase.MemboraNotifications
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

object AutomationScheduler {
    private const val PERIODIC_WORK = "membora-automation-periodic"
    private const val STARTUP_WORK = "membora-automation-startup"

    fun schedule(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val periodic = PeriodicWorkRequestBuilder<AutomationWorker>(12, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            periodic
        )
        runNow(context)
    }

    fun runNow(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            STARTUP_WORK,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<AutomationWorker>().setConstraints(constraints).build()
        )
    }
}

class AutomationWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        FirebaseServices.initialize(applicationContext)
        FirebaseServices.auth?.currentUser ?: return Result.success()
        val app = applicationContext as MemboraApplication
        val ready = withTimeoutOrNull(25_000) {
            while (
                app.repository.workspaceProvisioningState == WorkspaceProvisioningState.LOADING ||
                !app.repository.isAutomationDataReady
            ) {
                delay(250)
            }
            app.repository.workspaceProvisioningState == WorkspaceProvisioningState.READY &&
                app.repository.isAutomationDataReady
        } ?: false
        if (!ready) {
            return if (runAttemptCount < 3) Result.retry() else Result.success()
        }
        app.repository.processDueReminders()
        val delivery = AutomaticMessageDelivery.deliverReady(app.repository)
        val prepared = app.repository.consumePendingAutomationNotificationCount()
        if (prepared > 0 || delivery.submitted > 0) {
            MemboraNotifications.showAutomationSummary(applicationContext, prepared, delivery.submitted)
        }
        return if (delivery.retryNeeded && runAttemptCount < 3) Result.retry() else Result.success()
    }
}
