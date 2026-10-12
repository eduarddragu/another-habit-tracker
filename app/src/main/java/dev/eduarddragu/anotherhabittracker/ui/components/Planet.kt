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
import androidx.compose.ui.graphics.drawscope.DrawScope
import dev.eduarddragu.anotherhabittracker.domain.Orbit

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
 *
 * [moons] (see Orbit) circle the disc on flat orbits tilted with the ring, farther out and slower the
 * bigger they are, passing behind the disc on the upper half. [orbit] (0..1, looping) moves them, and
 * [grown] (0..1) brings today's grown moons from their last stage to the new one. They fade out as the
 * planet becomes a timer. Drawn past the planet's bounds (the layer doesn't clip), so the layout and
 * the cards under it don't move.
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
  moons: List<Orbit.Moon> = emptyList(),
  orbit: () -> Float = { 0f },
  grown: () -> Float = { 1f },
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
        val moonRing = Stroke(width = thinnest)
        onDrawBehind {
          val p = settle()
          val t = morph()
          val d = lerp(disc, timerDisc, t).toPx()
          val center = Offset(size.width / 2, size.height / 2)
          val grow = 0.94f + 0.06f * p
          val ringWidth = d * androidx.compose.ui.util.lerp(1.18f, 1.22f, t) * grow
          // The ring opens into a full circle: the track of the timer.
          val ringHeight = androidx.compose.ui.util.lerp(d * 0.32f * grow * squash(), ringWidth, t)
          val topLeft = Offset(center.x - ringWidth / 2, center.y - ringHeight / 2)
          val ringSize = Size(ringWidth, ringHeight)
          val ringLine = androidx.compose.ui.util.lerp((d / 55f).coerceAtLeast(thinnest), thinnest, t)
          if (ringStroke.width != ringLine) ringStroke = Stroke(width = ringLine)
          rotate(androidx.compose.ui.util.lerp(-32f + 14f * p + drift(), 0f, t), pivot = center) {
            val moonAlpha = p * (1f - t)
            if (moonAlpha > 0f) drawMoons(moons, d, center, orbit(), grown(), ink, moonAlpha, moonRing, back = true)
            // The idle loop fades out as the planet becomes a timer, so nothing jumps when it starts.
            drawCircle(Butter.copy(alpha = p), radius = d / 2 * grow * androidx.compose.ui.util.lerp(breathe(), 1f, t), center = center)
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
            if (moonAlpha > 0f) drawMoons(moons, d, center, orbit(), grown(), ink, moonAlpha, moonRing, back = false)
          }
        }
      }
  )
}

/**
 * The moons on one side of the disc: [back] for the upper half of their orbits (drawn before the disc,
 * so it covers them), else the lower half. Stage 1 sits just outside the ring, each stage a little
 * farther and bigger; from 30 days (stage 4) a moon gets a small ring of its own. Orbit periods are
 * whole fractions of the [orbit] loop, so it wraps without a jump.
 */
private fun DrawScope.drawMoons(moons: List<Orbit.Moon>, d: Float, center: Offset, orbit: Float, grow: Float, ink: Color, alpha: Float, ring: Stroke, back: Boolean) {
  moons.forEachIndexed { i, moon ->
    // A moon that grew today moves up from its last stage; a newborn one fades in from nothing.
    val stage = if (moon.grewToday) moon.stage - 1 + grow else moon.stage.toFloat()
    val born = if (moon.grewToday && moon.stage == 1) grow else 1f
    val a = d * (0.82f + 0.035f * (stage - 1f).coerceAtLeast(0f))
    val turns = 9 - moon.stage
    val angle = 2.0 * Math.PI * (orbit * turns + i * 0.382f)
    val sinA = sin(angle).toFloat()
    if ((sinA < 0f) != back) return@forEachIndexed
    val at = Offset(center.x + a * cos(angle).toFloat(), center.y + a * 0.3f * sinA)
    val r = d * (0.045f + 0.009f * (stage - 1f).coerceAtLeast(0f)) * born
    val color = ink.copy(alpha = alpha * born * if (moon.fading) 0.25f else 0.75f)
    drawCircle(color, radius = r, center = at)
    if (moon.stage >= 4) drawOval(color, topLeft = Offset(at.x - r * 1.6f, at.y - r * 0.45f), size = Size(r * 3.2f, r * 0.9f), style = ring)
  }
}
