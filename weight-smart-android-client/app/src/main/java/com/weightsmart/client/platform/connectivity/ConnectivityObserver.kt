package com.weightsmart.client.platform.connectivity

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ConnectivityObserver
 * Monitors network connectivity changes and exposes them as a reactive Flow.
 *
 * Architecture Role:
 * Platform-level service that wraps Android's ConnectivityManager callback API
 * into a Kotlin coroutine Flow. Used by SyncManager to trigger sync when the
 * device regains network connectivity. The Flow uses distinctUntilChanged() to
 * avoid redundant emissions when the status hasn't actually changed.
 *
 * Lifecycle:
 * Singleton-scoped and injected by Hilt. The NetworkCallback is registered
 * when the Flow is collected and unregistered when the collector cancels
 * (via callbackFlow's awaitClose). This prevents memory leaks from dangling
 * system callbacks.
 *
 * Key Concepts & Documentation:
 * callbackFlow: Converts callback-based APIs to coroutine Flows safely.
 * <a href="https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.flow/callback-flow.html">Reference: callbackFlow</a>
 * NetworkCallback: Android's listener for network state changes.
 * <a href="https://developer.android.com/reference/android/net/ConnectivityManager.NetworkCallback">Reference: NetworkCallback</a>
 * distinctUntilChanged: Filters consecutive duplicate emissions from the Flow.
 *
 * @author James Chase
 * @version 1.0
 * @since 2026-02-04
 */
@Singleton
class ConnectivityObserver @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    /**
     * Observe network status changes as a Flow of ConnectionStatus.
     * Emits immediately with current state, then on every change.
     *
     * Usage:
     * connectivityObserver.observe()
     *     .onEach { status -> if (status == Available) triggerSync() }
     *     .launchIn(scope)
     */
    fun observe(): Flow<ConnectionStatus> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(ConnectionStatus.Available)
            }

            override fun onLosing(network: Network, maxMsToLive: Int) {
                trySend(ConnectionStatus.Losing)
            }

            override fun onLost(network: Network) {
                trySend(ConnectionStatus.Lost)
            }

            override fun onUnavailable() {
                trySend(ConnectionStatus.Unavailable)
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(request, callback)

        // Emit initial state
        trySend(getCurrentStatus())

        awaitClose {
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }.distinctUntilChanged()

    /**
     * Synchronously check current connectivity status.
     */
    fun getCurrentStatus(): ConnectionStatus {
        val network = connectivityManager.activeNetwork ?: return ConnectionStatus.Unavailable
        val capabilities = connectivityManager.getNetworkCapabilities(network)
            ?: return ConnectionStatus.Unavailable

        return if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            ConnectionStatus.Available
        } else {
            ConnectionStatus.Unavailable
        }
    }

    /**
     * Quick check if device is currently online.
     */
    fun isOnline(): Boolean = getCurrentStatus() == ConnectionStatus.Available

    /**
     * Sealed class representing network connection states.
     */
    sealed class ConnectionStatus {
        data object Available : ConnectionStatus()
        data object Unavailable : ConnectionStatus()
        data object Losing : ConnectionStatus()
        data object Lost : ConnectionStatus()
    }
}
