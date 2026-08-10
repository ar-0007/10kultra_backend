package com.tenkultra.tv.data.api.models

import com.google.gson.annotations.SerializedName

/** POST /api/device/register body — identifies this box to the dashboard. */
data class RegisterRequest(
    @SerializedName("device_id") val deviceId: String,
    @SerializedName("mac") val mac: String,
    @SerializedName("model") val model: String?,
    @SerializedName("sig_sha256") val sigSha256: String?,
    @SerializedName("app_version") val appVersion: Int?,
    // The portal this box is actually using. For a reseller's own (self-managed) URL this lets the
    // dashboard SHOW it without any activate/revoke role. Null/blank until a portal is chosen.
    @SerializedName("server_url") val serverUrl: String? = null
)

/** Response of /api/device/register — the single-use pairing code to show in the QR. */
data class RegisterResponse(
    @SerializedName("status") val status: String?,
    @SerializedName("pairing_code") val pairingCode: String?
)

/** Response of /api/device/config — the assigned portal once activated + paid. */
data class DeviceConfigResponse(
    @SerializedName("status") val status: String?,   // pending | activated | payment_required | revoked | unknown
    @SerializedName("server_url") val serverUrl: String?,
    @SerializedName("mac") val mac: String?,
    @SerializedName("payment_status") val paymentStatus: String? // paid | unpaid | expired
)

/** Response of /api/setup/{code} — the portal URL the provider entered in the browser (null until set). */
data class SetupPairingResponse(
    @SerializedName("server_url") val serverUrl: String?
)

/** Response of /api/version/check — the latest published APK, if newer than this build. */
data class VersionCheckResponse(
    @SerializedName("update_available") val updateAvailable: Boolean = false,
    @SerializedName("version_code") val versionCode: Int? = null,
    @SerializedName("version_name") val versionName: String? = null,
    @SerializedName("apk_url") val apkUrl: String? = null,
    @SerializedName("apk_size") val apkSize: Long? = null,
    @SerializedName("changelog") val changelog: String? = null,
    @SerializedName("force_update") val forceUpdate: Boolean = false
)
