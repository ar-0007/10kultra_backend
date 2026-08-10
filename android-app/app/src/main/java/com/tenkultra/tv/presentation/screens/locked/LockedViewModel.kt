package com.tenkultra.tv.presentation.screens.locked

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tenkultra.tv.data.api.SessionManager
import com.tenkultra.tv.data.datastore.ContentCache
import com.tenkultra.tv.data.datastore.SettingsDataStore
import com.tenkultra.tv.data.repository.AuthRepository
import com.tenkultra.tv.data.repository.PortalBlockedException
import com.tenkultra.tv.data.repository.ProvisioningRepository
import com.tenkultra.tv.util.RemoteConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the dashboard currently says about this box — drives the status line on the screen. */
enum class ActivationState { CONNECTING, WAITING, PAYMENT_REQUIRED, REVOKED, OFFLINE }

data class SetupUiState(
    val loading: Boolean = true,
    val mac: String = "",
    val pairingCode: String = "",
    val activation: ActivationState = ActivationState.CONNECTING,
    /** Which of [RemoteConfig.PORTALS] the user has highlighted on the setup screen. */
    val selectedPortal: Int = 0,
    /** Free-text portal URL (phones can type one; TVs use the picker / dashboard). */
    val portalInput: String = "",
    val connecting: Boolean = false,
    val connectError: String? = null,
    val connected: Boolean = false,
    // The portal was REACHED but rejected this box (MAC not whitelisted / blocked) → show a
    // "contact your provider" dialog. Non-null = dialog visible; the string is the portal's own
    // message (may be blank → use the default text).
    val blockedMessage: String? = null
)

/**
 * ACTIVATION screen VM (dashboard-managed build). The box shows a pairing code + QR; the reseller
 * opens it in the 10K Ultra dashboard, activates the device and assigns its portal URL. This VM
 * polls the backend until that happens, then adopts the assigned portal and goes Home.
 *
 * The customer never types a portal URL here — that is the whole point of the managed build.
 */
