package com.tenkultra.tv.data.api

import okhttp3.Dns
import java.net.InetAddress

/**
 * Tries the device/system DNS first (fast, normal case). If it fails or returns
 * nothing — common on TV boxes that can't resolve dynamic-DNS hostnames like
 * star.homeip.net — it falls back to DNS-over-HTTPS (Google/Cloudflare), which
 * resolves reliably regardless of the box's broken resolver.
 */
class ResilientDns(
    private val system: Dns,
    private val doh: Dns
) : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        val viaSystem = runCatching { system.lookup(hostname) }.getOrNull()
        if (!viaSystem.isNullOrEmpty()) return viaSystem
        return doh.lookup(hostname)
    }
}
