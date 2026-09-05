package com.jeelgajera.fold.feature.transfer.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkAddress
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import java.net.Inet4Address

/** Which transport the phone is on, and what address the server would bind to. */
data class NetworkState(
    val ipv4: String?,
    val ssid: String?,
    val isWifi: Boolean,
    val isCellularOnly: Boolean,
) {
    val canServe: Boolean get() = ipv4 != null
}

/**
 * Works out where the server should listen.
 *
 * The rule is that FOLD binds to the Wi-Fi interface's own address and never to
 * `0.0.0.0`. Binding to all interfaces would put the server on cellular, on a
 * VPN's tunnel and on any USB tether that happened to be up -- none of which is
 * what "share over Wi-Fi" means to the person who tapped the button, and one of
 * which can mean "share with the internet" behind a carrier that hands out public
 * addresses.
 *
 * When the phone is on cellular only, [NetworkState.isCellularOnly] is true and
 * the UI refuses to start without an explicit confirmation.
 */
object NetworkAddress {

    fun current(context: Context): NetworkState {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val network = connectivity?.activeNetwork
        val capabilities = network?.let { connectivity.getNetworkCapabilities(it) }

        val isWifi = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val isCellular = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true

        val properties = network?.let { connectivity.getLinkProperties(it) }
        val ipv4 = properties?.linkAddresses
            ?.firstOrNull { it.isUsableIpv4() }
            ?.address?.hostAddress

        return NetworkState(
            ipv4 = if (isWifi) ipv4 else null,
            ssid = ssid(context),
            isWifi = isWifi,
            isCellularOnly = isCellular && !isWifi,
        )
    }

    private fun LinkAddress.isUsableIpv4(): Boolean =
        address is Inet4Address && !address.isLoopbackAddress && !address.isLinkLocalAddress

    /**
     * The network's name, for "Open that address in a browser on <SSID>".
     *
     * Reading it needs location permission on modern Android, and FOLD does not
     * ask for location just to print a network name. Without the permission this
     * returns null and the copy says "your Wi-Fi network" instead -- a slightly
     * less specific sentence is a better trade than a location prompt in a file
     * manager.
     */
    private fun ssid(context: Context): String? = try {
        val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
        @Suppress("DEPRECATION")
        wifi?.connectionInfo?.ssid
            ?.removeSurrounding("\"")
            ?.takeUnless { it.isEmpty() || it == "<unknown ssid>" }
    } catch (e: SecurityException) {
        null
    }
}
