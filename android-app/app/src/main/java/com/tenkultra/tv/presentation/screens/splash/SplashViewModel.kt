package com.tenkultra.tv.presentation.screens.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tenkultra.tv.data.api.DeviceFingerprint
import com.tenkultra.tv.data.api.SessionManager
import com.tenkultra.tv.data.datastore.SettingsDataStore
import com.tenkultra.tv.data.repository.AuthRepository
import com.tenkultra.tv.data.repository.PortalBlockedException
import com.tenkultra.tv.data.repository.ProvisioningRepository
import com.tenkultra.tv.util.RemoteConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SplashDestination { LOCKED, HOME, BLOCKED }

/**
 * DASHBOARD-MANAGED BUILD. The box never asks the customer for a portal URL — it registers itself
 * with the 10K Ultra backend and the reseller activates it (and assigns its portal) from the
 * dashboard. Boot decision:
 *
 *  1. Register + ask the backend for this device's config.
 *  2. `activated` with a server URL → adopt it (portal + dashboard-assigned MAC) and go Home.
 *  3. Anything else (pending / unpaid / revoked) → the ACTIVATION screen with the QR + code.
 *  4. Backend unreachable → OFFLINE GRACE: a box that was already activated keeps working from the
 *     saved portal, so an internet blip never locks out a paying customer. A box that has never
 *     been activated goes to the activation screen (it has nothing to play anyway).
 */
@HiltViewModel
class SplashViewModel @Inject constructor(
    private val settings: SettingsDataStore,
    private val session: SessionManager,
    private val authRepository: AuthRepository,
    private val provisioning: ProvisioningRepository
) : ViewModel() {

    private val _destination = MutableStateFlow<SplashDestination?>(null)
    val destination = _destination.asStateFlow()

    /** Provider's block/expiry message shown on the BLOCKED screen (null → use the default text). */
    var blockMessage: String? = null
        private set

    init {
        decide()
    }

    private fun decide() = viewModelScope.launch {
        // Ensure a MAC exists and is bound to the session for any portal call.
        val mac = settings.getOrCreateMac()
        session.mac = mac

        val savedPortal = settings.portalUrlFlow.first()

        // ---- 0. Hardcoded-portal TEST build (-PforcedPortal=…): skip licensing entirely. ----
        RemoteConfig.FORCED_PORTAL?.let { pinned ->
            if (!pinned.equals(savedPortal, ignoreCase = true)) {
                settings.clearCachedBase()
                session.endpointBase = ""
                session.token = ""
                settings.setPortalUrl(pinned)
            }
            connect(pinned)
            return@launch
        }

        // ---- 1. Announce this box to the dashboard, then read back its licence state. ----
        val deviceId = DeviceFingerprint.deviceId(mac)
        provisioning.register()                       // idempotent; null when offline
        val config = provisioning.pollConfig(deviceId) // null when offline

        when {
            // ---- 2. Activated + paid: the dashboard owns the portal, so always take its copy. ----
            config != null && config.status == "activated" && !config.serverUrl.isNullOrBlank() -> {
                // The dashboard may pin a specific (already whitelisted) MAC to this box.
                config.mac?.takeIf { it.isNotBlank() && !it.equals(mac, ignoreCase = true) }?.let {
                    settings.setMac(it)
                    session.mac = it
                }
                if (!config.serverUrl.equals(savedPortal, ignoreCase = true)) {
                    // A re-pointed box must forget the old endpoint or it reconnects to it.
                    settings.clearCachedBase()
                    session.endpointBase = ""
                    session.token = ""
                    settings.setPortalUrl(config.serverUrl)
                }
                connect(config.serverUrl)
            }

            // ---- 3. Backend answered and this box is NOT activated → activation screen. ----
            config != null -> _destination.value = SplashDestination.LOCKED

            // ---- 4. Backend unreachable → offline grace for an already-activated box. ----
            !savedPortal.isNullOrBlank() -> connect(savedPortal)

            else -> _destination.value = SplashDestination.LOCKED
        }
    }

    /**
     * Handshakes [portal]. A portal that REJECTS this box (MAC not whitelisted / blocked by the
     * provider) lands on the BLOCKED screen with the provider's own message; any other failure
     * still goes Home, which self-heals via `ensureSession` once the network returns.
     */
    private suspend fun connect(portal: String) {
        val result = authRepository.handshake(portal)
        val blocked = result.exceptionOrNull() as? PortalBlockedException
        if (blocked != null) {
            blockMessage = blocked.message?.trim()?.takeIf { it.isNotBlank() }
            session.lastBlockMessage = blockMessage.orEmpty()
            _destination.value = SplashDestination.BLOCKED
        } else {
            _destination.value = SplashDestination.HOME
        }
    }
}
