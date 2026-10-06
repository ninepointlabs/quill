package com.ninepointlabs.quill.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = GoldAccent,
    onPrimary = CypressBlack,
    primaryContainer = DarkTealGreen,
    onPrimaryContainer = GoldAccent,
    background = CypressBlack,
    onBackground = Parchment,
    surface = CardSurface,
    onSurface = Parchment,
    surfaceVariant = CardSurface,
    onSurfaceVariant = SageGreenGray,
    outline = DarkTealGreen,
    error = ErrorRed
)

@Composable
fun QuillTheme(
  darkTheme: Boolean = true,
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme = DarkColorScheme

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
