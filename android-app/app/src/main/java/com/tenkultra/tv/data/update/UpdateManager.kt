package com.tenkultra.tv.data.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import com.tenkultra.tv.BuildConfig
import com.tenkultra.tv.data.api.DeviceFingerprint
import com.tenkultra.tv.data.api.ProvisioningApi
import com.tenkultra.tv.data.datastore.SettingsDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** What the OTA updater wants the UI to show. */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(val percent: Int) : UpdateState
    data object Installing : UpdateState
    data class Error(val message: String) : UpdateState
}

/** The latest published build offered by the dashboard. */
data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val sizeBytes: Long,
    val changelog: String,
    val force: Boolean
)

/**
 * Drives the over-the-air update flow end to end: asks the backend whether a newer published
 * APK exists, downloads it, then installs it via [PackageInstaller]. Singleton so the app-wide
 * overlay and the Settings screen share one state. The first check is silent/non-blocking so
 * the normal 1–2s startup is unaffected.
 */
@Singleton
class UpdateManager @Inject constructor(
    private val api: ProvisioningApi,
    private val settings: SettingsDataStore,
    @ApplicationContext private val context: Context
) {
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state = _state.asStateFlow()

    init { active = this }

    /** Called by [UpdateInstallReceiver] once the system reports the install outcome. */
    internal fun onInstallResult(success: Boolean, message: String?) {
        _state.value = if (success) UpdateState.Idle else UpdateState.Error(message ?: "Install was cancelled.")
    }

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /**
     * Checks the dashboard for a newer build. [silent] = true (startup) never surfaces errors or
     * an "up to date" state; [silent] = false (manual "Check now") reports both.
     */
    suspend fun check(silent: Boolean = true) = withContext(Dispatchers.IO) {
        // Don't interrupt an in-progress download/install.
        if (_state.value is UpdateState.Downloading || _state.value is UpdateState.Installing) return@withContext
        if (!silent) _state.value = UpdateState.Checking
        runCatching {
            val deviceId = DeviceFingerprint.deviceId(settings.getOrCreateMac())
            val r = api.versionCheck(BuildConfig.VERSION_CODE, deviceId)
            if (r.updateAvailable && (r.versionCode ?: 0) > BuildConfig.VERSION_CODE && !r.apkUrl.isNullOrBlank()) {
                _state.value = UpdateState.Available(
                    UpdateInfo(
                        versionCode = r.versionCode ?: 0,
                        versionName = r.versionName ?: "",
                        apkUrl = r.apkUrl,
                        sizeBytes = r.apkSize ?: 0L,
                        changelog = r.changelog ?: "",
                        force = r.forceUpdate
                    )
                )
            } else if (!silent) {
                _state.value = UpdateState.UpToDate
            }
        }.onFailure { if (!silent) _state.value = UpdateState.Error("Couldn't check for updates.") }
    }

    /** Downloads the APK with progress, then hands it to the system installer. */
    suspend fun downloadAndInstall(info: UpdateInfo) = withContext(Dispatchers.IO) {
        _state.value = UpdateState.Downloading(0)
        val apk = runCatching { download(info) }.getOrElse {
            _state.value = UpdateState.Error("Download failed. Check the connection and try again.")
            return@withContext
        }
        runCatching { install(apk) }.onFailure {
            _state.value = UpdateState.Error("Install failed: ${it.message ?: "unknown error"}")
        }
    }

    private fun download(info: UpdateInfo): File {
        val out = File(context.cacheDir, "tenkultra-update-${info.versionCode}.apk")
        if (out.exists()) out.delete()
        val resp = http.newCall(Request.Builder().url(info.apkUrl).build()).execute()
        resp.use {
            val body = it.body ?: throw IllegalStateException("empty response")
            if (!it.isSuccessful) throw IllegalStateException("HTTP ${it.code}")
            val total = if (info.sizeBytes > 0) info.sizeBytes else body.contentLength()
            body.byteStream().use { input ->
                out.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    var read: Int
                    var done = 0L
                    var lastPct = -1
                    while (input.read(buf).also { n -> read = n } != -1) {
                        output.write(buf, 0, read)
                        done += read
                        if (total > 0) {
                            val pct = ((done * 100) / total).toInt().coerceIn(0, 100)
                            if (pct != lastPct) { lastPct = pct; _state.value = UpdateState.Downloading(pct) }
                        }
                    }
                }
            }
        }
        return out
    }

    private fun install(apk: File) {
        _state.value = UpdateState.Installing
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            apk.inputStream().use { input ->
                session.openWrite("tenkultra.apk", 0, apk.length()).use { out ->
                    input.copyTo(out)
                    session.fsync(out)
                }
            }
            // EXPLICIT (component-targeted) intent — an implicit/action-only broadcast is NOT
            // delivered to a manifest receiver that has no <intent-filter>, so the
            // STATUS_PENDING_USER_ACTION callback would be lost and the system "Install?" dialog
            // would never appear (install hangs). Targeting the class directly fixes that.
            val intent = Intent(context, UpdateInstallReceiver::class.java)
                .setAction(UpdateInstallReceiver.ACTION)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            val pi = PendingIntent.getBroadcast(context, sessionId, intent, flags)
            session.commit(pi.intentSender)
        }
    }

    /** Lets the UI dismiss a non-forced prompt. */
    fun dismiss() { if (_state.value is UpdateState.Available) _state.value = UpdateState.Idle }

    companion object {
        /** The live singleton, so [UpdateInstallReceiver] can report the install outcome back. */
        @Volatile internal var active: UpdateManager? = null
    }
}