@HiltViewModel
class LockedViewModel @Inject constructor(
    private val settings: SettingsDataStore,
    private val auth: AuthRepository,
    private val session: SessionManager,
    private val contentCache: ContentCache,
    private val provisioning: ProvisioningRepository
) : ViewModel() {

    private val _ui = MutableStateFlow(SetupUiState())
    val ui = _ui.asStateFlow()

    private var pollJob: Job? = null

    init { start() }

    /** Registers this box with the dashboard, shows its pairing code, and waits for activation. */
    fun start() {
        viewModelScope.launch {
            val mac = settings.getOrCreateMac()
            session.mac = mac
            // If we were sent here by a blocked boot, surface the provider dialog once.
            val blocked = session.lastBlockMessage
            session.lastBlockMessage = null
            _ui.update {
                it.copy(
                    loading = false, mac = mac, connectError = null,
                    blockedMessage = blocked?.takeIf { m -> m.isNotBlank() },
                    activation = ActivationState.CONNECTING
                )
            }
            startPolling()
        }
    }

    /** Asks the backend for a FRESH pairing code and restarts polling (the "New code" button). */
    fun refreshCode() {
        _ui.update { it.copy(pairingCode = "", activation = ActivationState.CONNECTING) }
        startPolling()
    }

    /**
     * One loop that registers this box (retrying while the backend is unreachable) and then polls
     * its licence state until the reseller activates it in the dashboard.
     */
    private fun startPolling() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            var deviceId: String? = null
            while (isActive) {
                if (deviceId == null) {
                    val registration = provisioning.register()
                    if (registration == null) {
                        _ui.update { it.copy(activation = ActivationState.OFFLINE) }
                        delay(RETRY_MS)
                        continue
                    }
                    deviceId = registration.deviceId
                    _ui.update {
                        it.copy(
                            pairingCode = registration.pairingCode,
                            activation = ActivationState.WAITING
                        )
                    }
                }
                delay(POLL_MS)
                // Network blip → keep the current state and try again, don't scare the user.
                val config = provisioning.pollConfig(deviceId) ?: continue
                when {
                    config.status == "activated" && !config.serverUrl.isNullOrBlank() -> {
                        config.mac?.takeIf { it.isNotBlank() }?.let {
                            settings.setMac(it)
                            session.mac = it
                        }
                        connect(config.serverUrl)
                        return@launch
                    }
                    config.status == "revoked" ->
                        _ui.update { it.copy(activation = ActivationState.REVOKED) }
                    config.status == "payment_required" || config.paymentStatus == "unpaid" ||
                        config.paymentStatus == "expired" ->
                        _ui.update { it.copy(activation = ActivationState.PAYMENT_REQUIRED) }
                    else -> _ui.update { it.copy(activation = ActivationState.WAITING) }
                }
            }
        }
    }

    /**
     * A QR was scanned in-app (mobile companion). Accepts the TV's
     * `tenkultra://connect?u=<portal>&m=<mac>` link so a phone can mirror an ALREADY-ACTIVATED box.
     */
    fun onScanned(raw: String) {
        val t = raw.trim()
        if (!t.startsWith("tenkultra://", true)) {
            _ui.update { it.copy(connectError = "That QR isn't a 10K Ultra connect code.") }
            return
        }
        val uri = android.net.Uri.parse(t)
        val portal = uri.getQueryParameter("u")?.trim().orEmpty()
        val mac = uri.getQueryParameter("m")?.trim().orEmpty()
        if (portal.isBlank()) {
            _ui.update { it.copy(connectError = "That QR has no portal URL.") }
            return
        }
        viewModelScope.launch {
            if (mac.isNotBlank()) {
                // Use the TV's WHITELISTED MAC for this handshake — the phone's own MAC isn't
                // whitelisted, so without this the portal rejects it. That's the point of scanning.
                settings.setMac(mac)
                session.mac = mac
                _ui.update { it.copy(mac = mac) }
            }
            connect(portal)
        }
    }

    /** Moves the highlight through the built-in server list (D-pad up/down on the setup screen). */
    fun selectPortal(index: Int) {
        val clamped = index.coerceIn(0, RemoteConfig.PORTALS.lastIndex)
        _ui.update { it.copy(selectedPortal = clamped, connectError = null) }
    }

    /**
     * Connects to one of the built-in servers. The dashboard still owns activation — this only
     * saves which server this box talks to, so a reseller can put a box on air immediately and the
     * dashboard's assigned URL still wins at the next boot.
     */
    fun connectSelectedPortal() {
        val portal = RemoteConfig.PORTALS.getOrNull(_ui.value.selectedPortal) ?: return
        viewModelScope.launch { connect(portal.url) }
    }

    fun onPortalInput(value: String) {
        _ui.update { it.copy(portalInput = value, connectError = null) }
    }

    /** Connects to a hand-typed portal URL (the phone's "enter URL" path). */
    fun connectTypedPortal() {
        val url = _ui.value.portalInput.trim()
        if (url.isBlank()) {
            _ui.update { it.copy(connectError = "Enter a portal URL first.") }
            return
        }
        val normalized = if (url.startsWith("http", true)) url else "http://$url"
        viewModelScope.launch { connect(normalized) }
    }

    /** Saves [portal] as this box's portal, handshakes it, and navigates Home on success. */
    private suspend fun connect(portal: String) {
        _ui.update { it.copy(connecting = true, connectError = null) }
        // Make the assigned URL authoritative: drop any old cached endpoint / lists / live session.
        settings.clearCachedBase()
        contentCache.clear()
        session.endpointBase = ""
        session.token = ""
        auth.handshake(portal).fold(
            onSuccess = {
                settings.setPortalUrl(portal)
                pollJob?.cancel()
                _ui.update { it.copy(connecting = false, connected = true) }
            },
            onFailure = { e ->
                if (e is PortalBlockedException) {
                    _ui.update {
                        it.copy(connecting = false, blockedMessage = e.message?.trim().orEmpty())
                    }
                } else {
                    _ui.update {
                        it.copy(
                            connecting = false,
                            connectError = e.message?.takeIf { m -> m.isNotBlank() }
                                ?: "Couldn't reach the assigned portal. It will retry automatically."
                        )
                    }
                }
            }
        )
    }

    /** Dismiss the "contact your provider" dialog (the box stays on the activation screen). */
    fun dismissBlocked() {
        _ui.update { it.copy(blockedMessage = null) }
    }

    override fun onCleared() { pollJob?.cancel() }

    private companion object {
        const val POLL_MS = 4_000L
        const val RETRY_MS = 10_000L
    }
}
