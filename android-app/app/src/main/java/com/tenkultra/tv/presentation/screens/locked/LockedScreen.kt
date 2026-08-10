package com.tenkultra.tv.presentation.screens.locked

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.tenkultra.tv.BuildConfig
import com.tenkultra.tv.presentation.common.BrandWordmark
import com.tenkultra.tv.presentation.common.QrCode
import com.tenkultra.tv.presentation.theme.appPalette
import com.tenkultra.tv.util.RemoteConfig

/**
 * ACTIVATION screen (dashboard-managed build). The box shows its pairing code + a QR that opens the
 * 10K Ultra dashboard; the reseller activates it there and assigns the portal. The box polls in the
 * background and jumps to Home the moment that happens — nothing to type on the TV.
 */
@Composable
fun LockedScreen(
    onConnected: () -> Unit,
    viewModel: LockedViewModel = hiltViewModel()
) {
    val palette = appPalette
    val state by viewModel.ui.collectAsStateWithLifecycle()

    // In-app QR scanner (mobile): scan the TV's "Connect Android app" QR → mirror an activated box.
    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let { viewModel.onScanned(it) }
    }
    fun launchScan() {
        scanLauncher.launch(
            ScanOptions().apply {
                setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                setPrompt("Scan the QR shown on your TV")
                setBeepEnabled(false)
                setOrientationLocked(false)
            }
        )
    }

    LaunchedEffect(state.connected) { if (state.connected) onConnected() }
    // Activation has no previous screen — swallow Back so it can't accidentally exit the app.
    BackHandler { }

    // TV (D-pad) vs phone/tablet (touch). The phone setup screen offers three ways in (scan, pick a
    // server, type a URL), so the scanner must NOT auto-open — that hid the other two behind a
    // camera-permission prompt.
    val isTv = rememberIsTv()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(palette.surface, palette.background, Color.Black)
                )
            )
            // TV needs a big safe-area inset; a phone needs a normal screen margin.
            .padding(
                horizontal = if (isTv) 56.dp else 20.dp,
                vertical = if (isTv) 40.dp else 16.dp
            )
    ) {
        if (!isTv) MobileSetupContent(
            state = state,
            onScan = { launchScan() },
            onPortalInput = viewModel::onPortalInput,
            onConnectTyped = viewModel::connectTypedPortal,
            onSelectPortal = viewModel::selectPortal,
            onConnectPortal = viewModel::connectSelectedPortal
        )
        else TvActivationContent(
            state = state,
            onRefreshCode = viewModel::refreshCode,
            onSelectPortal = viewModel::selectPortal,
            onConnectPortal = viewModel::connectSelectedPortal
        )

        // Portal reached but this box isn't authorized (MAC not whitelisted) → provider dialog.
        state.blockedMessage?.let {
            BlockedByPortalDialog(message = it, mac = state.mac, onClose = viewModel::dismissBlocked)
        }
    }
}

