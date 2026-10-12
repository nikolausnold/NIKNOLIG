package com.niknolig.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper

internal class InternetStatusMonitor(
    context: Context,
    private val onStatusChanged: (InternetStatus) -> Unit
) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isRegistered = false

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            publish(statusFor(network))
        }

        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities
        ) {
            publish(statusFor(networkCapabilities))
        }

        override fun onLost(network: Network) {
            publish(currentStatus())
        }
    }

    fun start() {
        publish(currentStatus())
        val manager = connectivityManager ?: return
        if (isRegistered) return

        try {
            manager.registerDefaultNetworkCallback(networkCallback, mainHandler)
            isRegistered = true
        } catch (_: RuntimeException) {
            publish(InternetStatus.UNKNOWN)
        }
    }

    fun stop() {
        if (!isRegistered) return
        try {
            connectivityManager?.unregisterNetworkCallback(networkCallback)
        } catch (_: RuntimeException) {
            // The system may already have removed the callback.
        } finally {
            isRegistered = false
        }
    }

    private fun currentStatus(): InternetStatus {
        val manager = connectivityManager ?: return InternetStatus.UNKNOWN
        val network = try {
            manager.activeNetwork
        } catch (_: RuntimeException) {
            return InternetStatus.UNKNOWN
        } ?: return InternetStatus.OFFLINE

        return statusFor(network)
    }

    private fun statusFor(network: Network): InternetStatus {
        val capabilities = try {
            connectivityManager?.getNetworkCapabilities(network)
        } catch (_: RuntimeException) {
            return InternetStatus.UNKNOWN
        }
        return statusFor(capabilities)
    }

    private fun statusFor(capabilities: NetworkCapabilities?): InternetStatus =
        InternetStatusClassifier.classify(
            networkAvailable = true,
            capabilitiesAvailable = capabilities != null,
            hasInternetCapability = capabilities?.hasCapability(
                NetworkCapabilities.NET_CAPABILITY_INTERNET
            ) == true,
            internetValidated = capabilities?.hasCapability(
                NetworkCapabilities.NET_CAPABILITY_VALIDATED
            ) == true
        )

    private fun publish(status: InternetStatus) {
        onStatusChanged(status)
    }
}
