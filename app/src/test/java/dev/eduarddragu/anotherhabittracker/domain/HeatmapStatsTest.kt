package dev.eduarddragu.anotherhabittracker.domain

import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HeatmapStatsTest {
  // A Wednesday.
  private val today = LocalDate.of(2026, 9, 30)

  @Test
  fun weeksEndWithTheCurrentPartialWeek() {
    val weeks = Heatmap.weeks(today, 3)
    assertEquals(3, weeks.size)
    weeks.forEach { assertEquals(7, it.size) }
    assertEquals(DayOfWeek.MONDAY, weeks.first().first()!!.dayOfWeek)
    assertEquals(today, weeks.last()[2])
    assertNull(weeks.last()[3])
    assertEquals(today.minusDays(2 + 14), weeks.first().first())
  }

  @Test
  fun studyIntensityFollowsTheBestScore() {
    val levels = (1..5).map { (Heatmap.cell(HabitKind.STUDY, DayLog(scores = listOf(it))) as Cell.Done).level }
    assertEquals(listOf(1, 1, 2, 3, 4), levels)
    assertEquals(Cell.Done(4), Heatmap.cell(HabitKind.STUDY, DayLog(scores = listOf(2, 5))))
  }

  @Test
  fun simpleIntensityFollowsMinutes() {
    val levels = listOf(5, 10, 25, 45).map { (Heatmap.cell(HabitKind.SIMPLE, DayLog(minutes = listOf(it))) as Cell.Done).level }
    assertEquals(listOf(1, 2, 3, 4), levels)
    assertEquals(Cell.Done(3), Heatmap.cell(HabitKind.SIMPLE, DayLog(minutes = listOf(15, 15))))
    assertEquals(Cell.Done(2), Heatmap.cell(HabitKind.SIMPLE, DayLog(minutes = listOf(null))))
  }

  @Test
  fun emptyAndFrozenDays() {
    assertEquals(Cell.Empty, Heatmap.cell(HabitKind.SIMPLE, null))
    assertEquals(Cell.Frozen, Heatmap.cell(HabitKind.SIMPLE, DayLog(frozen = true)))
    // A session on a frozen day wins.
    assertEquals(Cell.Done(2), Heatmap.cell(HabitKind.STUDY, DayLog(scores = listOf(3), frozen = true)))
  }

  @Test
  fun statsCountDaysMinutesAndScores() {
    val sessions =
      listOf(
        Session(today, 4, 30),
        Session(today, 2, 10),
        Session(today.minusDays(1), 5, null),
        Session(today.minusDays(3), null, 20),
        Session(today.minusDays(40), 3, 60), // outside the 30-day window
      )
    val stats = Stats.of(sessions, emptySet(), today)
    assertEquals(2, stats.streak)
    assertEquals(3, stats.daysLast30)
    assertEquals(60, stats.minutesLast30)
    assertEquals(11.0 / 3, stats.averageScoreLast30!!, 1e-9)
    assertEquals(5, stats.totalSessions)
    assertEquals(120, stats.totalMinutes)
  }

  @Test
  fun noScoresMeansNoAverage() = assertNull(Stats.of(listOf(Session(today, null, 10)), emptySet(), today).averageScoreLast30)

  @Test
  fun heatmapWaitsForEightWeeksOfHistory() {
    val today = LocalDate.of(2026, 9, 28)
    assertEquals(false, Heatmap.worthShowing(today, null))
    assertEquals(false, Heatmap.worthShowing(today, today.minusWeeks(8).plusDays(1)))
    assertEquals(true, Heatmap.worthShowing(today, today.minusWeeks(8)))
  }

  @Test
  fun visibleWeeksFollowTheHistory() {
    val today = LocalDate.of(2026, 9, 28) // a Monday
    assertEquals(10, Heatmap.visibleWeeks(today, null, minimum = 10))
    assertEquals(10, Heatmap.visibleWeeks(today, today.minusDays(20), minimum = 10))
    assertEquals(15, Heatmap.visibleWeeks(today, today.minusWeeks(14), minimum = 10))
    assertEquals(53, Heatmap.visibleWeeks(today, today.minusYears(3), minimum = 10))
  }
}
