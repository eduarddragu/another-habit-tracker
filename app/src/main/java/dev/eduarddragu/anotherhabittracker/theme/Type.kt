package dev.eduarddragu.anotherhabittracker.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.eduarddragu.anotherhabittracker.R

// The type of eduarddragu.dev, bundled as variable fonts (SIL OFL 1.1, licenses in assets/licenses).
// Variation settings on resource fonts are still marked experimental.
@OptIn(ExperimentalTextApi::class)
private fun variable(resource: Int, weight: FontWeight, style: FontStyle = FontStyle.Normal) =
  Font(resource, weight, style, variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)))

// The italic is its own file: without it, FontStyle.Italic would be a synthesized slant of the upright.
private val Cormorant =
  FontFamily(
    variable(R.font.cormorant_garamond, FontWeight.Normal),
    variable(R.font.cormorant_garamond, FontWeight.Medium),
    variable(R.font.cormorant_garamond, FontWeight.SemiBold),
    variable(R.font.cormorant_garamond_italic, FontWeight.Medium, FontStyle.Italic),
    variable(R.font.cormorant_garamond_italic, FontWeight.SemiBold, FontStyle.Italic),
  )
private val DmSans = FontFamily(variable(R.font.dm_sans, FontWeight.Normal), variable(R.font.dm_sans, FontWeight.Medium), variable(R.font.dm_sans, FontWeight.SemiBold))
private val GeistMono = FontFamily(variable(R.font.geist_mono, FontWeight.Medium))

// Cormorant has a small x-height, so display sizes run larger than Material's defaults. Its default
// figures are old-style (a 0 reads as an "o", a 1 as an "I"), so every serif style asks for lining ones.
private const val LINING = "lnum"

val Typography =
  Typography(
    displaySmall = TextStyle(fontFamily = Cormorant, fontWeight = FontWeight.Normal, fontSize = 40.sp, lineHeight = 44.sp, letterSpacing = (-0.8).sp, fontFeatureSettings = LINING),
    headlineSmall = TextStyle(fontFamily = Cormorant, fontWeight = FontWeight.Medium, fontSize = 28.sp, lineHeight = 32.sp, fontFeatureSettings = LINING),
    titleLarge = TextStyle(fontFamily = Cormorant, fontWeight = FontWeight.Medium, fontSize = 24.sp, lineHeight = 28.sp, fontFeatureSettings = LINING),
    titleSmall = TextStyle(fontFamily = Cormorant, fontWeight = FontWeight.Medium, fontSize = 20.sp, lineHeight = 24.sp, fontFeatureSettings = LINING),
    titleMedium = TextStyle(fontFamily = DmSans, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp),
    bodyLarge = TextStyle(fontFamily = DmSans, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = DmSans, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = DmSans, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = DmSans, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.28.sp),
    labelMedium = TextStyle(fontFamily = GeistMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 1.6.sp),
    labelSmall = TextStyle(fontFamily = GeistMono, fontWeight = FontWeight.Medium, fontSize = 10.sp, lineHeight = 14.sp, letterSpacing = 1.2.sp),
  )

/**
 * Numbers that are read as values (stats, reminder times, the streak). The serif's figures read as text
 * at this size, so values use DM Sans. DM Sans has no tabular figures (its "1" is narrow), so a number
 * that changes on screen animates its width instead (see RollingNumber).
 */
val Numerals = TextStyle(fontFamily = DmSans, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 30.sp, letterSpacing = (-0.5).sp)

/** The page's headline number: the streak on a habit's page, the coverage in the curriculum. */
val NumeralsDisplay = Numerals.copy(fontSize = 64.sp, lineHeight = 64.sp, letterSpacing = (-2).sp)

/** The streak on a home card. */
val NumeralsLarge = Numerals.copy(fontSize = 34.sp, lineHeight = 34.sp)

/** Stats, the score squares, a total next to a big number. */
val NumeralsSmall = Numerals.copy(fontSize = 22.sp, lineHeight = 26.sp)

/** A date heading an entry (the Recent timeline): mono like the labels, but sized to sit with body text. */
val DateLabel = TextStyle(fontFamily = GeistMono, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 1.4.sp)
