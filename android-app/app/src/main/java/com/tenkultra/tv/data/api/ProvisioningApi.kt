package com.tenkultra.tv.data.api

import com.tenkultra.tv.data.api.models.DeviceConfigResponse
import com.tenkultra.tv.data.api.models.RegisterRequest
import com.tenkultra.tv.data.api.models.RegisterResponse
import com.tenkultra.tv.data.api.models.SetupPairingResponse
import com.tenkultra.tv.data.api.models.VersionCheckResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/** Talks to the 10K Ultra dashboard backend (not the Stalker portal) for QR activation. */
interface ProvisioningApi {

    @POST("api/device/register")
    suspend fun register(@Body body: RegisterRequest): RegisterResponse

    @GET("api/device/config")
    suspend fun config(@Query("device_id") deviceId: String): DeviceConfigResponse

    @GET("api/version/check")
    suspend fun versionCheck(
        @Query("version_code") versionCode: Int,
        @Query("device_id") deviceId: String
    ): VersionCheckResponse

    /** Universal QR-pairing: the box polls this until the provider submits a portal URL in the browser. */
    @GET("api/setup/{code}")
    suspend fun getSetupPairing(@Path("code") code: String): SetupPairingResponse
}
