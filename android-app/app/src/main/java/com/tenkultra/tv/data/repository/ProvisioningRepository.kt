package com.tenkultra.tv.data.repository

import com.tenkultra.tv.BuildConfig
import com.tenkultra.tv.data.api.DeviceFingerprint
import com.tenkultra.tv.data.api.ProvisioningApi
import com.tenkultra.tv.data.api.models.RegisterRequest
import com.tenkultra.tv.data.datastore.SettingsDataStore
import com.tenkultra.tv.util.RemoteConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Talks to the 10K Ultra dashboard for QR activation: registers this box (getting a
 * single-use pairing code for the QR) and polls for the reseller-assigned portal.
 */
@Singleton
class ProvisioningRepository @Inject constructor(
    private val api: ProvisioningApi,
    private val settings: SettingsDataStore
) {
    data class Registration(val deviceId: String, val pairingCode: String, val status: String)
    data class Config(val status: String, val serverUrl: String?, val mac: String?, val paymentStatus: String? = null)

    /** Registers this device (sending its MAC); returns its id + pairing code (null if unreachable). */
    suspend fun register(): Registration? = withContext(Dispatchers.IO) {
        val mac = settings.getOrCreateMac()
        val deviceId = DeviceFingerprint.deviceId(mac)
        // Report the portal this box is actually using. For a reseller's own (self-managed) URL this
        // makes the dashboard SHOW it (no activate/revoke role). Blank until a portal is chosen.
        val portal = runCatching { settings.portalUrlFlow.first() }.getOrNull()
        runCatching {
            val r = api.register(
                RegisterRequest(
                    deviceId = deviceId,
                    mac = mac,                       // backend stores this so the dashboard never types it
                    model = DeviceFingerprint.STB_TYPE,
                    sigSha256 = null,
                    appVersion = BuildConfig.VERSION_CODE,
                    serverUrl = portal?.takeIf { it.isNotBlank() && !RemoteConfig.isManagedPortal(it) }
                )
            )
            Registration(deviceId, r.pairingCode.orEmpty(), r.status ?: "pending")
        }.getOrNull()
    }

    /** One status check — pending until activated+paid, then returns "activated" with url + mac. */
    suspend fun pollConfig(deviceId: String): Config? = withContext(Dispatchers.IO) {
        runCatching {
            val c = api.config(deviceId)
            Config(c.status ?: "pending", c.serverUrl, c.mac, c.paymentStatus)
        }.getOrNull()
    }
}
