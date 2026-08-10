package com.tenkultra.tv.presentation.common

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import com.tenkultra.tv.data.datastore.SettingsDataStore
import com.tenkultra.tv.data.repository.AuthRepository
import com.tenkultra.tv.presentation.theme.appPalette
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Checks (once) with the connected portal whether the subscription has lapsed. A failed/offline
 * check is ignored (never cries expiry on a plain outage). Exposes [expired] so [SubscriptionGuard]
 * can pop the "contact your provider" dialog. Universal — works for ANY portal the user entered.
 */
@HiltViewModel
class SubscriptionGuardViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val settings: SettingsDataStore
) : ViewModel() {

    private val _expired = MutableStateFlow(false)
    val expired = _expired.asStateFlow()

    init {
        viewModelScope.launch {
            val portal = settings.portalUrlFlow.first()
            if (portal.isNullOrBlank()) return@launch
            if (runCatching { authRepository.subscriptionExpired() }.getOrDefault(false)) {
                _expired.value = true
            }
        }
    }
}

/**
 * Drop on Home. If the subscription is expired, instantly shows a full-screen dialog telling the
 * user to contact their provider. It is DISMISSIBLE and offers a "Change portal URL" button — so an
 * expired customer is never trapped: they can dismiss and still open Settings, or jump straight to
 * Settings from the dialog to enter a different portal URL.
 */
@Composable
fun SubscriptionGuard(
    onOpenSettings: () -> Unit,
    viewModel: SubscriptionGuardViewModel = hiltViewModel()
) {
    val expired by viewModel.expired.collectAsStateWithLifecycle()
    var dismissed by remember { mutableStateOf(false) }
    if (expired && !dismissed) {
        SubscriptionExpiredDialog(
            onOpenSettings = { dismissed = true; onOpenSettings() },
            onClose = { dismissed = true }
        )
    }
}

/**
 * The "subscription expired — contact your provider" dialog. [onOpenSettings], when non-null, shows
 * a button that takes the user to Settings so they can change the portal URL (renew / switch
 * provider). Always dismissible so it never blocks access to Settings.
 */
@Composable
fun SubscriptionExpiredDialog(onClose: () -> Unit, onOpenSettings: (() -> Unit)? = null) {
    val palette = appPalette
    val okFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { okFocus.requestFocus() } }
    BackHandler { onClose() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC000000)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(palette.surface)
                .border(1.dp, Color(0xFFD9776A).copy(alpha = 0.6f), RoundedCornerShape(18.dp))
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Subscription Expired", color = Color(0xFFD9776A), fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(14.dp))
            Text(
                "Your subscription has expired.\nPlease contact your provider to renew — or enter a " +
                    "different portal URL from Settings to continue watching.",
                color = palette.text, fontSize = 16.sp, textAlign = TextAlign.Center, lineHeight = 24.sp
            )
            Spacer(Modifier.height(26.dp))
            Row {
                if (onOpenSettings != null) {
                    Button(
                        onClick = onOpenSettings,
                        modifier = Modifier.height(50.dp).focusRequester(okFocus)
                    ) {
                        Text("Change portal URL", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.width(14.dp))
                    Button(onClick = onClose, modifier = Modifier.height(50.dp)) {
                        Text("Dismiss", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    Button(
                        onClick = onClose,
                        modifier = Modifier.height(50.dp).focusRequester(okFocus)
                    ) {
                        Text("OK", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
