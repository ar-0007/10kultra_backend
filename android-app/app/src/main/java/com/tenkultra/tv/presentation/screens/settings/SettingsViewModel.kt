package com.tenkultra.tv.presentation.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tenkultra.tv.data.api.DeviceFingerprint
import com.tenkultra.tv.data.api.ProvisioningApi
import com.tenkultra.tv.data.api.SessionManager
import com.tenkultra.tv.data.datastore.SettingsDataStore
import com.tenkultra.tv.data.repository.AuthRepository
import com.tenkultra.tv.domain.model.AppLayout
import com.tenkultra.tv.domain.model.AppTheme
import com.tenkultra.tv.domain.model.VideoQuality
import com.tenkultra.tv.util.RemoteConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.security.SecureRandom
import javax.inject.Inject

data class SettingsUiState(
    val portalUrl: String = "",
    val mac: String = "",
    val deviceId: String = "",
    val theme: AppTheme = AppTheme.BRAND,
    val layout: AppLayout = AppLayout.CLASSIC,
    val videoQuality: VideoQuality = VideoQuality.AUTO,
    val reloadStatus: String? = null,
    val parentalPinSet: Boolean = false,
    val parentalLock: Boolean = false,
    // Subscription / account
    val accountLoading: Boolean = true,
    val accountName: String = "—",
    val accountPackage: String = "—",
    val accountExpiry: String = "—",
    val accountDaysLeft: Int? = null,
    val accountPhone: String = "—",
    val accountStatus: String = "—",
    val accountIsTrial: Boolean = false,
    // QR pairing inside the Change-portal dialog (same as the setup screen).
    val portalQrCode: String = "",
    val portalQrUrl: String = "",
    /** Set when the provider submits a URL in the browser → the screen applies it + restarts. */
    val qrReceivedUrl: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsDataStore,
    private val session: SessionManager,
    private val authRepository: AuthRepository,
    private val contentCache: com.tenkultra.tv.data.datastore.ContentCache,
    private val provisioningApi: ProvisioningApi,
    private val parental: com.tenkultra.tv.data.parental.ParentalControl
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState = _uiState.asStateFlow()

    private var qrPollJob: Job? = null

    init {
        viewModelScope.launch {
            val mac = settings.getOrCreateMac()
            val portal = settings.portalUrlFlow.first().orEmpty()
            val theme = settings.themeFlow.first()
            val layout = settings.layoutFlow.first()
            val quality = settings.videoQualityFlow.first()
            val pin = settings.parentalPinFlow.first()
            val lock = settings.parentalLockFlow.first()
            _uiState.update {
                it.copy(
                    mac = mac,
                    portalUrl = portal,
                    deviceId = DeviceFingerprint.deviceId(mac),
                    theme = theme,
                    layout = layout,
                    videoQuality = quality,
                    parentalPinSet = !pin.isNullOrBlank(),
                    parentalLock = lock
                )
            }
        }
        loadAccountInfo()
    }

    /** Loads subscription/account details from the portal (name, package, expiry, days left). */
    fun loadAccountInfo() {
        _uiState.update { it.copy(accountLoading = true) }
        viewModelScope.launch {
            val info = runCatching { authRepository.fetchAccountInfo() }.getOrNull()
            _uiState.update {
                if (info == null) it.copy(accountLoading = false)
                else it.copy(
                    accountLoading = false,
                    accountName = info.name,
                    accountPackage = info.packageName,
                    accountExpiry = info.expiry,
                    accountDaysLeft = info.daysLeft,
                    accountPhone = info.phone,
                    accountStatus = info.status,
                    accountIsTrial = info.isTrial
                )
            }
        }
    }

    /** Verifies a PIN against the stored one (suspending read; used before allowing changes). */
    suspend fun pinMatches(entered: String): Boolean {
        val stored = settings.parentalPinFlow.first()
        return !stored.isNullOrBlank() && stored == entered.trim()
    }

    fun setParentalPin(pin: String) {
        viewModelScope.launch {
            settings.setParentalPin(pin)
            _uiState.update { it.copy(parentalPinSet = pin.isNotBlank()) }
        }
    }

    fun removeParentalPin() {
        viewModelScope.launch {
            settings.setParentalPin(null)
            settings.setParentalLock(false)
            _uiState.update { it.copy(parentalPinSet = false, parentalLock = false) }
        }
    }

    fun toggleParentalLock() {
        viewModelScope.launch {
            val next = !_uiState.value.parentalLock
            settings.setParentalLock(next)
            // Turning the lock back ON re-arms it immediately, even if the PIN was already
            // entered earlier in this session (otherwise adult content would stay open).
            if (next) parental.relock()
            _uiState.update { it.copy(parentalLock = next) }
        }
    }

    fun cycleVideoQuality() {
        viewModelScope.launch {
            val current = _uiState.value.videoQuality
            val next = VideoQuality.entries[(current.ordinal + 1) % VideoQuality.entries.size]
            settings.setVideoQuality(next)
            _uiState.update { it.copy(videoQuality = next) }
        }
    }

    fun cycleTheme() {
        viewModelScope.launch {
            val current = _uiState.value.theme
            val next = AppTheme.entries[(current.ordinal + 1) % AppTheme.entries.size]
            settings.setTheme(next)
            _uiState.update { it.copy(theme = next) }
        }
    }

    fun cycleLayout() {
        viewModelScope.launch {
            val current = _uiState.value.layout
            val next = AppLayout.entries[(current.ordinal + 1) % AppLayout.entries.size]
            settings.setLayout(next)
            _uiState.update { it.copy(layout = next) }
        }
    }

    /** Saves a NEW user-entered portal URL, then invokes [onDone] (the screen restarts the app so
     *  Splash reconnects to the new portal cleanly). */
    fun changePortal(url: String, onDone: () -> Unit) {
        val clean = url.trim()
        if (clean.isBlank()) return
        viewModelScope.launch {
            settings.setPortalUrl(clean)
            // Fully cut ties with the OLD portal so the NEW URL takes effect cleanly:
            //  - clearCachedBase(): otherwise a still-alive old portal would be reconnected to.
            //  - contentCache.clear(): otherwise the old portal's genres/categories flash on Home.
            //  - reset the live session so nothing points at the old endpoint/token.
            settings.clearCachedBase()
            contentCache.clear()
            session.endpointBase = ""
            session.token = ""
            _uiState.update { it.copy(portalUrl = clean) }
            onDone()
        }
    }

    /** Opens QR pairing in the Change-portal dialog: fresh code + QR, then polls for a browser URL. */
    fun startPortalQr() {
        qrPollJob?.cancel()
        viewModelScope.launch {
            val mac = settings.getOrCreateMac()
            val code = genPairingCode()
            val qr = RemoteConfig.qrImageUrl(RemoteConfig.setupUrl(code, mac))
            _uiState.update { it.copy(portalQrCode = code, portalQrUrl = qr, qrReceivedUrl = null) }
            pollPortalQr(code)
        }
    }

    /** "Refresh QR" button — new code + QR. */
    fun refreshPortalQr() = startPortalQr()

    /** Stop polling when the dialog closes. */
    fun stopPortalQr() { qrPollJob?.cancel() }

    /** Clears the received-URL signal after the screen has applied it. */
    fun consumeQrUrl() { _uiState.update { it.copy(qrReceivedUrl = null) } }

    private fun pollPortalQr(code: String) {
        qrPollJob = viewModelScope.launch {
            while (isActive) {
                delay(3000)
                val url = runCatching { provisioningApi.getSetupPairing(code).serverUrl }.getOrNull()
                if (!url.isNullOrBlank()) {
                    _uiState.update { it.copy(qrReceivedUrl = url) }
                    return@launch
                }
            }
        }
    }

    fun reloadPortal() {
        _uiState.update { it.copy(reloadStatus = "Reconnecting…") }
        viewModelScope.launch {
            val portal = settings.portalUrlFlow.first().orEmpty()
            val result = authRepository.handshake(portal)
            _uiState.update {
                it.copy(
                    reloadStatus = if (result.isSuccess) "Portal reloaded ✓"
                    else "Reload failed: ${result.exceptionOrNull()?.message}"
                )
            }
        }
    }

    override fun onCleared() { qrPollJob?.cancel() }

    private companion object {
        private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        private val RNG = SecureRandom()
        fun genPairingCode(len: Int = 6): String =
            (1..len).map { ALPHABET[RNG.nextInt(ALPHABET.length)] }.joinToString("")
    }
}
