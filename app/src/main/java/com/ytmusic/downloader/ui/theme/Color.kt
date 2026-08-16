package com.ytmusic.downloader.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Premium iOS & Glassmorphism Palette
val iOSBlack = Color(0xFF000000)
val DarkBackground = Color(0xFF090A0D)
val DarkSurface = Color(0xFF13141A)
val DarkCard = Color(0xFF1C1D24)
val DarkCardElevated = Color(0xFF262833)

// Translucent Glass Surfaces
val GlassSurface = Color(0x88181922)
val GlassCard = Color(0x77232530)
val GlassCardHover = Color(0x992B2D3A)
val GlassBorder = Color(0x25FFFFFF)
val GlassBorderSubtle = Color(0x14FFFFFF)
val GlassBorderHighlight = Color(0x45FFFFFF)

// Apple Music Vibrant Red Accents
val AccentRed = Color(0xFFFA243C)
val AccentRedGradient = Color(0xFFFF4565)
val AccentPink = Color(0xFFFE3C72)
val AccentBlue = Color(0xFF0A84FF)
val AccentGreen = Color(0xFF30D158)

val AppleGradient = Brush.horizontalGradient(
    listOf(AccentRed, AccentRedGradient)
)

val GlassGradient = Brush.verticalGradient(
    listOf(Color(0x35FFFFFF), Color(0x05FFFFFF))
)

val AmbientGlowGradient = Brush.radialGradient(
    listOf(Color(0x33FA243C), Color(0x00000000))
)

// iOS Typography Colors
val TextPrimary = Color(0xFFFFFFFF)
val TextSecondary = Color(0xFF9898A0)
val TextTertiary = Color(0xFF636366)

val BadgeBackground = Color(0x40FFFFFF)
val BorderSubtle = Color(0x1AFFFFFF)