@Composable
private fun TvActivationContent(
    state: SetupUiState,
    onRefreshCode: () -> Unit,
    onSelectPortal: (Int) -> Unit,
    onConnectPortal: () -> Unit
) {
    val palette = appPalette
    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        // ---- Left: brand + what the reseller has to do + this box's identity ----
        Column(Modifier.weight(1f).padding(end = 48.dp)) {
            BrandWordmark(modifier = Modifier.width(190.dp), glow = false)
            Spacer(Modifier.height(14.dp))
            Text(
                "Activate this device",
                color = palette.text, fontSize = 26.sp, fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Scan the code with your phone, or open the 10K Ultra dashboard and enter the " +
                    "activation code shown — this box connects by itself as soon as it's activated. " +
                    "You can also pick a server below to start watching right away.",
                color = palette.textSecondary, fontSize = 13.sp, lineHeight = 19.sp
            )
            Spacer(Modifier.height(14.dp))
            IdentityRow("Activation code", state.pairingCode.ifBlank { "…" }, highlight = true)
            Spacer(Modifier.height(8.dp))
            IdentityRow("Device MAC", state.mac.ifBlank { "…" })
            Spacer(Modifier.height(14.dp))
            PortalPicker(
                selected = state.selectedPortal,
                connecting = state.connecting,
                onSelect = onSelectPortal,
                onConnect = onConnectPortal
            )
            Spacer(Modifier.height(12.dp))
            StatusLine(state = state)
            state.connectError?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, color = Color(0xFFD9776A), fontSize = 14.sp)
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onRefreshCode, modifier = Modifier.height(40.dp)) {
                    Text("NEW CODE", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.width(16.dp))
                Text(
                    "Version ${BuildConfig.VERSION_NAME}",
                    color = palette.textSecondary, fontSize = 12.sp
                )
            }
        }

        // ---- Right: the QR the reseller scans ----
        Column(
            modifier = Modifier.width(330.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .width(300.dp)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color.White)
                    .border(2.dp, palette.primary.copy(alpha = 0.55f), RoundedCornerShape(22.dp))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                // Generated ON-DEVICE (no backend image) → never blank, works on a slow link.
                if (state.pairingCode.isNotBlank()) {
                    QrCode(
                        data = RemoteConfig.activateUrl(state.pairingCode),
                        sizeDp = 268.dp,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                "Opens the 10K Ultra activation page",
                color = palette.textSecondary, fontSize = 12.sp, textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * The built-in server list ([RemoteConfig.PORTALS]). One tap / OK connects the box to that server
 * straight away, so a reseller can put a box on air without waiting for the dashboard hand-off.
 * The dashboard's assigned URL still wins at the next boot.
 */
@Composable
private fun PortalPicker(
    selected: Int,
    connecting: Boolean,
    onSelect: (Int) -> Unit,
    onConnect: () -> Unit
) {
    val palette = appPalette
    // Something must hold focus when the screen opens, or the D-pad has nowhere to start.
    val connectFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { connectFocus.requestFocus() } }
    Column {
        Text("Choose your server", color = palette.textSecondary, fontSize = 13.sp)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RemoteConfig.PORTALS.forEachIndexed { i, portal ->
                val on = i == selected
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (on) palette.primary else palette.surface)
                        .border(
                            1.dp,
                            if (on) palette.primary else palette.primary.copy(alpha = 0.28f),
                            RoundedCornerShape(12.dp)
                        )
                        // A tap picks the server; OK/tap on the already-picked one connects.
                        .clickable { if (on) onConnect() else onSelect(i) }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        portal.label,
                        color = if (on) palette.background else palette.text,
                        fontSize = 15.sp, fontWeight = FontWeight.Bold
                    )
                    Text(
                        portal.url.removePrefix("http://").removePrefix("https://"),
                        color = if (on) palette.background.copy(alpha = 0.75f) else palette.textSecondary,
                        fontSize = 11.sp
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onConnect,
            enabled = !connecting,
            modifier = Modifier.height(42.dp).focusRequester(connectFocus)
        ) {
            Text(
                if (connecting) "CONNECTING…" else "CONNECT TO THIS SERVER",
                fontSize = 13.sp, fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/** One "label — value" line for the pairing code / MAC, so both read clearly from the sofa. */
@Composable
private fun IdentityRow(label: String, value: String, highlight: Boolean = false) {
    val palette = appPalette
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = palette.textSecondary, fontSize = 13.sp, modifier = Modifier.width(150.dp))
        Text(
            value,
            color = if (highlight) palette.primary else palette.text,
            fontSize = if (highlight) 27.sp else 16.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = if (highlight) 6.sp else 0.sp
        )
    }
}

/** Live status of the dashboard hand-off, with a pulsing dot so it's obviously still working. */
@Composable
private fun StatusLine(state: SetupUiState) {
    val palette = appPalette
    val (dot, message) = when {
        state.connecting -> palette.primary to "Connecting to your portal…"
        state.activation == ActivationState.PAYMENT_REQUIRED ->
            Color(0xFFE0A458) to "Payment required — contact your provider to renew."
        state.activation == ActivationState.REVOKED ->
            Color(0xFFD9776A) to "This device was revoked. Contact your provider."
        state.activation == ActivationState.OFFLINE ->
            Color(0xFFD9776A) to "No internet — check the network, this will retry by itself."
        state.activation == ActivationState.CONNECTING ->
            palette.textSecondary to "Contacting the activation server…"
        else -> palette.liveDot to "Waiting for activation…"
    }
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "status")
    val pulse by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "statusPulse"
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).alpha(pulse).clip(CircleShape).background(dot))
        Spacer(Modifier.width(10.dp))
        Text(message, color = palette.text, fontSize = 15.sp)
    }
}

/**
 * Shown when the portal was reached but REJECTED this box (MAC not whitelisted / blocked). Tells the
 * user to contact their provider to whitelist this MAC, then dismiss and wait for the retry.
 */
