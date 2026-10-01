package app.edroid.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Default = Typography()

/**
 * Material 3 type scale with a slightly heavier, tighter display/headline treatment, which is
 * the characteristic "expressive" voice for large flexible app bars and empty states.
 */
val EDroidTypography = Typography(
    displayLarge = Default.displayLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
    displayMedium = Default.displayMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.25).sp),
    displaySmall = Default.displaySmall.copy(fontWeight = FontWeight.SemiBold),
    headlineLarge = Default.headlineLarge.copy(fontWeight = FontWeight.SemiBold),
    headlineMedium = Default.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
    headlineSmall = Default.headlineSmall.copy(fontWeight = FontWeight.Medium),
    titleLarge = Default.titleLarge.copy(fontWeight = FontWeight.Medium),
    titleMedium = Default.titleMedium.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium),
    bodyLarge = Default.bodyLarge,
    bodyMedium = Default.bodyMedium,
    labelLarge = Default.labelLarge,
    labelMedium = Default.labelMedium,
    labelSmall = Default.labelSmall,
)
