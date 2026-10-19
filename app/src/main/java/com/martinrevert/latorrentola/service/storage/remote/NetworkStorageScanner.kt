package com.martinrevert.latorrentola.service.storage.remote

import android.content.Context
import android.net.ConnectivityManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Proactively scans the local area network to discover active SMB and NFS servers
 * and their public exported shares without requiring manual user IP entry.
 */
@Singleton
class NetworkStorageScanner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val smbClient: SmbStorageClient,
    private val nfsClient: NfsStorageClient
) {
    companion object {
        private const val TAG = "NetworkStorageScanner"
        private const val SMB_PORT = 445
        private const val NFS_PORT = 2049
        private const val NETBIOS_PORT = 137
        private const val PROBE_TIMEOUT_MS = 250
    }

    /**
     * Proactively scans local subnet and native broadcast to discover active SMB & NFS servers
     * and their exported shares.
     */
    suspend fun scanNetwork(): List<RemoteServerConfig> = withContext(Dispatchers.IO) {
        val discoveredServers = mutableListOf<RemoteServerConfig>()
        val seenHosts = mutableSetOf<String>()

        // 1. Native NFS server broadcast discovery (via libnfs)
        if (LibNfsNative.isAvailable) {
            try {
                val nfsHosts = LibNfsNative.nativeFindLocalServers()
                nfsHosts?.forEach { host ->
                    val cleanHost = host.trim()
                    if (cleanHost.isNotBlank() && !isVirtualOrP2pSubnet(cleanHost) && seenHosts.add("nfs:$cleanHost")) {
                        discoveredServers.add(
                            RemoteServerConfig(
                                id = "discovered-nfs-$cleanHost",
                                name = "NFS ($cleanHost)",
                                type = RemoteServerType.NFS,
                                host = cleanHost,
                                port = NFS_PORT,
                                shareOrExport = ""
                            )
                        )
                    }
                }
            } catch (e: Throwable) {
                Log.d(TAG, "Native NFS broadcast scan error: ${e.message}")
            }
        }

        // 2. Discover local subnet IPv4 addresses
        val activeIp = getActiveNetworkIpv4()
        val subnetIps = if (activeIp != null) getSubnetIpsFor(activeIp) else emptyList()
        val subnetPrefix = activeIp?.substringBeforeLast('.')

        // 3. Fast NetBIOS Name Service (NBNS) UDP 137 broadcast discovery for SMB
        if (subnetPrefix != null) {
            try {
                val netBiosHosts = scanNetBiosBroadcast("$subnetPrefix.255")
                for (host in netBiosHosts) {
                    if (!isVirtualOrP2pSubnet(host) && seenHosts.add("smb:$host")) {
                        discoveredServers.add(
                            RemoteServerConfig(
                                id = "discovered-smb-$host",
                                name = "SMB ($host)",
                                type = RemoteServerType.SMB,
                                host = host,
                                port = SMB_PORT,
                                shareOrExport = ""
                            )
                        )
                    }
                }
            } catch (e: Throwable) {
                Log.d(TAG, "NetBIOS broadcast scan error: ${e.message}")
            }
        }

        // 4. Fast parallel TCP probe for ports 445 (SMB) and 2049 (NFS)
        if (subnetIps.isNotEmpty()) {
            val probeResults = subnetIps.chunked(32).flatMap { batch ->
                batch.map { ip ->
                    async {
                        val smbOpen = if (!seenHosts.contains("smb:$ip")) isPortOpen(ip, SMB_PORT, PROBE_TIMEOUT_MS) else false
                        val nfsOpen = if (!seenHosts.contains("nfs:$ip")) isPortOpen(ip, NFS_PORT, PROBE_TIMEOUT_MS) else false
                        Triple(ip, smbOpen, nfsOpen)
                    }
                }.awaitAll()
            }

            for ((ip, smbOpen, nfsOpen) in probeResults) {
                if (smbOpen && seenHosts.add("smb:$ip")) {
                    discoveredServers.add(
                        RemoteServerConfig(
                            id = "discovered-smb-$ip",
                            name = "SMB ($ip)",
                            type = RemoteServerType.SMB,
                            host = ip,
                            port = SMB_PORT,
                            shareOrExport = ""
                        )
                    )
                }
                if (nfsOpen && seenHosts.add("nfs:$ip")) {
                    discoveredServers.add(
                        RemoteServerConfig(
                            id = "discovered-nfs-$ip",
                            name = "NFS ($ip)",
                            type = RemoteServerType.NFS,
                            host = ip,
                            port = NFS_PORT,
                            shareOrExport = ""
                        )
                    )
                }
            }
        }

        // 5. For each discovered server, expand public shares/exports proactively
        val expandedServers = mutableListOf<RemoteServerConfig>()
        for (server in discoveredServers) {
            try {
                val entryPoints = if (server.type == RemoteServerType.SMB) {
                    smbClient.listEntryPoints(server)
                } else {
                    nfsClient.listEntryPoints(server)
                }

                if (entryPoints.isNotEmpty()) {
                    entryPoints.forEach { share ->
                        val cleanShare = share.trimStart('/')
                        val shareLabel = if (cleanShare.isBlank()) server.name else "${server.name} / $cleanShare"
                        expandedServers.add(
                            server.copy(
                                id = "${server.type.name.lowercase()}-${server.host}-${cleanShare.replace('/', '_')}",
                                name = shareLabel,
                                shareOrExport = if (server.type == RemoteServerType.NFS) (if (share.startsWith("/")) share else "/$share") else cleanShare
                            )
                        )
                    }
                } else {
                    expandedServers.add(server)
                }
            } catch (_: Throwable) {
                expandedServers.add(server)
            }
        }

        expandedServers
    }

    private fun isPortOpen(ip: String, port: Int, timeoutMs: Int): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(ip, port), timeoutMs)
                true
            }
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Sends a NetBIOS node status query to the broadcast address on UDP port 137.
     * SMB servers on the LAN respond immediately with their IP address and NetBIOS name.
     */
    private fun scanNetBiosBroadcast(broadcastIp: String): Set<String> {
        val foundIps = mutableSetOf<String>()
        val socket = DatagramSocket()
        socket.broadcast = true
        socket.soTimeout = 400

        try {
            // NetBIOS wildcard node status request (*\x00...)
            val requestData = byteArrayOf(
                0x80.toByte(), 0x00.toByte(), 0x00, 0x00, 0x00, 0x01, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00, 0x20, 0x43, 0x4b, 0x41,
                0x41, 0x41, 0x41, 0x41, 0x41, 0x41, 0x41, 0x41,
                0x41, 0x41, 0x41, 0x41, 0x41, 0x41, 0x41, 0x41,
                0x41, 0x41, 0x41, 0x41, 0x41, 0x41, 0x41, 0x41,
                0x41, 0x41, 0x41, 0x41, 0x41, 0x00, 0x00, 0x21,
                0x00, 0x01
            )
            val packet = DatagramPacket(requestData, requestData.size, InetAddress.getByName(broadcastIp), NETBIOS_PORT)
            socket.send(packet)

            val recvBuf = ByteArray(1024)
            val deadline = System.currentTimeMillis() + 400
            while (System.currentTimeMillis() < deadline) {
                try {
                    val recvPacket = DatagramPacket(recvBuf, recvBuf.size)
                    socket.receive(recvPacket)
                    val ip = recvPacket.address?.hostAddress
                    if (ip != null && !ip.startsWith("127.") && !isVirtualOrP2pSubnet(ip)) {
                        foundIps.add(ip)
                    }
                } catch (_: Exception) {
                    break
                }
            }
        } catch (_: Throwable) {
        } finally {
            try { socket.close() } catch (_: Throwable) {}
        }

        return foundIps
    }

    private fun getSubnetIpsFor(ip: String): List<String> {
        return try {
            val prefix = ip.substringBeforeLast('.') + "."
            val myLastOctet = ip.substringAfterLast('.').toIntOrNull() ?: -1

            (1..254).filter { it != myLastOctet }.map { "$prefix$it" }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    /**
     * Obtains the primary active IPv4 address from Android's ConnectivityManager.
     * Guaranteed to return the real Wi-Fi or Ethernet IP, filtering out Android Wi-Fi Direct (192.168.49.x),
     * VPN, mobile data, and virtual network interfaces.
     */
    fun getActiveNetworkIpv4(): String? {
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val activeNet = cm?.activeNetwork
            if (activeNet != null) {
                val linkProps = cm.getLinkProperties(activeNet)
                val activeIp = linkProps?.linkAddresses
                    ?.map { it.address }
                    ?.filterIsInstance<Inet4Address>()
                    ?.firstOrNull { addr ->
                        !addr.isLoopbackAddress && !addr.isLinkLocalAddress && !isVirtualOrP2pSubnet(addr.hostAddress)
                    }?.hostAddress
                if (!activeIp.isNullOrBlank()) {
                    return activeIp
                }
            }
        } catch (e: Throwable) {
            Log.d(TAG, "Error getting active network link properties: ${e.message}")
        }

        // Fallback: enumerate interfaces, strictly filtering out virtual/p2p/vpn interfaces
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()?.toList() ?: emptyList()
            val validInterfaces = interfaces.filter { iface ->
                iface.isUp && !iface.isLoopback && !iface.isVirtual && !iface.isPointToPoint &&
                        !isVirtualInterfaceName(iface.name)
            }.sortedByDescending { iface ->
                when {
                    iface.name.startsWith("eth", ignoreCase = true) || iface.name.startsWith("en", ignoreCase = true) -> 10
                    iface.name.startsWith("wlan", ignoreCase = true) || iface.name.startsWith("wl", ignoreCase = true) -> 5
                    else -> 1
                }
            }

            for (iface in validInterfaces) {
                for (addr in iface.inetAddresses) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress && !addr.isLinkLocalAddress && addr.isSiteLocalAddress) {
                        val host = addr.hostAddress
                        if (!host.isNullOrBlank() && !isVirtualOrP2pSubnet(host)) {
                            return host
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            Log.d(TAG, "Error enumerating network interfaces: ${e.message}")
        }
        return null
    }

    private fun isVirtualInterfaceName(name: String): Boolean {
        val lower = name.lowercase()
        return lower.contains("p2p") ||
                lower.contains("dummy") ||
                lower.contains("tun") ||
                lower.contains("tap") ||
                lower.contains("rmnet") ||
                lower.contains("rndis") ||
                lower.contains("docker") ||
                lower.contains("vbox") ||
                lower.contains("ccmni") ||
                lower.contains("radio")
    }

    private fun isVirtualOrP2pSubnet(ip: String?): Boolean {
        if (ip == null) return true
        // Android Wi-Fi Direct is always hardcoded to 192.168.49.0/24
        if (ip.startsWith("192.168.49.")) return true
        // Android Emulator QEMU virtual NAT network (10.0.2.0/24) where 10.0.2.2 is host loopback
        if (ip.startsWith("10.0.2.")) return true
        // Carrier CGNAT
        if (ip.startsWith("100.64.")) return true
        // Localhost
        if (ip.startsWith("127.")) return true
        return false
    }
}
