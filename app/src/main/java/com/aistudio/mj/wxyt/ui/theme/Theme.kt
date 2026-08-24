package com.aistudio.mj.wxyt.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
      primary = SoftBlue,
      secondary = DeepPurple,
      tertiary = GlowingPink,
      background = AmoledBlack,
      surface = DarkSurface,
      onPrimary = LightText,
      onSecondary = LightText,
      onTertiary = LightText,
      onBackground = LightText,
      onSurface = LightText
  )

private val LightColorScheme = lightColorScheme(
  primary = SoftBlue,
  secondary = DeepPurple,
  tertiary = GlowingPink,
  background = androidx.compose.ui.graphics.Color.White,
  surface = androidx.compose.ui.graphics.Color(0xFFF7F7FA),
  onPrimary = androidx.compose.ui.graphics.Color.White,
  onSecondary = androidx.compose.ui.graphics.Color.White,
  onTertiary = androidx.compose.ui.graphics.Color.White,
  onBackground = androidx.compose.ui.graphics.Color(0xFF111116),
  onSurface = androidx.compose.ui.graphics.Color(0xFF111116)
)

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true,
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val context = LocalContext.current
  val useDynamic = dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
  val colorScheme = when {
    useDynamic && darkTheme -> dynamicDarkColorScheme(context)
    useDynamic -> dynamicLightColorScheme(context)
    darkTheme -> DarkColorScheme
    else -> LightColorScheme
  }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
