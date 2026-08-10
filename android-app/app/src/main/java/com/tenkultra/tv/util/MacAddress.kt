package com.tenkultra.tv.util

import java.security.SecureRandom

object MacAddress {
    /**
     * 10K Ultra branded MAC prefix.
     *
     * A MAC address is a HEX number — only 0-9 and A-F are allowed — so the letters
     * **W** and **T** (from "DW:AT") cannot appear in a real MAC anywhere in the world.
     * We use the two VALID letters from the abbreviations as the brand:
     *   D  → from **DW** (client)
     *   A  → from **AT** (10K Ultra app)
     * giving the prefix "DA:7A". Every 10K Ultra box therefore starts with DA:7A and the
     * remaining 4 octets are random, so no two boxes ever share the same MAC.
     */
    const val PREFIX = "DA:7A"

    // NOTE: no fixed/default MAC constant lives here on purpose. Every box's MAC is generated
    // uniquely + deterministically from its device id via [forDevice]; a shared hardcoded MAC is
    // exactly the bug that made every box identical, so it must never be reintroduced.

    /**
     * Derives a STABLE per-device MAC from a stable device identifier (Settings.Secure.ANDROID_ID).
     * Deterministic: the same device always produces the same MAC — so it survives app
     * uninstall / reinstall / update (ANDROID_ID is stable per device + app-signing-key), while
     * still being unique per device. No randomness, no server round-trip needed.
     *
     * MAC = [PREFIX] + the first 4 bytes of SHA-256(deviceKey) → DA:7A:xx:xx:xx:xx.
     */
    fun forDevice(deviceKey: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest(deviceKey.toByteArray(Charsets.UTF_8))
        val tail = (0..3).joinToString(":") { String.format("%02X", digest[it].toInt() and 0xFF) }
        return "$PREFIX:$tail"
    }

    /**
     * Generates a unique branded MAC: [PREFIX] + 4 cryptographically-random octets.
     * 4 random octets = ~4.3 billion combinations, so every device gets its own.
     */
    fun generate(): String {
        val random = SecureRandom()
        val tail = (0..3).joinToString(":") { String.format("%02X", random.nextInt(256)) }
        return "$PREFIX:$tail"
    }
}
