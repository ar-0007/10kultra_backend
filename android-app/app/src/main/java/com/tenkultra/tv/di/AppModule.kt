package com.tenkultra.tv.di

import com.tenkultra.tv.BuildConfig
import com.tenkultra.tv.data.api.ProvisioningApi
import com.tenkultra.tv.data.api.RedirectFixInterceptor
import com.tenkultra.tv.data.api.ResilientDns
import com.tenkultra.tv.data.api.StalkerApiService
import com.tenkultra.tv.data.api.StalkerAuthInterceptor
import com.tenkultra.tv.util.RemoteConfig
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.InetAddress
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideGson(): Gson = GsonBuilder().setLenient().create()

    /**
     * DNS that resolves via the box first, then DNS-over-HTTPS (Google, bootstrapped
     * by literal IPs so it needs no DNS to start). Fixes boxes that can't resolve
     * dynamic-DNS portal hostnames like star.homeip.net.
     */
    @Provides
    @Singleton
    fun provideDns(): Dns {
        val doh = DnsOverHttps.Builder()
            .client(OkHttpClient.Builder().build())
            .url("https://dns.google/dns-query".toHttpUrl())
            .bootstrapDnsHosts(
                InetAddress.getByName("8.8.8.8"),
                InetAddress.getByName("8.8.4.4")
            )
            .includeIPv6(false)
            .build()
        return ResilientDns(Dns.SYSTEM, doh)
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        redirectFix: RedirectFixInterceptor,
        authInterceptor: StalkerAuthInterceptor,
        dns: Dns
    ): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            // No logging overhead in release builds.
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC
            else HttpLoggingInterceptor.Level.NONE
        }
        return OkHttpClient.Builder()
            .dns(dns)
            // Repairs the provider's broken redirect on every hop: star.homeip.net 302 →
            // p1.airce.io/stalker_portal/c/… (a dead 404 path). This strips the stray
            // "/c/" from the Location header so the auto-redirect lands on the working
            // /stalker_portal/… API. Added FIRST so its rewritten Location reaches the
            // redirect follower.
            .addNetworkInterceptor(redirectFix)
            // Auth as a NETWORK interceptor so it re-stamps the MAC cookie + device
            // params + Bearer on EVERY hop — including cross-host redirects (e.g.
            // star.homeip.net 302 → p1.airce.io/…), where OkHttp would otherwise strip
            // the cookie/authorization.
            .addNetworkInterceptor(authInterceptor)
            .addInterceptor(logging)
            // Tighter timeouts so a slow/dead host fails fast instead of freezing the UI for 15-20s
            // (paired with withRefresh now doing a single re-auth+retry, not 3).
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .writeTimeout(12, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, gson: Gson): Retrofit =
        Retrofit.Builder()
            // Base URL is a placeholder; every call supplies an absolute @Url.
            .baseUrl("http://localhost/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()

    @Provides
    @Singleton
    fun provideStalkerApiService(retrofit: Retrofit): StalkerApiService =
        retrofit.create(StalkerApiService::class.java)

    /**
     * Dashboard (control-panel) API for QR activation + OTA. Uses its own clean
     * OkHttp client — NO Stalker auth/redirect interceptors — pointed at
     * [RemoteConfig.DASHBOARD_BASE].
     */
    @Provides
    @Singleton
    fun provideProvisioningApi(dns: Dns, gson: Gson): ProvisioningApi {
        val client = OkHttpClient.Builder()
            .dns(dns)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
        return Retrofit.Builder()
            .baseUrl(RemoteConfig.BACKEND_BASE)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(ProvisioningApi::class.java)
    }
}
