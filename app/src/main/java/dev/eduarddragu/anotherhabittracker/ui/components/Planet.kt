package dev.eduarddragu.anotherhabittracker.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.preferredFrameRate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The butter disc of the planet mark, borrowed from a sibling project's hero. */
private val Butter = Color(0xFFF2C94C)

/**
 * A small planet: a butter disc with a tilted ring. Geometry at rest: the ring is 118% of the disc
 * wide and 32% of it tall, tilted -18 degrees, drawn in the ink colour at 60%.
 *
 * [settle] (0..1) is the entrance: the disc grows from 94% and fades in while the ring turns from
 * -32 to -18 degrees and grows from 94%. Once settled, the idle loop moves it: [drift] adds degrees
 * to the ring's tilt, [squash] scales its height (the ring opening and closing as it turns) and
 * [breathe] scales the disc. Everything happens at draw time.
 */
@Composable
fun Planet(
  modifier: Modifier = Modifier,
  disc: Dp = 64.dp,
  settle: () -> Float = { 1f },
  drift: () -> Float = { 0f },
  squash: () -> Float = { 1f },
  breathe: () -> Float = { 1f },
) {
  val ink = MaterialTheme.colorScheme.onSurface
  // Its own layer, so each frame redraws only the planet and not the screen around it; 30 fps is plenty
  // for a loop this slow and lets the display drop from 120 Hz. Wide and tall enough for the ring at
  // its widest swing.
  Canvas(modifier.graphicsLayer().preferredFrameRate(30f).size(width = disc * 1.3f, height = disc * 1.1f)) {
    val p = settle()
    val d = disc.toPx()
    val center = Offset(size.width / 2, size.height / 2)
    val grow = 0.94f + 0.06f * p
    drawCircle(Butter.copy(alpha = p), radius = d / 2 * grow * breathe(), center = center)
    val ringWidth = d * 1.18f * grow
    val ringHeight = d * 0.32f * grow * squash()
    rotate(-32f + 14f * p + drift(), pivot = center) {
      drawOval(
        ink.copy(alpha = 0.6f * p),
        topLeft = Offset(center.x - ringWidth / 2, center.y - ringHeight / 2),
        size = Size(ringWidth, ringHeight),
        style = Stroke(width = (d / 55f).coerceAtLeast(1.5.dp.toPx())),
      )
    }
  }
}
