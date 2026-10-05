package com.jsnu.laundry.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val TealPrimary = Color(0xFF0E9384)
val TealDark = Color(0xFF0B7A6E)
val MintBg = Color(0xFFF3FAF8)

private val LightColors = lightColorScheme(
    primary = TealPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC6F1EA),
    onPrimaryContainer = Color(0xFF053B35),
    secondary = Color(0xFF5B6B7C),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE1E9F1),
    onSecondaryContainer = Color(0xFF1C2834),
    background = MintBg,
    onBackground = Color(0xFF16201E),
    surface = Color.White,
    onSurface = Color(0xFF16201E),
    surfaceVariant = Color(0xFFE9F2F0),
    onSurfaceVariant = Color(0xFF45564F),
    error = Color(0xFFD64545),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF5CD6C8),
    onPrimary = Color(0xFF053B35),
    primaryContainer = Color(0xFF0B5A51),
    onPrimaryContainer = Color(0xFFC6F1EA),
    secondary = Color(0xFF9FB4C7),
    background = Color(0xFF101615),
    onBackground = Color(0xFFDCE7E3),
    surface = Color(0xFF17201E),
    onSurface = Color(0xFFDCE7E3),
    surfaceVariant = Color(0xFF23302D),
    onSurfaceVariant = Color(0xFFA6B8B2),
    error = Color(0xFFFF8A8A),
)

/** 业务状态配色（与网页版一致） */
object StatusColors {
    val idle = Color(0xFF2E9E5B)
    val reservable = Color(0xFF0E9384)
    val taken = Color(0xFF8A919C)
    val locked = Color(0xFF5B6B7C)
    val fault = Color(0xFFD64545)
    val unknown = Color(0xFF9AA3AD)
}

@Composable
fun JSNULaundryTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
