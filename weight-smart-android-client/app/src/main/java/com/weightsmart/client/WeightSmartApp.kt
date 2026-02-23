package com.weightsmart.client

import android.app.Application
import androidx.work.Configuration
import com.weightsmart.client.platform.worker.SyncManager
import com.weightsmart.client.platform.worker.SyncWorkerFactory
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * WeightSmartApp
 * The Application class for WeightSmart.
 *
 * Architecture Role:
 * 1. Hilt Entry Point: @HiltAndroidApp triggers Hilt's code generation for dependency injection.
 * 2. WorkManager Setup: Provides custom Configuration with SyncWorkerFactory.
 * 3. Sync Initialization: Starts connectivity observer and schedules periodic sync.
 *
 * Key Concepts & Documentation:
 * @HiltAndroidApp: Must be placed on the Application class for Hilt DI to work.
 * <a href="https://developer.android.com/training/dependency-injection/hilt-android">Reference: Hilt Android</a>
 * Configuration.Provider: Allows WorkManager to use custom WorkerFactory for injection.
 * <a href="https://developer.android.com/topic/libraries/architecture/workmanager/advanced/custom-configuration">Reference: WorkManager Custom Configuration</a>
 *
 * @author James Chase
 * @version 1.2
 * @since 2026-02-04
 */
@HiltAndroidApp
class WeightSmartApp : Application(), Configuration.Provider {

    //Custom WorkerFactory injected by Hilt -- provides constructor injection to SyncWorker
    @Inject
    lateinit var workerFactory: SyncWorkerFactory

    //Sync orchestrator injected by Hilt -- manages initial, periodic, and connectivity-triggered syncs
    @Inject
    lateinit var syncManager: SyncManager

    override fun onCreate() {
        super.onCreate()

        // Start connectivity observer for auto-sync when network becomes available
        syncManager.startConnectivityObserver()

        // Schedule periodic background sync (every 15 minutes)
        syncManager.schedulePeriodicSync()
    }

    /**
     * WorkManager configuration with custom SyncWorkerFactory.
     * Uses manual factory injection instead of @HiltWorker due to kapt/Kotlin 2.x incompatibility.
     */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(android.util.Log.DEBUG)
            .build()
}
