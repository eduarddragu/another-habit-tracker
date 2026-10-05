package dev.eduarddragu.anotherhabittracker.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.eduarddragu.anotherhabittracker.domain.Cell
import dev.eduarddragu.anotherhabittracker.domain.Descriptions
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.ceil

/**
 * Accent alpha for levels 1..4. Starts at 0.40 so the lightest logged day stays distinguishable from
 * an empty one (0.28 gave barely 1.1:1 against the empty-cell color).
 */
internal val LEVEL_ALPHA = floatArrayOf(0f, 0.40f, 0.60f, 0.80f, 1f)

private val WEEKDAY_LABELS = mapOf(0 to "MON", 2 to "WED", 4 to "FRI")

/**
 * GitHub-style grid: one column per week (Monday on top), one square per day. Frozen days are dashed,
 * today is always outlined, future days are left blank. It doesn't animate: the week strip above it
 * plays the moment a day is logged.
 */
@Composable
fun HeatmapGrid(
  weeks: List<List<LocalDate?>>,
  cells: Map<LocalDate, Cell>,
  today: LocalDate,
  cellSize: Dp,
  gap: Dp,
  modifier: Modifier = Modifier,
  /** Days off among the dashed days, so the description can tell them from freezes. */
  daysOff: Set<LocalDate> = emptySet(),
) {
  val accent = MaterialTheme.colorScheme.primary
  val empty = MaterialTheme.colorScheme.surfaceContainerHighest
  val muted = MaterialTheme.colorScheme.onSurfaceVariant
  val todayOutline = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)

  val todayDone = cells[today] is Cell.Done

  // A picture of the history: a screen reader gets its gist in one sentence instead of month labels.
  val description = remember(cells, daysOff, today) { Descriptions.heatmap(cells, daysOff, today) }
  Row(modifier.clearAndSetSemantics { contentDescription = description }) {
    Column(Modifier.width(30.dp)) {
      Spacer(Modifier.height(18.dp))
      Column(verticalArrangement = Arrangement.spacedBy(gap)) {
        repeat(7) { row ->
          Box(Modifier.height(cellSize), contentAlignment = Alignment.CenterStart) {
            WEEKDAY_LABELS[row]?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = muted, maxLines = 1) }
          }
        }
      }
    }
    Column {
      MonthLabels(weeks, cellSize, gap)
      Spacer(Modifier.height(4.dp))
      Canvas(Modifier.width(cellSize * weeks.size + gap * (weeks.size - 1)).height(cellSize * 7 + gap * 6)) {
        val size = cellSize.toPx()
        val step = size + gap.toPx()
        val radius = CornerRadius(size * 0.22f)
        val dash = PathEffect.dashPathEffect(floatArrayOf(size * 0.18f, size * 0.14f))
        val outline = Stroke(width = 1.5.dp.toPx())
        val inset = 0.75.dp.toPx()
        weeks.forEachIndexed { col, week ->
          week.forEachIndexed { row, day ->
            if (day == null) return@forEachIndexed
            val topLeft = Offset(col * step, row * step)
            val area = Size(size, size)
            when (val cell = cells[day] ?: Cell.Empty) {
              is Cell.Done -> drawRoundRect(accent.copy(alpha = LEVEL_ALPHA[cell.level]), topLeft, area, radius)
              Cell.Frozen -> {
                drawRoundRect(empty, topLeft, area, radius)
                drawRoundRect(muted, topLeft, area, radius, style = Stroke(width = 1.dp.toPx(), pathEffect = dash))
              }
              Cell.Empty -> if (day != today) drawRoundRect(empty, topLeft, area, radius)
            }
            if (day == today) {
              val color = if (todayDone) todayOutline else accent
              drawRoundRect(color, topLeft + Offset(inset, inset), Size(size - 2 * inset, size - 2 * inset), radius, style = outline)
            }
          }
        }
      }
    }
  }
}

@Composable
private fun MonthLabels(weeks: List<List<LocalDate?>>, cellSize: Dp, gap: Dp) {
  // Label a column when its Monday starts a month that differs from the previous column. The first
  // column is labelled only if it holds the start of its month, so a sliver of a month can't crowd out
  // the next one.
  val labels =
    weeks.mapIndexed { index, week ->
      val first = week.firstOrNull() ?: return@mapIndexed null
      val previous = weeks.getOrNull(index - 1)?.firstOrNull()
      val starts = if (previous == null) first.dayOfMonth <= 7 else previous.month != first.month
      if (starts) first.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).uppercase() else null
    }
  // A label needs about 30dp: three small columns, or a single large one.
  val span = ceil(30f / (cellSize + gap).value).toInt().coerceAtLeast(1)
  Row(Modifier.height(14.dp)) {
    var skip = 0
    labels.forEachIndexed { index, label ->
      val columnWidth = if (index == labels.lastIndex) cellSize else cellSize + gap
      when {
        skip > 0 -> skip--
        label != null && index <= labels.size - span -> {
          // A label spans enough columns that short months don't overlap.
          Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.width((cellSize + gap) * span),
          )
          skip = span - 1
        }
        else -> Spacer(Modifier.width(columnWidth))
      }
    }
  }
}

/** Legend row: "Less ▢▢▢▢▢ More". */
@Composable
fun HeatmapLegend(modifier: Modifier = Modifier) {
  val cellSize = 10.dp
  val accent = MaterialTheme.colorScheme.primary
  val empty = MaterialTheme.colorScheme.surfaceContainerHighest
  Row(modifier, verticalAlignment = Alignment.CenterVertically) {
    Text("LESS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.width(6.dp))
    Canvas(Modifier.width(cellSize * 5 + 3.dp * 4).height(cellSize)) {
      val size = cellSize.toPx()
      val step = size + 3.dp.toPx()
      (0..4).forEach { level ->
        val color = if (level == 0) empty else accent.copy(alpha = LEVEL_ALPHA[level])
        drawRoundRect(color, Offset(level * step, 0f), Size(size, size), CornerRadius(size * 0.22f))
      }
    }
    Spacer(Modifier.width(6.dp))
    Text("MORE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}
