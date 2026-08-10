package com.tenkultra.tv.data.api

import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stamps every request with the Stalker session: the MAC cookie, MAG STB
 * user-agent identifiers, the Bearer token (once handshaked), and the device
 * fingerprint query params (sn / device_id / device_id2 / stb_type) the portal
 * uses to authorize the device.
 */
@Singleton
class StalkerAuthInterceptor @Inject constructor(
    private val session: SessionManager
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()

        // Append the device fingerprint to the URL (portals validate these).
        val urlBuilder = original.url.newBuilder()
        if (session.mac.isNotBlank()) {
            val deviceId = DeviceFingerprint.deviceId(session.mac)
            urlBuilder.setQueryParameter("sn", DeviceFingerprint.serialNumber(session.mac))
            urlBuilder.setQueryParameter("device_id", deviceId)
            urlBuilder.setQueryParameter("device_id2", deviceId)
            urlBuilder.setQueryParameter("stb_type", DeviceFingerprint.STB_TYPE)
        }

        val builder = original.newBuilder()
            .url(urlBuilder.build())
            .header(
                "User-Agent",
                "Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) " +
                    "MAG200 stbapp ver: 2 rev: 250 Safari/533.3"
            )
            .header("X-User-Agent", "Model: MAG250; Link: WiFi")
            .header("Accept", "*/*")
            .header(
                "Cookie",
                "mac=${session.mac}; stb_lang=en; timezone=Europe/London"
            )

        if (session.token.isNotBlank()) {
            builder.header("Authorization", "Bearer ${session.token}")
        }

        return chain.proceed(builder.build())
    }
}
