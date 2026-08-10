package com.tenkultra.tv.presentation.navigation

import android.util.Base64
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.tenkultra.tv.domain.model.AppLayout
import com.tenkultra.tv.domain.model.VodItem
import com.tenkultra.tv.presentation.common.ActivationGuard
import com.tenkultra.tv.presentation.common.LocalParentalGate
import com.tenkultra.tv.presentation.common.ParentalGateHost
import com.tenkultra.tv.presentation.common.SubscriptionGuard
import com.tenkultra.tv.presentation.common.rememberIsTv
import com.tenkultra.tv.presentation.mobile.MobileHome
import com.tenkultra.tv.presentation.mobile.MobilePlayerScreen
import com.tenkultra.tv.presentation.mobile.MobileSettingsScreen
import com.tenkultra.tv.presentation.mobile.MobileVodDetailScreen
import com.tenkultra.tv.presentation.screens.blocked.BlockedScreen
import com.tenkultra.tv.presentation.screens.cinematic.CinematicHomeScreen
import com.tenkultra.tv.presentation.screens.home.HomeScreen
import com.tenkultra.tv.presentation.screens.livetv.ChannelListScreen
import com.tenkultra.tv.presentation.screens.locked.LockedScreen
import com.tenkultra.tv.presentation.screens.media.MediaBrowserScreen
import com.tenkultra.tv.presentation.screens.modern.ModernHomeScreen
import com.tenkultra.tv.presentation.screens.player.PlayerScreen
import com.tenkultra.tv.presentation.screens.settings.SettingsScreen
import com.tenkultra.tv.presentation.screens.splash.SplashScreen
import com.tenkultra.tv.presentation.screens.vod.VodDetailArgs
import com.tenkultra.tv.presentation.screens.vod.VodDetailScreen
import com.tenkultra.tv.presentation.screens.vod.VodListScreen
import com.tenkultra.tv.presentation.theme.ThemeViewModel
import com.google.gson.Gson
import java.net.URLEncoder

object Routes {
    const val SPLASH = "splash"
    const val LOCKED = "locked"
    const val BLOCKED = "blocked/{message}"
    const val HOME = "home"
    const val VOD_LIST = "vod_list/{categoryId}/{categoryTitle}"
    const val VOD_DETAIL = "vod_detail/{payload}"
    const val CHANNEL_LIST = "channels/{genreId}/{genreTitle}"
    const val PLAYER = "player/{type}/{cmd}/{title}/{series}"
    const val SETTINGS = "settings"
    const val MEDIA = "media"

    private fun enc(value: String) = URLEncoder.encode(value, "UTF-8")
    private fun b64(value: String) =
        Base64.encodeToString(value.toByteArray(), Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)

    fun vodList(categoryId: String, categoryTitle: String) =
        "vod_list/${enc(categoryId)}/${enc(categoryTitle)}"

    fun vodDetail(item: VodItem, categoryId: String): String {
        val args = VodDetailArgs(
            movieId = item.id,
            categoryId = categoryId,
            name = item.name,
            year = item.year,
            genre = item.genre,
            description = item.description,
            posterUrl = item.posterUrl,
            cmd = item.cmd,
            isSeries = item.isSeries
        )
        return "vod_detail/${b64(Gson().toJson(args))}"
    }

    fun channelList(genreId: String, genreTitle: String) =
        "channels/${enc(genreId)}/${enc(genreTitle)}"

    // Empty b64 (encodes "") stands for "no provider message → use the default text".
    fun blocked(message: String?) = "blocked/${b64(message.orEmpty())}"

    fun player(type: String, cmd: String, title: String, series: Int = 0) =
        "player/$type/${b64(cmd)}/${b64(title)}/$series"
}

@Composable
fun AppNavigation() {
    // CHILD LOCK hosted above the whole nav graph: every layout's "open this content" call below
    // goes through the gate, so adult content asks for the PIN no matter which screen it came from.
    ParentalGateHost { AppNavHost() }
}

