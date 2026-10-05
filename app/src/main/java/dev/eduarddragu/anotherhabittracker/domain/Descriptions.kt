package dev.eduarddragu.anotherhabittracker.domain

import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * What the drawn parts of a screen (the week strip, the heatmap, a rolling streak) say to a screen
 * reader, in one sentence each. Plain text, built here so it can be tested.
 */
object Descriptions {
  /**
   * "This week: Monday done, Tuesday frozen, Wednesday day off, today open, 4 days ahead". A past day
   * with nothing on it is missed. [daysOff] tells a day off from a freeze (both are dashed squares).
   */
  fun week(cells: Map<LocalDate, Cell>, daysOff: Set<LocalDate>, today: LocalDate): String {
    val days = Heatmap.weeks(today, 1).first()
    val parts =
      days.filterNotNull().map { day ->
        val name = if (day == today) "today" else day.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
        "$name ${state(cells[day], day in daysOff, today = day == today)}"
      }
    val ahead = days.count { it == null }
    val tail = if (ahead > 0) listOf(if (ahead == 1) "1 day ahead" else "$ahead days ahead") else emptyList()
    return "This week: " + (parts + tail).joinToString(", ")
  }

  /** "Done on 23 of the last 30 days, 2 frozen, 1 off": the heatmap's gist, today included. */
  fun heatmap(cells: Map<LocalDate, Cell>, daysOff: Set<LocalDate>, today: LocalDate, days: Int = 30): String {
    val window = (0 until days).map { today.minusDays(it.toLong()) }
    val done = window.count { cells[it] is Cell.Done }
    val frozen = window.count { cells[it] == Cell.Frozen && it !in daysOff }
    val off = window.count { cells[it] == Cell.Frozen && it in daysOff }
    return listOfNotNull(
        "Done on $done of the last $days days",
        frozen.takeIf { it > 0 }?.let { "$it frozen" },
        off.takeIf { it > 0 }?.let { "$it off" },
      )
      .joinToString(", ")
  }

  /** "12 days in a row", the streak number and its caption read as one. */
  fun streak(days: Int): String = "${dayCount(days)} in a row"

  private fun state(cell: Cell?, off: Boolean, today: Boolean): String =
    when {
      cell is Cell.Done -> "done"
      cell == Cell.Frozen && off -> "day off"
      cell == Cell.Frozen -> "frozen"
      today -> "open"
      else -> "missed"
    }
}
