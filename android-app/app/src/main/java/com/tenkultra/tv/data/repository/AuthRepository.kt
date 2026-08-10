package com.tenkultra.tv.data.repository

import com.tenkultra.tv.data.api.SessionManager
import com.tenkultra.tv.data.api.StalkerApiService
import com.tenkultra.tv.data.api.StalkerEndpoints
import com.tenkultra.tv.data.datastore.SettingsDataStore
import com.tenkultra.tv.domain.model.AccountInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Raised when the portal reaches us but rejects the device/subscription. */
class PortalBlockedException(message: String) : Exception(message)

/** Shown when content can't load because the subscription has lapsed. */
const val SUBSCRIPTION_EXPIRED_MESSAGE =
    "Your subscription has expired. Please renew it to continue watching."

@Singleton
class AuthRepository @Inject constructor(
    private val api: StalkerApiService,
    private val session: SessionManager,
    private val settings: SettingsDataStore
) {
    /**
     * Discovers the working portal endpoint (trying the standard Stalker paths),
     * handshakes for a token, then validates the device/subscription via get_profile.
     * On success the working base + token are stored in [SessionManager].
     */
    suspend fun handshake(portalUrl: String): Result<String> = withContext(Dispatchers.IO) {
        // Try the last-known working base first (skips re-probing 5 URLs → faster startup).
        val cached = runCatching { settings.cachedBaseFlow.first() }.getOrNull()
        val candidates = (listOfNotNull(cached) + StalkerEndpoints.candidateBases(portalUrl)).distinct()
        var lastError: Throwable =
            IllegalStateException("Could not reach a Stalker portal at this URL.")

        for (base in candidates) {
            val resp = try {
                api.handshake(StalkerEndpoints.handshake(base))
            } catch (e: Exception) {
                lastError = e
                continue
            }
            val token = resp.body()?.js?.token?.takeIf { it.isNotBlank() } ?: continue

            // Pin the session to the host that ACTUALLY answered. star.homeip.net 302-redirects to
            // p1.airce.io on every request; by capturing the final post-redirect URL here we make all
            // later calls (create_link, lists, profile) go DIRECT — cutting a full redirect
            // round-trip (~0.5–1s) off every single portal request. Falls back to the tried base if
            // no redirect happened.
            val finalUrl = resp.raw().request.url
            val resolvedBase = runCatching {
                finalUrl.newBuilder().query(null).fragment(null).build().toString()
            }.getOrDefault(base)

            // Working endpoint found.
            session.endpointBase = resolvedBase
            session.token = token
            runCatching { settings.setCachedBase(resolvedBase) }

            // Validate device/subscription — surface the provider's own message if blocked.
            val block = profileBlockMessage(resolvedBase)
            return@withContext if (block != null) {
                Result.failure(PortalBlockedException(block))
            } else {
                Result.success(token)
            }
        }
        Result.failure(lastError)
    }

    /**
     * Fetches the subscriber's account/subscription details (name, package, expiry,
     * days left). Re-handshakes via the saved portal if the session isn't open yet.
     * Returns null only if there's no portal configured / reachable.
     */
    suspend fun fetchAccountInfo(): AccountInfo? = withContext(Dispatchers.IO) {
        var base = session.endpointBase
        if (base.isNullOrBlank()) {
            val portal = runCatching { settings.portalUrlFlow.first() }.getOrNull().orEmpty()
            if (portal.isBlank()) return@withContext null
            handshake(portal)
            base = session.endpointBase
        }
        val b = base ?: return@withContext null

        val acc = runCatching { api.getAccountInfo(StalkerEndpoints.getAccountInfo(b)).js }.getOrNull()
        val prof = runCatching { api.getProfile(StalkerEndpoints.getProfile(b)).js }.getOrNull()

        val name = listOfNotNull(acc?.fname, acc?.fullName, acc?.login, prof?.name)
            .firstOrNull { it.isNotBlank() } ?: "—"
        val pkg = listOfNotNull(acc?.tariffPlanName, acc?.tariffPlan)
            .firstOrNull { it.isNotBlank() } ?: "—"
        // Real expiry field first; many reseller panels stash it in the 'phone' field.
        val expiryRaw = listOfNotNull(acc?.endDate, acc?.expBillingDate)
            .firstOrNull { it.isNotBlank() && looksLikeDate(it) }
            ?: acc?.phone?.takeIf { looksLikeDate(it) }
            ?: "—"
        val active = ((prof?.status ?: acc?.status ?: 0) == 0) && ((prof?.blocked ?: "0") == "0")
        val trial = pkg.contains("trial", true) || pkg.contains("test", true)

        AccountInfo(
            name = name,
            packageName = pkg,
            expiry = expiryRaw,
            daysLeft = daysUntil(expiryRaw),
            phone = acc?.phone?.takeIf { it.isNotBlank() } ?: "—",
            status = if (active) "Active" else "Blocked",
            isTrial = trial
        )
    }

    /**
     * True when the portal reports the subscription as no longer valid — either the profile is
     * blocked/unauthorized, or the account's end date is already in the past. Lets the UI show a
     * clear "renew your subscription" message instead of a generic "couldn't load" error.
     * Returns false when there's no portal/network (so we don't cry expiry on a plain outage).
     */
    suspend fun subscriptionExpired(): Boolean = withContext(Dispatchers.IO) {
        val info = fetchAccountInfo() ?: return@withContext false
        val pastDue = info.daysLeft != null && info.daysLeft < 0
        info.status != "Active" || pastDue
    }

    private fun looksLikeDate(s: String): Boolean =
        Regex("\\d{4}[-/.]\\d{1,2}[-/.]\\d{1,2}").containsMatchIn(s) ||
            Regex("\\d{1,2}[-/.]\\d{1,2}[-/.]\\d{4}").containsMatchIn(s)

    private fun daysUntil(raw: String): Int? {
        if (raw == "—") return null
        val formats = listOf(
            "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd", "dd.MM.yyyy",
            "dd/MM/yyyy", "MM/dd/yyyy", "MMMM dd, yyyy"
        )
        for (f in formats) {
            val d = runCatching { SimpleDateFormat(f, Locale.ENGLISH).parse(raw.trim()) }.getOrNull()
            if (d != null) {
                return TimeUnit.MILLISECONDS.toDays(d.time - System.currentTimeMillis()).toInt()
            }
        }
        return null
    }

    /**
     * Returns the provider block message if the profile is NOT authorized (else null). Detects a
     * block TWO ways so it's reliable across portals: (a) an explicit block/conflict message, OR
     * (b) the profile's status/blocked FLAGS (status != 0 or blocked != "0") — some portals reject a
     * non-whitelisted MAC via the flags without any message. A healthy authorized box returns
     * status 0 + blocked "0" → null (not blocked). When flagged-but-message-less, returns "" so the
     * caller shows its default "contact your provider" text.
     */
    private suspend fun profileBlockMessage(base: String): String? = runCatching {
        val js = api.getProfile(StalkerEndpoints.getProfile(base)).js ?: return@runCatching null
        val explicit = js.blockMsg?.takeIf { it.isNotBlank() }
        val conflict = js.msg?.takeIf {
            it.contains("conflict", true) ||
                it.contains("authorization", true) ||
                it.contains("blocked", true)
        }
        val message = when {
            explicit != null && conflict != null -> "$conflict\n$explicit"
            else -> explicit ?: conflict
        }?.replace("<br>", " ")?.trim()

        // Flag-based block: status non-zero, or blocked not "0"/"false".
        val flaggedBlocked = (js.status != null && js.status != 0) ||
            (js.blocked?.trim()?.takeIf { it.isNotEmpty() }?.let { it != "0" && !it.equals("false", true) } == true)

        when {
            !message.isNullOrBlank() -> message   // portal gave a reason
            flaggedBlocked -> ""                  // blocked via flags, no message → default text
            else -> null                          // authorized
        }
    }.getOrNull()
}
