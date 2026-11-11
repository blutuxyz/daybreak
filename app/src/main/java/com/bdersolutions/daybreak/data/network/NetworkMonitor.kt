package com.bdersolutions.daybreak.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether the phone can reach the internet right now, as a flow. "Online" means the system has checked that
 * the default network really reaches the internet (validated), not just that Wi-Fi or mobile data is on.
 * Screens that need the server ask again when this turns true, so a start without a connection heals itself.
 */
class NetworkMonitor(context: Context) {
    private val manager = context.getSystemService(ConnectivityManager::class.java)
    private val _online = MutableStateFlow(currentlyOnline())
    val online: StateFlow<Boolean> = _online.asStateFlow()

    init {
        runCatching {
            manager?.registerNetworkCallback(
                NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(),
                object : ConnectivityManager.NetworkCallback() {
                    override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                        _online.value = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                    }

                    override fun onLost(network: Network) {
                        _online.value = currentlyOnline()
                    }
                },
            )
        }
    }

    private fun currentlyOnline(): Boolean {
        val manager = manager ?: return false
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
