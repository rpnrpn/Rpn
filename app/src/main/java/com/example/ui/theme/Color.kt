package com.example.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Sleek M3 Primary brand palette
val Purple80 = Color(0xFFFF007F) // Neon Synthwave Pink
val PurpleGrey80 = Color(0xFF8A2BE2) // Neon Synthwave Purple
val Pink80 = Color(0xFFFF5E00) // Neon Synthwave Orange

val Purple40 = Color(0xFF6750A4) // M3 Primary Purple
val PurpleGrey40 = Color(0xFF625B71)
val Pink40 = Color(0xFF7D5260)

// Data structure holding all custom colors for flexible theme support
data class AppColors(
    val background: Color,
    val card: Color,
    val border: Color,
    val textDark: Color,
    val textMedium: Color,
    val highlightCard: Color,
    val highlightBorder: Color,
    val highlightText: Color,
    val secondaryContainer: Color,
    val bottomNavBackground: Color
)

// The Cyber Obsidian theme (Dark Mode)
val CyberObsidianColors = AppColors(
    background = Color(0xFF0B0813), // Cosmic obsidian/violet midnight canvas
    card = Color(0xFF141026),       // Deep space card background
    border = Color(0xFF251E3D),     // Tech cosmic border
    textDark = Color(0xFFF8FAFC),   // Crisp silver white text
    textMedium = Color(0xFF9E95B6), // Cosmic lavender slate text
    highlightCard = Color(0x26FF007F), // Soft glowing magenta background
    highlightBorder = Color(0xFFFF007F), // Neon magenta glow border
    highlightText = Color(0xFFFF007F),  // High-contrast neon magenta text
    secondaryContainer = Color(0xFF251E3D), // Cosmic capsule badge background
    bottomNavBackground = Color(0xFF0C0A14) // Midnight status strip
)

// The Neo Steel theme (Light Mode)
val NeoSteelColors = AppColors(
    background = Color(0xFFF1F5F9), // Soft modern slate background
    card = Color(0xFFFFFFFF),       // Clean white cards
    border = Color(0xFFE2E8F0),     // Clean light gray border
    textDark = Color(0xFF0F172A),   // Deep slate primary text
    textMedium = Color(0xFF475569), // Muted slate secondary text
    highlightCard = Color(0xFFECFEFF), // Soft cyan glowing background
    highlightBorder = Color(0xFF06B6D4), // Cool cyan accent border
    highlightText = Color(0xFF0891B2),  // Deep cyan readable text
    secondaryContainer = Color(0xFFE2E8F0), // Clean light badge background
    bottomNavBackground = Color(0xFFF8FAFC) // Sleek bottom tab strip
)

// Static CompositionLocal for modern Material 3 Jetpack Compose styling
val LocalAppColors = staticCompositionLocalOf { CyberObsidianColors }

// Composable property getters to seamlessly wire existing layouts with dynamic colors
val SlateDarkBackground: Color
    @Composable
    get() = LocalAppColors.current.background

val SlateDarkCard: Color
    @Composable
    get() = LocalAppColors.current.card

val SlateBorder: Color
    @Composable
    get() = LocalAppColors.current.border

val SleekTextDark: Color
    @Composable
    get() = LocalAppColors.current.textDark

val SleekTextMedium: Color
    @Composable
    get() = LocalAppColors.current.textMedium

val SleekHighlightCard: Color
    @Composable
    get() = LocalAppColors.current.highlightCard

val SleekHighlightBorder: Color
    @Composable
    get() = LocalAppColors.current.highlightBorder

val SleekHighlightText: Color
    @Composable
    get() = LocalAppColors.current.highlightText

val SleekSecondaryContainer: Color
    @Composable
    get() = LocalAppColors.current.secondaryContainer

val SleekBottomNavBackground: Color
    @Composable
    get() = LocalAppColors.current.bottomNavBackground

// Signal Strength Colors mapped to electric neon indicators (consistent across themes for precision diagnostics)
val SignalExcellent = Color(0xFF00FF9D)     // Electric Neon Green (Superb: >= -55 dBm)
val SignalGood = Color(0xFF00F0FF)          // Radiant Electric Cyan (Solid: >= -68 dBm)
val SignalFair = Color(0xFFFFD600)          // Bright Laser Yellow (Fair: >= -82 dBm)
val SignalWeak = Color(0xFFFF3366)          // Vivid Hot Crimson (Weak: < -82 dBm)



