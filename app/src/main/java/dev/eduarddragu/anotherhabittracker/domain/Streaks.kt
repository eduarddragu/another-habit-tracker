package dev.eduarddragu.anotherhabittracker.domain

import java.time.LocalDate
import java.time.temporal.IsoFields

/**
 * Streak rules, per habit:
 * - a day with at least one session counts;
 * - a frozen day keeps the streak alive without adding to it;
 * - while today is still open, the streak runs up to yesterday.
 */
object Streaks {
  fun current(sessions: Set<LocalDate>, freezes: Set<LocalDate>, today: LocalDate): Int {
    var day = if (today in sessions || today in freezes) today else today.minusDays(1)
    var count = 0
    while (day in sessions || day in freezes) {
      if (day in sessions) count++
      day = day.minusDays(1)
    }
    return count
  }
}

/** One freeze per ISO week, and only on a day without a session or another freeze. */
object Freezes {
  /**
   * The freezes that count, for the streak and for the week's one freeze: not on a day with a session
   * (the session wins) nor on a day off (time off added later over a freeze takes it over).
   */
  fun counted(freezes: Set<LocalDate>, sessions: Set<LocalDate>, paused: Set<LocalDate>): Set<LocalDate> = freezes - sessions - paused

  fun canFreeze(day: LocalDate, sessions: Set<LocalDate>, freezes: Set<LocalDate>): Boolean {
    if (day in sessions || day in freezes) return false
    return freezes.none { isoWeek(it) == isoWeek(day) }
  }

  /**
   * Yesterday can still be frozen after the fact: it has nothing logged, this week's freeze (of
   * yesterday's week) is free, and the day before carries a streak to save: a session, or bridges
   * with a session behind them. [bridges] are freezes plus days off: a day off carries the streak but
   * doesn't use the week's freeze.
   */
  fun canSaveYesterday(today: LocalDate, sessions: Set<LocalDate>, freezes: Set<LocalDate>, bridges: Set<LocalDate> = freezes): Boolean {
    val yesterday = today.minusDays(1)
    val before = yesterday.minusDays(1)
    return canFreeze(yesterday, sessions, freezes) && (before in sessions || before in bridges) && Streaks.current(sessions, bridges, before) > 0
  }

  /**
   * Yesterday has no session and no freeze (a day off still counts as empty) and the habit was
   * already running then: the habit's page offers to log it (done late, past midnight) and, when the
   * rules allow, to freeze it. Logging it is the only way to log for yesterday, so it doesn't depend
   * on a streak. Only a session or a freeze before yesterday shows the habit was running: days off
   * are the same for every habit, so they say nothing about one that has just been added.
   */
  fun yesterdayEmpty(today: LocalDate, sessions: Set<LocalDate>, freezes: Set<LocalDate>): Boolean {
    val yesterday = today.minusDays(1)
    return yesterday !in sessions && yesterday !in freezes && (sessions + freezes).any { it < yesterday }
  }

  /**
   * Yesterday was missed after a streak: nothing on it (no session, freeze or day off, all in
   * [bridges] but the sessions), and the day before carried a streak. Two days missed are not one slip.
   */
  fun slipped(today: LocalDate, sessions: Set<LocalDate>, bridges: Set<LocalDate>): Boolean {
    val yesterday = today.minusDays(1)
    val before = yesterday.minusDays(1)
    return yesterday !in sessions && yesterday !in bridges && (before in sessions || before in bridges) && Streaks.current(sessions, bridges, before) > 0
  }

  private fun isoWeek(day: LocalDate) = day.get(IsoFields.WEEK_BASED_YEAR) to day.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
}

/** "1 day", "12 days": every day count in the copy goes through here. */
fun dayCount(days: Int): String = if (days == 1) "1 day" else "$days days"
