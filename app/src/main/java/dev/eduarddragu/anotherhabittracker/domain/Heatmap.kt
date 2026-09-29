package dev.eduarddragu.anotherhabittracker.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** What one heatmap cell shows. Levels 1..4 are increasing intensity of a logged day. */
sealed interface Cell {
  data object Empty : Cell

  data object Frozen : Cell

  data class Done(val level: Int) : Cell
}

/** A day as logged: all its sessions (score and minutes) and whether it was frozen. */
data class DayLog(val scores: List<Int?> = emptyList(), val minutes: List<Int?> = emptyList(), val frozen: Boolean = false)

object Heatmap {
  /**
   * Intensity of a logged day. Study: the best score of the day (1-2, 3, 4, 5). Simple habits: total
   * minutes (under 10, 10-19, 20-39, 40+); a session without minutes counts as a solid day.
   */
  fun cell(kind: HabitKind, log: DayLog?): Cell {
    if (log == null) return Cell.Empty
    val sessions = maxOf(log.scores.size, log.minutes.size)
    if (sessions == 0) return if (log.frozen) Cell.Frozen else Cell.Empty
    val level =
      when (kind) {
        HabitKind.STUDY ->
          when (log.scores.filterNotNull().maxOrNull()) {
            null -> 2
            in 1..2 -> 1
            3 -> 2
            4 -> 3
            else -> 4
          }
        HabitKind.SIMPLE -> {
          val total = log.minutes.filterNotNull().sum()
          when {
            log.minutes.all { it == null } -> 2
            total < 10 -> 1
            total < 20 -> 2
            total < 40 -> 3
            else -> 4
          }
        }
      }
    return Cell.Done(level)
  }

  /** The heatmap earns its place after eight weeks of history; before that it's mostly empty squares. */
  fun worthShowing(today: LocalDate, since: LocalDate?): Boolean = since != null && !since.isAfter(today.minusWeeks(8))

  /**
   * How many week columns to draw: enough to reach back to [since] (the first log, so there are no
   * months of grey from before the habit existed), never fewer than [minimum], at most a year.
   */
  fun visibleWeeks(today: LocalDate, since: LocalDate?, minimum: Int, maximum: Int = 53): Int {
    val first = since?.takeIf { it.isBefore(today) } ?: today
    val span = ChronoUnit.WEEKS.between(first.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)), today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))).toInt() + 1
    return span.coerceIn(minimum.coerceAtMost(maximum), maximum)
  }

  /**
   * Week columns (Monday first) ending with the week that contains [today]. Days after today are null,
   * so the last column can be partial.
   */
  fun weeks(today: LocalDate, count: Int): List<List<LocalDate?>> {
    val lastMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    return (count - 1 downTo 0).map { back ->
      val monday = lastMonday.minusWeeks(back.toLong())
      (0L until 7L).map { monday.plusDays(it).takeUnless { day -> day.isAfter(today) } }
    }
  }
}
