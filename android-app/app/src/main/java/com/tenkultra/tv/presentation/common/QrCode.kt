package com.tenkultra.tv.presentation.common

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import androidx.compose.ui.graphics.Color as ComposeColor

/**
 * Renders a QR code generated ON-DEVICE (via the bundled zxing library), so pairing/connect QRs
 * appear INSTANTLY and work even with no internet or a cold backend — the reason the old
 * backend-image QR box (`/api/qr`) sometimes showed up blank. [data] is the string encoded into
 * the QR (a setup URL, an tenkultra:// connect link, …).
 */
@Composable
fun QrCode(
    data: String,
    sizeDp: Dp,
    modifier: Modifier = Modifier,
    dark: ComposeColor = ComposeColor.Black,
    light: ComposeColor = ComposeColor.White
) {
    // Cheap to encode; keyed on the data + colours so it only re-encodes when they change.
    val bitmap = remember(data, dark, light) {
        runCatching { encodeQr(data, 512, dark.toArgb(), light.toArgb()) }.getOrNull()
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "QR code",
            contentScale = ContentScale.Fit,
            modifier = modifier
        )
    }
}

private fun encodeQr(data: String, px: Int, darkArgb: Int, lightArgb: Int): android.graphics.Bitmap {
    val hints = mapOf(
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
        EncodeHintType.MARGIN to 1
    )
    val matrix = QRCodeWriter().encode(data, BarcodeFormat.QR_CODE, px, px, hints)
    val w = matrix.width
    val h = matrix.height
    val pixels = IntArray(w * h)
    for (y in 0 until h) {
        val row = y * w
        for (x in 0 until w) {
            pixels[row + x] = if (matrix[x, y]) darkArgb else lightArgb
        }
    }
    return android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888).apply {
        setPixels(pixels, 0, w, 0, 0, w, h)
    }
}
