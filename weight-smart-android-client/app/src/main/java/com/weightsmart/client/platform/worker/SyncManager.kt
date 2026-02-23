package com.weightsmart.client.platform.worker

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.weightsmart.client.platform.connectivity.ConnectivityObserver
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SyncManager
 * Orchestrates all sync operations and manages WorkManager jobs.
 *
 * Architecture Role:
 * Central coordinator that owns the sync lifecycle:
 *   1. Initial sync on login -- fetches 100 records via existing pagination endpoint
 *   2. Periodic background sync -- runs every 15 minutes (battery-aware)
 *   3. Connectivity-triggered sync -- fires when device regains network
 *   4. Manual sync -- exposed for pull-to-refresh from the UI
 *
 * Lifecycle & Scheduling:
 * Injected as a Singleton. Connectivity observer is started in WeightSmartApp.onCreate().
 * Uses a SupervisorJob scope so individual sync failures don't cancel the observer.
 * All work requests require NetworkType.CONNECTED constraint so they only execute online.
 *
 * Key Concepts & Documentation:
 * WorkManager: Android's recommended solution for deferrable, guaranteed background work.
 * <a href="https://developer.android.com/topic/libraries/architecture/workmanager">Reference: WorkManager</a>
 * Constraints: Conditions (network, battery) that must be met before work executes.
 * <a href="https://developer.android.com/topic/libraries/architecture/workmanager/how-to/define-work#constraints">Reference: Constraints</a>
 * ExistingWorkPolicy.REPLACE: Cancels any pending sync and replaces it with a fresh one.
 *
 * @author James Chase
 * @version 1.0
 * @since 2026-02-04
 */
@Singleton
class SyncManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val connectivityObserver: ConnectivityObserver
) {
    // WorkManager instance scoped to the application
    private val workManager = WorkManager.getInstance(context)
    // SupervisorJob scope: individual sync failures don't cancel the connectivity observed
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    companion object {
        private const val TAG = "SyncManager"
        private const val INITIAL_SYNC_WORK = "initial_sync_work"
        private const val IMMEDIATE_SYNC_WORK = "immediate_sync_work"
        private const val PERIODIC_SYNC_WORK = "periodic_sync_work"
    }

    /**
     * Start observing connectivity and trigger sync when online.
     * Call this from Application.onCreate() or after login.
     */
    fun startConnectivityObserver() {
        Log.d(TAG, "Starting connectivity observer")
        connectivityObserver.observe()
            .onEach { status ->
                Log.d(TAG, "Connectivity status: $status")
                if (status == ConnectivityObserver.ConnectionStatus.Available) {
                    requestImmediateSync()
                }
            }
            .launchIn(scope)
    }

    /**
     * Trigger initial sync after login.
     * Fetches the most recent 100 records for new devices.
     * Uses existing pagination endpoint (10 pages x 10 records).
     */
    fun triggerInitialSync() {
        Log.d(TAG, "Triggering initial sync")

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .setInputData(
                workDataOf(SyncWorker.KEY_IS_INITIAL_SYNC to true)
            )
            .addTag("sync")
            .addTag("initial_sync")
            .build()

        workManager.enqueueUniqueWork(
            INITIAL_SYNC_WORK,
            ExistingWorkPolicy.REPLACE,
            syncRequest
        )
    }

    /**
     * Request an immediate delta sync.
     * Used when connectivity is restored or on manual refresh.
     */
    fun requestImmediateSync() {
        if (!connectivityObserver.isOnline()) {
            Log.d(TAG, "Offline, skipping immediate sync")
            return
        }

        Log.d(TAG, "Requesting immediate sync")

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .setInputData(
                workDataOf(SyncWorker.KEY_IS_INITIAL_SYNC to false)
            )
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                30, TimeUnit.SECONDS
            )
            .addTag("sync")
            .addTag("immediate_sync")
            .build()

        workManager.enqueueUniqueWork(
            IMMEDIATE_SYNC_WORK,
            ExistingWorkPolicy.REPLACE,
            syncRequest
        )
    }

    /**
     * Schedule periodic background sync.
     * Runs every 15 minutes when device is connected and battery is not low.
     */
    fun schedulePeriodicSync() {
        Log.d(TAG, "Scheduling periodic sync")

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        val periodicRequest = PeriodicWorkRequestBuilder<SyncWorker>(
            15, TimeUnit.MINUTES,
            5, TimeUnit.MINUTES  // Flex interval
        )
            .setConstraints(constraints)
            .setInputData(
                workDataOf(SyncWorker.KEY_IS_INITIAL_SYNC to false)
            )
            .addTag("sync")
            .addTag("periodic_sync")
            .build()

        workManager.enqueueUniquePeriodicWork(
            PERIODIC_SYNC_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            periodicRequest
        )
    }

    /**
     * Cancel all sync jobs (e.g., on logout).
     */
    fun cancelAllSync() {
        Log.d(TAG, "Cancelling all sync work")
        workManager.cancelAllWorkByTag("sync")
        workManager.cancelUniqueWork(INITIAL_SYNC_WORK)
        workManager.cancelUniqueWork(IMMEDIATE_SYNC_WORK)
        workManager.cancelUniqueWork(PERIODIC_SYNC_WORK)
    }
}
