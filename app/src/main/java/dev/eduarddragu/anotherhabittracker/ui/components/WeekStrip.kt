package dev.eduarddragu.anotherhabittracker.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.eduarddragu.anotherhabittracker.domain.Cell
import dev.eduarddragu.anotherhabittracker.domain.Heatmap
import dev.eduarddragu.anotherhabittracker.theme.Motion
import java.time.LocalDate

private val INITIALS = listOf("M", "T", "W", "T", "F", "S", "S")

/**
 * This week, Monday to Sunday: done days filled, missed days grey, frozen days dashed, today outlined
 * until it's done, the days ahead as dots. On day one it's a single open square waiting for today,
 * which reads as a goal rather than as an empty chart.
 *
 * [commit] animates the day that was just logged; [pulse] (0..1) sends one ring out of an open today.
 */
@Composable
fun WeekStrip(
  cells: Map<LocalDate, Cell>,
  today: LocalDate,
  modifier: Modifier = Modifier,
  cellSize: Dp = 10.dp,
  gap: Dp = 4.dp,
  /** A missed day; defaults to a grey that works on the plain background, pass a darker one on a card. */
  missed: Color = MaterialTheme.colorScheme.outlineVariant,
  initials: Boolean = false,
  commit: CommitPlayback? = null,
  pulse: () -> Float = { 0f },
) {
  val colors = MaterialTheme.colorScheme
  val accent = colors.primary
  val muted = colors.onSurfaceVariant
  val days = Heatmap.weeks(today, 1).first()
  Column(modifier) {
    Canvas(Modifier.width(cellSize * 7 + gap * 6).height(cellSize)) {
      val size = cellSize.toPx()
      val step = size + gap.toPx()
      val radius = CornerRadius(size * 0.24f)
      days.forEachIndexed { index, day ->
        val topLeft = Offset(index * step, 0f)
        if (day == null) {
          // A day still ahead: a dot where its square will be.
          drawCircle(missed, radius = (size * 0.16f).coerceAtLeast(1.5.dp.toPx()), center = topLeft + Offset(size / 2, size / 2))
          return@forEachIndexed
        }
        val cell = cells[day] ?: Cell.Empty
        val playing = commit != null && commit.animating(day)
        val progress = if (playing) commit.fill.value else 1f
        when {
          cell is Cell.Done -> {
            if (progress < 1f) drawOpen(day == today, topLeft, size, radius, accent, missed)
            val scaled = size * (0.6f + 0.4f * progress)
            val offset = (size - scaled) / 2
            drawRoundRect(accent.copy(alpha = progress), topLeft + Offset(offset, offset), Size(scaled, scaled), CornerRadius(scaled * 0.24f))
          }
          cell is Cell.Frozen -> {
            val dash = PathEffect.dashPathEffect(floatArrayOf(size * 0.2f, size * 0.14f))
            val inset = 0.75.dp.toPx()
            drawRoundRect(muted.copy(alpha = progress), topLeft + Offset(inset, inset), Size(size - 2 * inset, size - 2 * inset), radius, style = Stroke(1.dp.toPx(), pathEffect = dash))
          }
          else -> drawOpen(day == today, topLeft, size, radius, accent, missed)
        }
        // The ring that leaves a square as it fills, or an open today asking for attention.
        val ring = if (playing) commit.ring.value else if (day == today && cell == Cell.Empty) pulse() else 0f
        if (ring > 0f && ring < 1f) {
          val grown = size * (1f + 0.35f * ring)
          val offset = (size - grown) / 2
          drawRoundRect(accent.copy(alpha = 0.5f * (1f - ring)), topLeft + Offset(offset, offset), Size(grown, grown), CornerRadius(grown * 0.24f), style = Stroke(1.5.dp.toPx()))
        }
      }
    }
    if (initials) {
      Spacer(Modifier.height(6.dp))
      Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
        INITIALS.forEachIndexed { index, letter ->
          Box(Modifier.width(cellSize), contentAlignment = Alignment.Center) {
            Text(letter, style = MaterialTheme.typography.labelSmall, color = if (days[index] == today) accent else muted)
          }
        }
      }
    }
  }
}

private fun DrawScope.drawOpen(isToday: Boolean, topLeft: Offset, size: Float, radius: CornerRadius, accent: Color, missed: Color) {
  if (isToday) {
    val stroke = 1.5.dp.toPx()
    drawRoundRect(accent, topLeft + Offset(stroke / 2, stroke / 2), Size(size - stroke, size - stroke), radius, style = Stroke(stroke))
  } else {
    drawRoundRect(missed, topLeft, Size(size, size), radius)
  }
}

/**
 * A number whose changed digits roll like an odometer: the old digit leaves upwards, the new one
 * comes in from below, right to left like a carry. Clipped, never faded, so it can't look doubled.
 */
@Composable
fun RollingNumber(value: Int, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
  val digits = value.toString()
  Row(modifier.opticalStart(digits, style)) {
    digits.forEachIndexed { index, digit ->
      val fromRight = digits.length - index
      key(fromRight) {
        AnimatedContent(
          targetState = digit,
          transitionSpec = {
            val spec = tween<IntOffset>(320, delayMillis = 40 * (fromRight - 1), easing = Motion.EaseUi)
            slideInVertically(spec) { it } togetherWith slideOutVertically(spec) { -it } using SizeTransform(clip = true) { _, _ -> tween(320, easing = Motion.EaseUi) }
          },
          modifier = Modifier.clipToBounds(),
          label = "digit",
        ) { shown ->
          Text(shown.toString(), style = style, color = color)
        }
      }
    }
  }
}

/**
 * How far each DM Sans digit's visible left edge sits from the start of its box, in ems (read from the
 * font at the weight Numerals uses). The "1" counts its stem rather than the tip of its flag, which is
 * what the eye lines up.
 */
private val DIGIT_START = floatArrayOf(0.046f, 0.12f, 0.054f, 0.049f, 0.043f, 0.065f, 0.051f, 0.028f, 0.061f, 0.060f)

/**
 * Pulls a number left by its first digit's empty side, so a big number lines up with the text under it
 * (at display sizes a "1" otherwise looks indented).
 */
@Composable
fun Modifier.opticalStart(text: String, style: TextStyle): Modifier {
  val first = text.firstOrNull()?.takeIf { it.isDigit() } ?: return this
  val shift = with(LocalDensity.current) { (style.fontSize * DIGIT_START[first - '0']).toDp() }
  return offset(x = -shift)
}
