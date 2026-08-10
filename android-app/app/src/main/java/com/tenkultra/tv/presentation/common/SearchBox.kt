package com.tenkultra.tv.presentation.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.tenkultra.tv.presentation.theme.appPalette

/** A reusable search field (mouse/touch friendly). Reports focus so a parent D-pad
 *  handler can step aside while the user types. */
@Composable
fun SearchBox(
    query: String,
    hint: String,
    onChange: (String) -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    // Selected-by-D-pad but NOT yet activated: show the highlight border WITHOUT opening the
    // keyboard. The keyboard only opens once the field actually gets focus (on OK).
    highlighted: Boolean = false
) {
    val palette = appPalette
    var focused by remember { mutableStateOf(false) }
    val active = focused || highlighted
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (active) palette.surface else palette.surface.copy(alpha = 0.7f))
            .border(
                if (active) 2.dp else 1.dp,
                if (active) palette.primary else palette.primary.copy(alpha = 0.4f),
                RoundedCornerShape(10.dp)
            )
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.Search, null,
            tint = if (active) palette.primary else palette.textSecondary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(10.dp))
        BasicTextField(
            value = query,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(color = palette.text, fontSize = 16.sp),
            cursorBrush = SolidColor(palette.primary),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Search,
                autoCorrect = false
            ),
            modifier = Modifier
                .weight(1f)
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .onFocusChanged { focused = it.isFocused; onFocusChanged(it.isFocused) },
            decorationBox = { inner ->
                if (query.isEmpty()) Text(hint, color = palette.textSecondary, fontSize = 16.sp)
                inner()
            }
        )
        if (query.isNotEmpty()) {
            Icon(
                Icons.Filled.Close, "Clear", tint = palette.textSecondary,
                modifier = Modifier.size(20.dp).onTap("clear-search") { onChange("") }
            )
        }
    }
}
