package dev.eduarddragu.anotherhabittracker.theme

import androidx.compose.ui.graphics.Color

// Palette from eduarddragu.dev. Shared by the Compose theme and the widget.
internal object Light {
  val background = Color(0xFFFAF8F5)
  val subtle = Color(0xFFF0EBE3)
  val text = Color(0xFF1A1714)
  val muted = Color(0xFF5A4E42)
  val border = Color(0xFFE2DBD2)
  val borderStrong = Color(0xFFC9BFB4)
  // A touch deeper than the site's #C14F1E: small accent text on the tonal cards needs 4.5:1.
  val accent = Color(0xFFB04619)
  val accentContainer = Color(0xFFF7E5DC)
}

internal object Dark {
  val background = Color(0xFF1A1714)
  val subtle = Color(0xFF2A2420)
  val text = Color(0xFFFAF8F5)
  val muted = Color(0xFFB3A597)
  val border = Color(0xFF342C26)
  val borderStrong = Color(0xFF4A3F36)
  // A touch lighter than the site's #D9632F, for the same reason on the dark cards.
  val accent = Color(0xFFE8743F)
  val accentContainer = Color(0xFF422518)
}
