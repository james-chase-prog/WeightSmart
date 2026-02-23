package com.weightsmart.client.platform.worker

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.weightsmart.client.data.SessionManager
import com.weightsmart.client.data.local.WeightDao
import com.weightsmart.client.data.remote.WeightSmartApi
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SyncWorkerFactory
 * Custom WorkerFactory that creates SyncWorker instances with injected dependencies.
 *
 * Architecture Role:
 * Bridges Hilt dependency injection with WorkManager's worker creation.
 * Used instead of @HiltWorker to avoid kapt/Kotlin 2.x metadata incompatibility.
 * Registered in WeightSmartApp via Configuration.Provider so WorkManager
 * uses this factory instead of the default reflection-based one.
 *
 * Key Concepts & Documentation:
 * WorkerFactory: Allows custom creation of Worker instances with constructor injection.
 * <a href="https://developer.android.com/topic/libraries/architecture/workmanager/advanced/custom-configuration">Reference: Custom Configuration</a>
 *
 * @author James Chase
 * @version 1.0
 * @since 2026-02-06
 */
@Singleton
class SyncWorkerFactory @Inject constructor(
    private val api: WeightSmartApi,
    private val dao: WeightDao,
    private val sessionManager: SessionManager
) : WorkerFactory() {

    /**
     * Creates a worker instance for the given class name.
     * Returns a fully-injected SyncWorker for known class names, or null to
     * fall back to WorkManager's default reflection-based factory for unknown workers.
     */
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters
    ): ListenableWorker? {
        return when (workerClassName) {
            SyncWorker::class.java.name -> SyncWorker(
                context = appContext,
                params = workerParameters,
                api = api,
                dao = dao,
                sessionManager = sessionManager
            )
            // Return null for unknown workers - WorkManager will use default factory
            else -> null
        }
    }
}
