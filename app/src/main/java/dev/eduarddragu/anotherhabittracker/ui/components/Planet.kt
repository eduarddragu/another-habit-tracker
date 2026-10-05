package dev.eduarddragu.anotherhabittracker.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.preferredFrameRate
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.lerp
import kotlin.math.cos
import kotlin.math.sin
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
 *
 * As a timer ([morph] 0 to 1): the disc grows to [timerDisc], the ring stops swinging and opens into a
 * flat circle (a faint track), and an accent arc shows how much is [left], grey while [paused].
 */
@Composable
fun Planet(
  modifier: Modifier = Modifier,
  disc: Dp = 64.dp,
  settle: () -> Float = { 1f },
  drift: () -> Float = { 0f },
  squash: () -> Float = { 1f },
  breathe: () -> Float = { 1f },
  morph: () -> Float = { 0f },
  left: () -> Float = { 1f },
  paused: () -> Boolean = { false },
  closed: () -> Float = { 0f },
  timerDisc: Dp = 168.dp,
) {
  val ink = MaterialTheme.colorScheme.onSurface
  val accent = MaterialTheme.colorScheme.primary
  // Its own layer, so each frame redraws only the planet and not the screen around it; 30 fps is plenty
  // for a loop this slow and lets the display drop from 120 Hz. Wide and tall enough for the ring at
  // its widest swing, and for the timer's full circle once it has opened.
  Spacer(
    modifier
      .graphicsLayer()
      .preferredFrameRate(30f)
      .layout { measurable, constraints ->
        val t = morph()
        val d = lerp(disc, timerDisc, t)
        val width = (d * 1.3f).roundToPx()
        val height = (d * androidx.compose.ui.util.lerp(1.1f, 1.3f, t)).roundToPx()
        val placeable = measurable.measure(Constraints.fixed(width, height))
        layout(width, height) { placeable.place(0, 0) }
      }
      // The idle loop draws 30 times a second for as long as Home is up: the strokes are made once (per
      // size), and the ring's only again when its width actually changes (during the morph).
      .drawWithCache {
        val arcStroke = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
        val closedStroke = Stroke(width = 3.dp.toPx())
        val thinnest = 1.5.dp.toPx()
        val bead = 4.5.dp.toPx()
        var ringStroke = Stroke(width = thinnest)
        onDrawBehind {
          val p = settle()
          val t = morph()
          val d = lerp(disc, timerDisc, t).toPx()
          val center = Offset(size.width / 2, size.height / 2)
          val grow = 0.94f + 0.06f * p
          // The idle loop fades out as the planet becomes a timer, so nothing jumps when it starts.
          drawCircle(Butter.copy(alpha = p), radius = d / 2 * grow * androidx.compose.ui.util.lerp(breathe(), 1f, t), center = center)
          val ringWidth = d * androidx.compose.ui.util.lerp(1.18f, 1.22f, t) * grow
          // The ring opens into a full circle: the track of the timer.
          val ringHeight = androidx.compose.ui.util.lerp(d * 0.32f * grow * squash(), ringWidth, t)
          val topLeft = Offset(center.x - ringWidth / 2, center.y - ringHeight / 2)
          val ringSize = Size(ringWidth, ringHeight)
          val ringLine = androidx.compose.ui.util.lerp((d / 55f).coerceAtLeast(thinnest), thinnest, t)
          if (ringStroke.width != ringLine) ringStroke = Stroke(width = ringLine)
          rotate(androidx.compose.ui.util.lerp(-32f + 14f * p + drift(), 0f, t), pivot = center) {
            drawOval(ink.copy(alpha = androidx.compose.ui.util.lerp(0.6f, 0.18f, t) * p), topLeft = topLeft, size = ringSize, style = ringStroke)
            // Done: the ring closes in the accent, once.
            val c = closed()
            if (c > 0f) drawOval(accent.copy(alpha = 0.6f * c * t), topLeft = topLeft, size = ringSize, style = closedStroke)
            if (t > 0f) {
              // What's left, from 12 o'clock clockwise, draining back towards it; a bead at its head.
              val color = if (paused()) ink.copy(alpha = 0.4f * t) else accent.copy(alpha = t)
              val sweep = 360f * left().coerceIn(0f, 1f)
              drawArc(color, startAngle = -90f, sweepAngle = sweep, useCenter = false, topLeft = topLeft, size = ringSize, style = arcStroke)
              if (sweep > 0f) {
                val angle = Math.toRadians((-90f + sweep).toDouble())
                drawCircle(color, radius = bead, center = Offset(center.x + ringWidth / 2 * cos(angle).toFloat(), center.y + ringHeight / 2 * sin(angle).toFloat()))
              }
            }
          }
        }
      }
  )
}
