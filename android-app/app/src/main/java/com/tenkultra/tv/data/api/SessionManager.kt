package com.tenkultra.tv.data.api

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds the live Stalker session: the device MAC (loaded from DataStore) and the
 * in-memory auth token obtained from handshake. Read by [StalkerAuthInterceptor]
 * to stamp every outgoing request.
 */
@Singleton
class SessionManager @Inject constructor() {
    // The device MAC, loaded once from DataStore (unique + stable per box) and stamped on every
    // portal request by StalkerAuthInterceptor. Seeded by Splash / ContentRepository.ensureSession.
    @Volatile
    var mac: String = ""

    @Volatile
    var token: String = ""

    /** The portal endpoint base (`…/load.php` or `…/portal.php`) discovered at handshake. */
    @Volatile
    var endpointBase: String = ""

    /**
     * Set when a saved-portal boot handshake finds the box BLOCKED (MAC not whitelisted). The setup
     * screen reads it once to show the "Portal Not Authorized — contact provider" dialog, then clears
     * it. Universal build: a blocked box is NOT closed — the user can just enter a different URL.
     */
    @Volatile
    var lastBlockMessage: String? = null

    fun isAuthenticated(): Boolean = token.isNotBlank()
}
