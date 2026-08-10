package com.tenkultra.tv.data.api

import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

/**
 * Repairs the provider's broken redirect inside the app — the same fix the test proxy uses.
 *
 * star.homeip.net 302-redirects every request to p1.airce.io/stalker_portal/**c**/… , but
 * `/c/` is the Stalker HTML-UI folder, not the API, so that path 404s (Cloudflare). We strip
 * the stray `/c/` from the `Location` header (and collapse any accidental
 * `/stalker_portal/stalker_portal/` doubling) so OkHttp's redirect follower lands on the
 * working `/stalker_portal/…` API path. Runs as a NETWORK interceptor, so its rewritten
 * Location is what RetryAndFollowUpInterceptor reads when choosing the next hop.
 */
class RedirectFixInterceptor @Inject constructor() : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        val location = response.header("Location")
        if (response.isRedirect && location != null && location.contains(BROKEN)) {
            val fixed = location
                .replace(BROKEN, FIXED)
                .replace(DOUBLED, FIXED)
            return response.newBuilder()
                .header("Location", fixed)
                .build()
        }
        return response
    }

    private companion object {
        const val BROKEN = "/stalker_portal/c/"
        const val FIXED = "/stalker_portal/"
        const val DOUBLED = "/stalker_portal/stalker_portal/"
    }
}
