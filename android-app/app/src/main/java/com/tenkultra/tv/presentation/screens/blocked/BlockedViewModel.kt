package com.tenkultra.tv.presentation.screens.blocked

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tenkultra.tv.data.api.DeviceFingerprint
import com.tenkultra.tv.data.datastore.SettingsDataStore
import com.tenkultra.tv.data.repository.AuthRepository
import com.tenkultra.tv.data.repository.PortalBlockedException
import com.tenkultra.tv.data.repository.ProvisioningRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives the "Your STB is blocked" screen. "Reload portal" re-runs the handshake: if the provider
 * has since un-blocked / renewed the box the portal accepts it and we go Home; otherwise we stay
 * blocked (refreshing the message).
 */
@HiltViewModel
class BlockedViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val settings: SettingsDataStore,
    private val provisioning: ProvisioningRepository,
) : ViewModel() {

    data class UiState(
        val reloading: Boolean = false,
        val connected: Boolean = false,
        val message: String? = null,
    )

    private val _ui = MutableStateFlow(UiState())
    val ui = _ui.asStateFlow()

    /** Seed the provider's block message once (from the nav arg), if any. */
    fun setInitialMessage(message: String?) {
        if (_ui.value.message == null && !message.isNullOrBlank()) {
            _ui.update { it.copy(message = message) }
        }
    }

    fun reload() {
        if (_ui.value.reloading) return
        viewModelScope.launch {
            _ui.update { it.copy(reloading = true) }
            // Managed build: the dashboard may have re-pointed this box while it sat blocked, so
            // take its assigned URL first and only fall back to the one we already had.
            val mac = settings.getOrCreateMac()
            val assigned = provisioning.pollConfig(DeviceFingerprint.deviceId(mac))
                ?.takeIf { it.status == "activated" }?.serverUrl?.takeIf { it.isNotBlank() }
            assigned?.let {
                settings.clearCachedBase()
                settings.setPortalUrl(it)
            }
            val portal = assigned ?: settings.portalUrlFlow.first()?.takeIf { it.isNotBlank() }
            if (portal == null) {
                // Never activated → nothing to reload; tell the user rather than silently spinning.
                _ui.update {
                    it.copy(
                        reloading = false,
                        message = "This device isn't activated yet. Please contact your provider."
                    )
                }
                return@launch
            }
            val result = authRepository.handshake(portal)
            _ui.update {
                when {
                    result.isSuccess -> it.copy(reloading = false, connected = true)
                    result.exceptionOrNull() is PortalBlockedException ->
                        it.copy(reloading = false, message = result.exceptionOrNull()?.message)
                    // Plain network hiccup — keep the box on the blocked screen with its message.
                    else -> it.copy(reloading = false)
                }
            }
        }
    }
}
