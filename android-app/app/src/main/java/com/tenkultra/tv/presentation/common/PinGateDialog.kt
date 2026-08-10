package com.tenkultra.tv.presentation.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.tenkultra.tv.presentation.theme.appPalette
import kotlinx.coroutines.launch

/** Asks for the parental PIN before opening adult content. Calls [onSuccess] on a match. */
@Composable
fun PinGateDialog(
    title: String = "Adult content locked",
    onVerify: suspend (String) -> Boolean,
    onSuccess: () -> Unit,
    onCancel: () -> Unit
) {
    val palette = appPalette
    val scope = rememberCoroutineScope()
    var pin by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    val fieldFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { fieldFocus.requestFocus() } }

    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xCC000000)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(420.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(palette.surface)
                .border(1.dp, palette.primary, RoundedCornerShape(14.dp))
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Lock, null, tint = palette.primary, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Text(title, color = palette.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            Text("Enter the parental PIN to continue.", color = palette.textSecondary, fontSize = 14.sp)
            Spacer(Modifier.height(16.dp))
            BasicTextField(
                value = pin,
                onValueChange = { pin = it.filter(Char::isDigit).take(4); message = null },
                singleLine = true,
                textStyle = TextStyle(color = palette.text, fontSize = 24.sp, fontWeight = FontWeight.Bold),
                cursorBrush = SolidColor(palette.primary),
                visualTransformation = PasswordVisualTransformation('●'),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                modifier = Modifier
                    .width(180.dp).height(54.dp)
                    .focusRequester(fieldFocus)
                    .clip(RoundedCornerShape(8.dp))
                    .background(palette.background)
                    .border(1.dp, palette.primary, RoundedCornerShape(8.dp))
                    .padding(horizontal = 16.dp),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (pin.isEmpty()) Text("● ● ● ●", color = palette.textSecondary, fontSize = 20.sp)
                        inner()
                    }
                }
            )
            message?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, color = Color(0xFFFF8A80), fontSize = 14.sp)
            }
            Spacer(Modifier.height(22.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Button(
                    onClick = {
                        if (pin.length != 4) { message = "Enter the 4-digit PIN"; return@Button }
                        scope.launch {
                            if (onVerify(pin)) onSuccess() else { message = "Wrong PIN"; pin = "" }
                        }
                    },
                    modifier = Modifier.weight(1f).height(48.dp)
                ) { Text("Unlock", fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
                Button(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f).height(48.dp)
                ) { Text("Cancel", fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}
