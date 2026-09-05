package com.jeelgajera.fold.feature.transfer.net

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.net.InetAddress

/** Another FOLD instance seen on the network. */
data class DiscoveredPeer(
    val name: String,
    val host: String,
    val port: Int,
) {
    val url: String get() = "http://$host:$port"
}

/**
 * mDNS advertisement and discovery over Android's NSD.
 *
 * Two FOLD instances on the same Wi-Fi find each other without anyone typing an
 * IP address. Nothing is transmitted by discovery beyond a service name, a host
 * and a port -- no file list, no device identifier, and no contact with anything
 * outside the local network.
 *
 * Discovery is not a permission to send. A peer appearing in the list means "this
 * device is running FOLD"; a transfer still requires the receiving side to accept,
 * which is why the receive screen says so in as many words.
 */
class Discovery(context: Context) {

    private val nsd: NsdManager? = context.getSystemService(NsdManager::class.java)
    private var registration: NsdManager.RegistrationListener? = null

    /**
     * Advertises this device.
     *
     * The service name is the phone's model, not a user-chosen name and not
     * anything identifying: it appears on every screen in range, so it should say
     * "Phone (2a)" and nothing more.
     */
    fun advertise(port: Int, onRegistered: (String) -> Unit = {}) {
        val manager = nsd ?: return
        unadvertise()

        val info = NsdServiceInfo().apply {
            serviceName = Build.MODEL.take(30).ifBlank { "FOLD" }
            serviceType = SERVICE_TYPE
            setPort(port)
        }

        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {
                // The system may rename on a collision; report the real name back
                // so the UI does not claim one the network did not accept.
                onRegistered(info.serviceName)
            }

            override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                Log.i(TAG, "mDNS registration failed ($errorCode); the server still works by address")
            }

            override fun onServiceUnregistered(info: NsdServiceInfo) = Unit
            override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) = Unit
        }

        registration = listener
        runCatching { manager.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener) }
    }

    fun unadvertise() {
        val manager = nsd ?: return
        registration?.let { runCatching { manager.unregisterService(it) } }
        registration = null
    }

    /**
     * Peers on the network, emitted as they appear.
     *
     * Resolution is serialised through a queue because `NsdManager.resolveService`
     * rejects concurrent calls with `FAILURE_ALREADY_ACTIVE` -- a detail that is
     * easy to miss and shows up as peers silently never resolving.
     */
    fun discover(): Flow<List<DiscoveredPeer>> = callbackFlow {
        val manager = nsd ?: run {
            send(emptyList())
            awaitClose { }
            return@callbackFlow
        }

        val found = LinkedHashMap<String, DiscoveredPeer>()
        val pending = ArrayDeque<NsdServiceInfo>()
        var resolving = false

        fun resolveNext() {
            if (resolving) return
            val next = pending.removeFirstOrNull() ?: return
            resolving = true

            @Suppress("DEPRECATION")
            manager.resolveService(
                next,
                object : NsdManager.ResolveListener {
                    override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                        resolving = false
                        resolveNext()
                    }

                    override fun onServiceResolved(info: NsdServiceInfo) {
                        val host: InetAddress? = @Suppress("DEPRECATION") info.host
                        val address = host?.hostAddress
                        if (address != null) {
                            found[info.serviceName] = DiscoveredPeer(
                                name = info.serviceName,
                                host = address,
                                port = info.port,
                            )
                            trySend(found.values.toList())
                        }
                        resolving = false
                        resolveNext()
                    }
                },
            )
        }

        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit

            override fun onServiceFound(info: NsdServiceInfo) {
                if (info.serviceType.contains(SERVICE_TYPE.trimEnd('.'), ignoreCase = true)) {
                    pending.addLast(info)
                    resolveNext()
                }
            }

            override fun onServiceLost(info: NsdServiceInfo) {
                found.remove(info.serviceName)
                trySend(found.values.toList())
            }

            override fun onDiscoveryStopped(serviceType: String) = Unit

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                runCatching { manager.stopServiceDiscovery(this) }
                close()
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                runCatching { manager.stopServiceDiscovery(this) }
            }
        }

        runCatching {
            manager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        }

        awaitClose { runCatching { manager.stopServiceDiscovery(listener) } }
    }

    private companion object {
        /** Registered per RFC 6763's convention: an app-specific name under _tcp. */
        const val SERVICE_TYPE = "_fold._tcp."
        const val TAG = "FoldDiscovery"
    }
}
