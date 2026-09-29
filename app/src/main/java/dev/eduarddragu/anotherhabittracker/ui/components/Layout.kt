package dev.eduarddragu.anotherhabittracker.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Horizontal gutter of every screen. */
val Gutter = 20.dp

fun Modifier.gutter(): Modifier = padding(horizontal = Gutter)

/**
 * Content padding for scrolling screens, edge to edge: the list itself fills the window and scrolls
 * behind the status and gesture bars, while its first and last items stay clear of them. Padding the
 * container instead would leave a dead band at the top and stop the scroll short of the bottom.
 */
@Composable
fun screenPadding(horizontal: Dp = Gutter, top: Dp = 16.dp, bottom: Dp = 24.dp): PaddingValues {
  val insets = WindowInsets.safeDrawing.asPaddingValues()
  val direction = LocalLayoutDirection.current
  return PaddingValues(
    start = insets.calculateStartPadding(direction) + horizontal,
    top = insets.calculateTopPadding() + top,
    end = insets.calculateEndPadding(direction) + horizontal,
    bottom = insets.calculateBottomPadding() + bottom,
  )
}
