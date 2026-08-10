package com.tenkultra.tv.data.api

import com.tenkultra.tv.data.api.models.CategoryListResponse
import com.tenkultra.tv.data.api.models.ChannelListResponse
import com.tenkultra.tv.data.api.models.CreateLinkResponse
import com.tenkultra.tv.data.api.models.EpisodeListResponse
import com.tenkultra.tv.data.api.models.AccountInfoResponse
import com.tenkultra.tv.data.api.models.HandshakeResponse
import com.tenkultra.tv.data.api.models.ProfileResponse
import com.tenkultra.tv.data.api.models.SeasonListResponse
import com.tenkultra.tv.data.api.models.ShortEpgResponse
import com.tenkultra.tv.data.api.models.VodListResponse
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Url

/**
 * Retrofit interface for the Stalker Middleware portal. Every call passes the full
 * absolute URL via [Url] (built by [StalkerEndpoints]); headers are added by
 * [StalkerAuthInterceptor]. Endpoints beyond auth return raw bodies for now and are
 * typed in later build steps (channels / VOD / EPG).
 */
interface StalkerApiService {

    // Returns the full Response so the caller can read the FINAL (post-redirect) URL and pin the
    // session to the host that actually answered — skipping the star.homeip.net→p1.airce.io
    // redirect on every later call.
    @GET
    suspend fun handshake(@Url url: String): Response<HandshakeResponse>

    @GET
    suspend fun getProfile(@Url url: String): ProfileResponse

    @GET
    suspend fun getAccountInfo(@Url url: String): AccountInfoResponse

    @GET
    suspend fun getAllChannels(@Url url: String): ResponseBody

    @GET
    suspend fun getItvList(@Url url: String): ChannelListResponse

    @GET
    suspend fun createLink(@Url url: String): CreateLinkResponse

    /** Raw create_link body — lets us capture the EXACT portal response (even non-JSON) for the
     *  on-screen play diagnostic, and salvage a URL from portals that answer in an odd shape. */
    @GET
    suspend fun createLinkRaw(@Url url: String): ResponseBody

    @GET
    suspend fun getGenres(@Url url: String): CategoryListResponse

    @GET
    suspend fun getVodCategories(@Url url: String): CategoryListResponse

    @GET
    suspend fun getOrderedList(@Url url: String): VodListResponse

    @GET
    suspend fun getSeasons(@Url url: String): SeasonListResponse

    @GET
    suspend fun getEpisodes(@Url url: String): EpisodeListResponse

    /** Resolves ONE episode's real playable file rows via &episode_id= (authoritative cmd). */
    @GET
    suspend fun getEpisodeFile(@Url url: String): EpisodeListResponse

    @GET
    suspend fun getEpgInfo(@Url url: String): ResponseBody

    @GET
    suspend fun getShortEpg(@Url url: String): ShortEpgResponse
}
