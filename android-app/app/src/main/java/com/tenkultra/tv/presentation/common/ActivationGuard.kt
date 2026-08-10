package com.tenkultra.tv.presentation.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.tenkultra.tv.data.api.DeviceFingerprint
import com.tenkultra.tv.data.datastore.SettingsDataStore
import com.tenkultra.tv.data.repository.ProvisioningRepository
import com.tenkultra.tv.util.RemoteConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Periodically re-checks with the backend that this box is still activated AND paid. If the
 * reseller revokes it or marks it unpaid/expired, the next poll flips [locked] true so Home can
 * send the box back to the LOCKED screen. A failed poll (network blip) is IGNORED — offline grace,
 * so a paying customer is never kicked out just because the internet dropped for a moment.
 */
@HiltViewModel
class ActivationGuardViewModel @Inject constructor(
    private val provisioning: ProvisioningRepository,
    private val settings: SettingsDataStore
) : ViewModel() {

    private val _locked = MutableStateFlow(false)
    val locked = _locked.asStateFlow()

    init {
        viewModelScope.launch {
            // A reseller's OWN portal (self-managed) is NOT under our activation/kill-switch — only the
            // default managed portal (star.homeip.net) is gated. So don't watch/lock a self-managed box.
            val portal = settings.portalUrlFlow.first()
            if (!RemoteConfig.isManagedPortal(portal)) return@launch

            val deviceId = DeviceFingerprint.deviceId(settings.getOrCreateMac())
            while (isActive) {
                delay(CHECK_INTERVAL_MS)
                val c = provisioning.pollConfig(deviceId) ?: continue // unreachable → keep watching
                if (c.status != "activated") {
                    _locked.value = true
                    return@launch
                }
            }
        }
    }

    private companion object {
        const val CHECK_INTERVAL_MS = 30L * 60L * 1000L // 30 minutes
    }
}

/**
 * Drop this anywhere on the Home screen. It silently re-validates activation in the background
 * and calls [onLocked] if the device is no longer activated/paid (remote kill switch).
 */
@Composable
fun ActivationGuard(
    onLocked: () -> Unit,
    viewModel: ActivationGuardViewModel = hiltViewModel()
) {
    val locked by viewModel.locked.collectAsStateWithLifecycle()
    LaunchedEffect(locked) { if (locked) onLocked() }
}