@Composable
private fun AppNavHost() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.SPLASH,
        // Smooth, snappy screen transitions (gentle fade + slight slide).
        enterTransition = { fadeIn(tween(240)) + slideInHorizontally(tween(260)) { it / 14 } },
        exitTransition = { fadeOut(tween(160)) },
        popEnterTransition = { fadeIn(tween(220)) },
        popExitTransition = { fadeOut(tween(180)) + slideOutHorizontally(tween(240)) { it / 14 } }
    ) {
        composable(Routes.SPLASH) {
            SplashScreen(
                onNavigateToLocked = {
                    navController.navigate(Routes.LOCKED) { popUpTo(Routes.SPLASH) { inclusive = true } }
                },
                onNavigateToHome = {
                    navController.navigate(Routes.HOME) { popUpTo(Routes.SPLASH) { inclusive = true } }
                },
                onNavigateToBlocked = { message ->
                    navController.navigate(Routes.blocked(message)) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                }
            )
        }
        composable(
            route = Routes.BLOCKED,
            arguments = listOf(navArgument("message") { type = NavType.StringType })
        ) { entry ->
            val raw = entry.arguments?.getString("message").orEmpty()
            val message = runCatching {
                String(Base64.decode(raw, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP))
            }.getOrNull()?.takeIf { it.isNotBlank() }
            BlockedScreen(
                message = message,
                onConnected = {
                    navController.navigate(Routes.HOME) { popUpTo(Routes.BLOCKED) { inclusive = true } }
                }
            )
        }
        composable(Routes.LOCKED) {
            LockedScreen(
                onConnected = {
                    navController.navigate(Routes.HOME) { popUpTo(Routes.LOCKED) { inclusive = true } }
                }
            )
        }
        composable(Routes.HOME) {
            val isTv = rememberIsTv()
            val gate = LocalParentalGate.current
            // Subscription expired → dismissible dialog with a shortcut to Settings so the user can
            // change the portal URL. Never blocks Settings access. (Applies on TV + mobile.)
            SubscriptionGuard(
                onOpenSettings = { navController.navigate(Routes.SETTINGS) }
            )
            if (!isTv) {
                // ---- MOBILE (touch) UI: bottom-nav home ----
                MobileHome(
                    onPlayChannel = { channel ->
                        navController.navigate(Routes.player("itv", channel.cmd, channel.name))
                    },
                    onOpenVod = { item, categoryId ->
                        if (item.isSeries) navController.navigate(Routes.vodDetail(item, categoryId))
                        else navController.navigate(Routes.player("vod", item.cmd, item.name))
                    },
                    // "Continue watching" resumes the exact movie/episode — no detail screen between.
                    onResumeVod = { cmd, title, series ->
                        navController.navigate(Routes.player("vod", cmd, title, series))
                    },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) }
                )
                return@composable
            }
            val prefs: ThemeViewModel = hiltViewModel()
            val layout by prefs.layout.collectAsStateWithLifecycle()
            // Remote kill switch (TV/box only): if revoked/unpaid, drop back to the locked screen.
            ActivationGuard(
                onLocked = {
                    navController.navigate(Routes.LOCKED) { popUpTo(Routes.HOME) { inclusive = true } }
                }
            )
            if (layout == AppLayout.MODERN) {
                ModernHomeScreen(
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    onOpenVodItem = { item, categoryId ->
                        if (item.isSeries) navController.navigate(Routes.vodDetail(item, categoryId))
                        else navController.navigate(Routes.player("vod", item.cmd, item.name))
                    },
                    onPlayChannel = { channel ->
                        navController.navigate(Routes.player("itv", channel.cmd, channel.name))
                    }
                )
            } else if (layout == AppLayout.CINEMATIC) {
                CinematicHomeScreen(
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    onOpenVodItem = { item, categoryId ->
                        if (item.isSeries) navController.navigate(Routes.vodDetail(item, categoryId))
                        else navController.navigate(Routes.player("vod", item.cmd, item.name))
                    },
                    onPlayChannel = { channel ->
                        navController.navigate(Routes.player("itv", channel.cmd, channel.name))
                    },
                    // "Continue watching" resumes the exact movie/episode — no detail screen in between.
                    onResumeVod = { cmd, title, series ->
                        navController.navigate(Routes.player("vod", cmd, title, series))
                    }
                )
            } else {
                HomeScreen(
                    onOpenVodCategory = { category ->
                        gate.guard(category.title) {
                            navController.navigate(Routes.vodList(category.id, category.title))
                        }
                    },
                    onOpenTvGenre = { genre ->
                        gate.guard(genre.title) {
                            navController.navigate(Routes.channelList(genre.id, genre.title))
                        }
                    },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    onOpenMedia = { navController.navigate(Routes.MEDIA) }
                )
            }
        }
        composable(Routes.SETTINGS) {
            if (rememberIsTv()) SettingsScreen(onBack = { navController.popBackStack() })
            else MobileSettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.MEDIA) {
            MediaBrowserScreen(
                onBack = { navController.popBackStack() },
                onPlayFile = { path, name ->
                    navController.navigate(Routes.player("file", path, name))
                }
            )
        }
        composable(
            route = Routes.VOD_LIST,
            arguments = listOf(
                navArgument("categoryId") { type = NavType.StringType },
                navArgument("categoryTitle") { type = NavType.StringType }
            )
        ) {
            // Category-level lock: an adult VOD category already asked for the PIN on the way in.
            VodListScreen(
                onBack = { navController.popBackStack() },
                onSelect = { item, categoryId ->
                    if (item.isSeries) {
                        navController.navigate(Routes.vodDetail(item, categoryId))
                    } else {
                        navController.navigate(Routes.player("vod", item.cmd, item.name))
                    }
                }
            )
        }
        composable(
            route = Routes.VOD_DETAIL,
            arguments = listOf(navArgument("payload") { type = NavType.StringType })
        ) {
            val onPlayVod: (String, String, Int) -> Unit = { cmd, title, series ->
                navController.navigate(Routes.player("vod", cmd, title, series))
            }
            if (rememberIsTv()) VodDetailScreen(onBack = { navController.popBackStack() }, onPlay = onPlayVod)
            else MobileVodDetailScreen(onBack = { navController.popBackStack() }, onPlay = onPlayVod)
        }
        composable(
            route = Routes.CHANNEL_LIST,
            arguments = listOf(
                navArgument("genreId") { type = NavType.StringType },
                navArgument("genreTitle") { type = NavType.StringType }
            )
        ) {
            // No per-channel gate here: the CHILD LOCK is category-level, so an adult genre
            // already asked for the PIN before this list could open.
            ChannelListScreen(
                onBack = { navController.popBackStack() },
                onPlay = { channel ->
                    navController.navigate(Routes.player("itv", channel.cmd, channel.name))
                }
            )
        }
        composable(
            route = Routes.PLAYER,
            arguments = listOf(
                navArgument("type") { type = NavType.StringType },
                navArgument("cmd") { type = NavType.StringType },
                navArgument("title") { type = NavType.StringType },
                navArgument("series") { type = NavType.StringType }
            )
        ) {
            if (rememberIsTv()) PlayerScreen(onBack = { navController.popBackStack() })
            else MobilePlayerScreen(onBack = { navController.popBackStack() })
        }
    }
}
