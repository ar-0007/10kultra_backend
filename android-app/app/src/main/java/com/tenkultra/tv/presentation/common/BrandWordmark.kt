package com.tenkultra.tv.presentation.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.tenkultra.tv.R

/**
 * The 10K Ultra wordmark: the real gold logo art (`drawable-nodpi/logo.png`, background already
 * keyed out) so it sits on any screen colour, with a soft gold bloom behind it that reproduces
 * the glow of the original render at TV viewing distance.
 *
 * The whole mark scales off the caller's width — the art is 1024px wide, so it stays crisp on 4K.
 */
@Composable
fun BrandWordmark(
    modifier: Modifier = Modifier,
    glow: Boolean = true,
) {
    val logo = painterResource(R.drawable.logo)
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (glow) {
            Image(
                painter = logo,
                contentDescription = null,
                contentScale = ContentScale.FillWidth,
                modifier = Modifier
                    .fillMaxWidth()
                    .scale(1.06f)
                    .blur(26.dp)
            )
        }
        Image(
            painter = logo,
            contentDescription = "10K Ultra",
            contentScale = ContentScale.FillWidth,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