@Composable
private fun BlockedByPortalDialog(message: String, mac: String, onClose: () -> Unit) {
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
                .widthIn(max = 540.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(palette.surface)
                .border(1.dp, Color(0xFFD9776A).copy(alpha = 0.6f), RoundedCornerShape(18.dp))
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Portal Not Authorized", color = Color(0xFFD9776A), fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            if (message.isNotBlank()) {
                Text(message, color = palette.text, fontSize = 15.sp, textAlign = TextAlign.Center, lineHeight = 21.sp)
                Spacer(Modifier.height(10.dp))
            }
            Text(
                "Please contact your provider to whitelist this box, then it will connect automatically.",
                color = palette.text, fontSize = 16.sp, textAlign = TextAlign.Center, lineHeight = 23.sp
            )
            Spacer(Modifier.height(14.dp))
            Text("MAC:  $mac", color = palette.primary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(24.dp))
            Button(onClick = onClose, modifier = Modifier.height(50.dp).focusRequester(okFocus)) {
                Text("OK", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/**
 * MOBILE setup. A phone has THREE ways in, in the order most people will use them:
 *  1. Scan the TV’s QR — this OVERRIDES everything, importing the box’s portal AND its
 *     whitelisted MAC, so the phone connects as that box (see [LockedViewModel.onScanned]).
 *  2. Tap one of the built-in servers.
 *  3. Type any portal URL by hand.
 * The phone’s own MAC is shown so a provider can whitelist the handset itself if needed.
 */
@Composable
private fun MobileSetupContent(
    state: SetupUiState,
    onScan: () -> Unit,
    onPortalInput: (String) -> Unit,
    onConnectTyped: () -> Unit,
    onSelectPortal: (Int) -> Unit,
    onConnectPortal: () -> Unit
) {
    val palette = appPalette
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(12.dp))
        BrandWordmark(modifier = Modifier.width(190.dp), glow = false)
        Spacer(Modifier.height(6.dp))
        Text(
            "Connect your account",
            color = palette.text, fontSize = 22.sp, fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(20.dp))

        // ---- 1. Scan the TV’s QR (imports the TV’s portal + whitelisted MAC) ----
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(palette.primary)
                .clickable { onScan() }
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (state.connecting) "CONNECTING…" else "SCAN THE QR ON YOUR TV",
                color = palette.background, fontSize = 16.sp, fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "TV → Settings → Connect Android app. Scanning copies that box\u2019s server and MAC.",
            color = palette.textSecondary, fontSize = 12.sp, textAlign = TextAlign.Center,
            lineHeight = 17.sp
        )

        Spacer(Modifier.height(22.dp))
        DividerLabel("or choose a server")
        Spacer(Modifier.height(12.dp))

        // ---- 2. Built-in servers ----
        RemoteConfig.PORTALS.forEachIndexed { i, portal ->
            val on = i == state.selectedPortal
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (on) palette.primary.copy(alpha = 0.16f) else palette.surface)
                    .border(
                        1.dp,
                        if (on) palette.primary else palette.primary.copy(alpha = 0.22f),
                        RoundedCornerShape(12.dp)
                    )
                    .clickable { onSelectPortal(i) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(portal.label, color = palette.text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text(
                        portal.url.removePrefix("http://").removePrefix("https://"),
                        color = palette.textSecondary, fontSize = 12.sp
                    )
                }
                if (on) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(palette.primary))
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(palette.surface)
                .border(1.dp, palette.primary.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                .clickable(enabled = !state.connecting) { onConnectPortal() }
                .padding(vertical = 13.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("CONNECT", color = palette.primary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(22.dp))
        DividerLabel("or enter a portal URL")
        Spacer(Modifier.height(12.dp))

        // ---- 3. Hand-typed URL ----
        SetupTextField(
            value = state.portalInput,
            onChange = onPortalInput,
            placeholder = "http://your-portal.com/c/",
            onImeAction = onConnectTyped,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(palette.surface)
                .border(1.dp, palette.primary.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                .clickable(enabled = !state.connecting) { onConnectTyped() }
                .padding(vertical = 13.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("CONNECT TO THIS URL", color = palette.primary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }

        state.connectError?.let {
            Spacer(Modifier.height(14.dp))
            Text(it, color = Color(0xFFD9776A), fontSize = 13.sp, textAlign = TextAlign.Center)
        }

        Spacer(Modifier.height(22.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("This phone\u2019s MAC:  ", color = palette.textSecondary, fontSize = 12.sp)
            Text(
                state.mac.ifBlank { "…" },
                color = palette.primary, fontSize = 13.sp, fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(4.dp))
        Text("Version ${BuildConfig.VERSION_NAME}", color = palette.textSecondary, fontSize = 11.sp)
        Spacer(Modifier.height(24.dp))
    }
}

/** A thin rule with a caption in the middle — separates the three ways to connect. */
@Composable
private fun DividerLabel(text: String) {
    val palette = appPalette
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.weight(1f).height(1.dp).background(palette.primary.copy(alpha = 0.22f)))
        Text("  $text  ", color = palette.textSecondary, fontSize = 12.sp)
        Box(Modifier.weight(1f).height(1.dp).background(palette.primary.copy(alpha = 0.22f)))
    }
}

/** Rounded text box for the hand-typed portal URL. */
@Composable
private fun SetupTextField(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    onImeAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = appPalette
    Row(
        modifier = modifier
            .height(50.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(palette.surface)
            .border(1.dp, palette.primary.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(color = palette.text, fontSize = 15.sp),
            cursorBrush = SolidColor(palette.primary),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Go,
                autoCorrect = false
            ),
            keyboardActions = KeyboardActions(onGo = { onImeAction() }, onDone = { onImeAction() }),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                if (value.isEmpty()) {
                    Text(placeholder, color = palette.textSecondary, fontSize = 15.sp)
                }
                inner()
            }
        )
    }
}

/** True on Android TV / leanback boxes (D-pad); false on phones & tablets (touch). */
@Composable
private fun rememberIsTv(): Boolean {
    val context = LocalContext.current
    return remember {
        val ui = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        ui?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    }
}
