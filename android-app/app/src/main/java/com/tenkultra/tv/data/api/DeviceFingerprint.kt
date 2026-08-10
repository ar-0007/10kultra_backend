package com.tenkultra.tv.data.api

import java.security.MessageDigest

/**
 * Derives the MAG/STB device identifiers a Stalker portal expects, from the MAC.
 * This mirrors STBEMU's default scheme — providers bind a subscription to the MAC
 * *plus* these values, so they must match or the portal reports "device conflict".
 *
 *   device_id = device_id2 = uppercase SHA-256 of the MAC string (with colons)
 *   sn        = first 13 chars of uppercase MD5 of the MAC
 */
object DeviceFingerprint {

    const val STB_TYPE = "MAG250"

    fun deviceId(mac: String): String = sha256(mac).uppercase()

    fun serialNumber(mac: String): String = md5(mac).uppercase().take(13)

    private fun sha256(input: String): String = hex(MessageDigest.getInstance("SHA-256"), input)

    private fun md5(input: String): String = hex(MessageDigest.getInstance("MD5"), input)

    private fun hex(digest: MessageDigest, input: String): String =
        digest.digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
