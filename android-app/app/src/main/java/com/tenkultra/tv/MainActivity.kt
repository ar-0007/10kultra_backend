package com.tenkultra.tv

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tenkultra.tv.data.api.SessionManager
import com.tenkultra.tv.data.datastore.ContentCache
import com.tenkultra.tv.data.datastore.SettingsDataStore
import com.tenkultra.tv.presentation.common.ConnectivityViewModel
import com.tenkultra.tv.presentation.common.NoInternetOverlay
import com.tenkultra.tv.presentation.common.RemoteKeyBus
import com.tenkultra.tv.presentation.common.UpdateOverlay
import com.tenkultra.tv.presentation.navigation.AppNavigation
import com.tenkultra.tv.presentation.theme.TenKUltraTheme
import com.tenkultra.tv.presentation.theme.ThemeViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settings: SettingsDataStore
    @Inject lateinit var session: SessionManager
    @Inject lateinit var contentCache: ContentCache
    /**
     * Catch every key at the Activity level — before Compose focus routing — so the remote's
     * COLOUR buttons fire reliably even when a child view (preview player, text field) holds
     * focus. Colour keys are folded to a [RemoteColor] and pushed on [RemoteKeyBus] for whichever
     * screen is showing; everything else (D-pad/OK/Back) flows on to Compose as usual.
     *
     * The Log.d line is a diagnostic: if a remote's colour button doesn't work, run
     * `adb logcat -s UltraKey`, press the button, and read the exact keyCode to add in
     * [RemoteKeyBus.colorFor].
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            // Diagnostic only in debug builds — this is the hottest input path (every D-pad press),
            // so building the log string on every keystroke in release was pure overhead.
            if (BuildConfig.DEBUG) {
                Log.d("UltraKey", "keyCode=${event.keyCode} name=${KeyEvent.keyCodeToString(event.keyCode)}")
            }
            RemoteKeyBus.colorFor(event.keyCode)?.let { color ->
                RemoteKeyBus.emit(color)
                return true // consume so it doesn't double-fire through Compose too
            }
        } else if (event.action == KeyEvent.ACTION_UP && RemoteKeyBus.colorFor(event.keyCode) != null) {
            return true // swallow the matching UP so the system doesn't also act on it
        }
        return super.dispatchKeyEvent(event)
    }

    /**
     * Mobile pairing: the 10K Ultra MOBILE app opens `tenkultra://connect?u=<portal>&m=<mac>` (from
     * scanning the TV's QR). Import the portal + MAC so this install connects to the same account.
     * Handled here (before Splash reads the saved portal) — no camera lib, works offline.
     */
    private fun handleConnectLink(intent: Intent?): Boolean {
        val data = intent?.data ?: return false
        if (data.scheme != "tenkultra" || data.host != "connect") return false
        val url = data.getQueryParameter("u")?.trim().orEmpty()
        val mac = data.getQueryParameter("m")?.trim().orEmpty()
        if (url.isBlank()) return false
        runBlocking {
            settings.clearCachedBase()
            contentCache.clear()
            if (mac.isNotBlank()) settings.setMac(mac)
            settings.setPortalUrl(url)
        }
        session.endpointBase = ""
        session.token = ""
        return true
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // App already running → import + relaunch so Splash reconnects to the new portal cleanly.
        if (handleConnectLink(intent)) {
            runCatching {
                val relaunch = packageManager.getLaunchIntentForPackage(packageName)
                relaunch?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                startActivity(relaunch)
            }
            Runtime.getRuntime().exit(0)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // TV/box → locked landscape. Phone/tablet → locked PORTRAIT (only the video player flips to
        // landscape, and restores portrait on exit).
        val ui = getSystemService(Context.UI_MODE_SERVICE) as? android.app.UiModeManager
        val isTv = ui?.currentModeType == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION ||
            packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK)
        requestedOrientation = if (isTv) {
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        } else {
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        // Cold start via the pairing link → import before the UI (Splash) reads the saved portal.
        handleConnectLink(intent)
        setContent {
            val themeViewModel: ThemeViewModel = hiltViewModel()
            val theme by themeViewModel.theme.collectAsStateWithLifecycle()
            TenKUltraTheme(theme = theme) {
                Box(Modifier.fillMaxSize()) {
                    AppNavigation()
                    // App-wide "No Internet Connection" popup — shows whenever the
                    // device drops its validated internet, auto-hides when it returns.
                    val connectivity: ConnectivityViewModel = hiltViewModel()
                    val online by connectivity.isOnline.collectAsStateWithLifecycle()
                    NoInternetOverlay(visible = !online)
                    // App-wide OTA updater — silent check on launch, prompts when the
                    // dashboard publishes a newer APK, downloads + installs it.
                    UpdateOverlay()
                }
            }
        }
    }
}
