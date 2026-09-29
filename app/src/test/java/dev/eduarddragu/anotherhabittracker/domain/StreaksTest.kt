package dev.eduarddragu.anotherhabittracker.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreaksTest {
  private val today = LocalDate.of(2026, 10, 10)

  private fun days(vararg ago: Long) = ago.map { today.minusDays(it) }.toSet()

  @Test
  fun emptyHistoryHasNoStreak() = assertEquals(0, Streaks.current(emptySet(), emptySet(), today))

  @Test
  fun countsConsecutiveDaysIncludingToday() = assertEquals(3, Streaks.current(days(0, 1, 2), emptySet(), today))

  @Test
  fun openTodayCountsUpToYesterday() = assertEquals(2, Streaks.current(days(1, 2), emptySet(), today))

  @Test
  fun gapBreaksTheStreak() {
    assertEquals(2, Streaks.current(days(0, 1, 3, 4), emptySet(), today))
    assertEquals(0, Streaks.current(days(2, 3), emptySet(), today))
  }

  @Test
  fun freezeKeepsTheStreakWithoutAddingToIt() {
    assertEquals(2, Streaks.current(days(0, 2), days(1), today))
    assertEquals(1, Streaks.current(days(1), days(0), today))
  }

  @Test
  fun longestSpansFreezes() = assertEquals(4, Streaks.longest(days(0, 1, 5, 6, 7, 9), days(8)))

  @Test
  fun oneFreezePerIsoWeek() {
    val monday = LocalDate.of(2026, 10, 5)
    assertTrue(Freezes.canFreeze(monday, emptySet(), emptySet()))
    assertFalse(Freezes.canFreeze(monday.plusDays(3), emptySet(), setOf(monday)))
    assertTrue(Freezes.canFreeze(monday.plusDays(7), emptySet(), setOf(monday)))
  }

  @Test
  fun cannotFreezeADayAlreadyLogged() = assertFalse(Freezes.canFreeze(today, setOf(today), emptySet()))
}
