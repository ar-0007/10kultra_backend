package com.tenkultra.tv.util

import com.tenkultra.tv.BuildConfig
import java.net.URLEncoder

/**
 * Points the app at the 10K Ultra backend + dashboard.
 *
 * - [BACKEND_BASE]  = the Express API on Render (device register/config, OTA, QR image).
 * - [DASHBOARD_BASE] = the admin dashboard on Netlify (the page a reseller opens from the QR to
 *   activate this device and assign its portal URL).
 * Both must end with "/".
 *
 * THIS IS THE DASHBOARD-MANAGED BUILD (not the universal/STB-Emu one): every box must be activated
 * from the dashboard before it can watch anything, the portal URL is assigned there (never typed on
 * the box), and the reseller can revoke or mark a box unpaid at any time — see [ActivationGuard].
 */
object RemoteConfig {
    /**
     * Backend API. The app calls register/config/qr/version here.
     *
     * MUST be 10K Ultra's OWN backend, never another product's: the version feed behind
     * `/api/version/check` is per-product, so pointing this at a different app's backend makes
     * every box show a bogus "Update Required" for that app's build number.
     *
     * DEBUG builds talk to the backend running on the DEV MACHINE (10.0.2.2 is how the Android
     * emulator reaches the host's localhost), so nothing has to be deployed to test the flow.
     */
    val BACKEND_BASE: String =
        if (BuildConfig.DEBUG) "http://10.0.2.2:4000/" else "https://tenkultra-backend.onrender.com/"

    /** Dashboard. The QR opens this so a reseller can activate the device. */
    val DASHBOARD_BASE: String =
        if (BuildConfig.DEBUG) "http://10.0.2.2:4178/" else "https://10kultra-dashboard.netlify.app/"

    /**
     * MANAGED BUILD: every box on this build is licensed through the 10K Ultra dashboard, so every
     * portal it can ever be pointed at is a managed one. Keeping this as a function (rather than
     * inlining `true`) leaves one place to carve out an exception later if a reseller is ever given
     * their own un-gated portal.
     */
    @Suppress("UNUSED_PARAMETER")
    fun isManagedPortal(url: String?): Boolean = true

    /**
     * A build-time pinned portal (`-PforcedPortal=…`). When set, this APK talks ONLY to that server:
     * the box skips registration/activation entirely and connects on boot. Blank in every normal
     * build, which leaves the dashboard-managed flow untouched.
     */
    val FORCED_PORTAL: String? = BuildConfig.FORCED_PORTAL.takeIf { it.isNotBlank() }

    /** One selectable server on the setup screen / in the dashboard's server picker. */
    data class Portal(val label: String, val url: String)

    /**
     * The servers 10K Ultra ships with. Shown as one-tap choices on the setup screen and as the
     * quick-pick list in the dashboard, so nobody has to type a URL.
     *
     * All three were verified against the Stalker API (`/stalker_portal/server/load.php` →
     * `action=handshake` returns a token):
     *  - tv.a1tv.ac      — answers directly.
     *  - portal.elite4k.co — answers directly.
     *  - mega4k.cc       — its OWN host serves no Stalker API (404); it root-redirects to
     *    steel4k.cc, which does. The app probes paths on the host it is given and never hops
     *    hosts, so the working host is pinned here. Change this line if mega4k.cc ever serves
     *    the API itself.
     */
    val PORTALS = listOf(
        Portal("A1 TV", "http://tv.a1tv.ac"),
        Portal("Elite 4K", "http://portal.elite4k.co"),
        Portal("Mega 4K", "http://steel4k.cc")
    )

    /** Page the reseller opens (from the QR) to activate this device + set its server URL + MAC. */
    fun activateUrl(pairingCode: String): String =
        "${DASHBOARD_BASE}activation?code=$pairingCode"

    /**
     * Backend-hosted pairing page. Kept for the Settings → "Change portal" QR, which lets a reseller
     * re-point an ALREADY-ACTIVATED box from their phone without opening the full dashboard.
     */
    fun setupUrl(pairingCode: String, mac: String): String {
        val macQ = URLEncoder.encode(mac, "UTF-8")
        return "${BACKEND_BASE}setup/$pairingCode?mac=$macQ"
    }

    /** Backend-rendered QR PNG for [data] — loaded directly by the box (no on-device QR lib). */
    fun qrImageUrl(data: String, size: Int = 480): String {
        val enc = URLEncoder.encode(data, "UTF-8")
        return "${BACKEND_BASE}api/qr?data=$enc&size=$size"
    }

    /**
     * Deep link the TV shows as a QR so the 10K Ultra MOBILE app can copy this box's portal + MAC in
     * one scan (`tenkultra://connect?u=<portal>&m=<mac>`). The phone's camera opens it → the mobile
     * app (registered for this scheme) imports the config and connects. No backend involved.
     */
    fun mobileConnectLink(portalUrl: String, mac: String): String {
        val u = URLEncoder.encode(portalUrl, "UTF-8")
        val m = URLEncoder.encode(mac, "UTF-8")
        return "tenkultra://connect?u=$u&m=$m"
    }

    /** QR PNG of [mobileConnectLink] — shown in TV Settings → "Connect Android app". */
    fun mobileConnectQr(portalUrl: String, mac: String): String =
        qrImageUrl(mobileConnectLink(portalUrl, mac))
}
