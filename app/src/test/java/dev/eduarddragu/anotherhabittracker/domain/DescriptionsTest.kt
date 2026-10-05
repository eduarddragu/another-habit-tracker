package dev.eduarddragu.anotherhabittracker.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DescriptionsTest {
  // A Friday: Monday to Thursday are behind it, Saturday and Sunday ahead.
  private val friday = LocalDate.of(2026, 10, 2)
  private val monday = friday.minusDays(4)

  @Test
  fun weekReadsEveryDayThenTheDaysAhead() {
    val cells = mapOf(monday to Cell.Done(2), monday.plusDays(1) to Cell.Frozen, monday.plusDays(2) to Cell.Frozen)
    val off = setOf(monday.plusDays(2))
    assertEquals(
      "This week: Monday done, Tuesday frozen, Wednesday day off, Thursday missed, today open, 2 days ahead",
      Descriptions.week(cells, off, friday),
    )
  }

  @Test
  fun weekSaysWhatTodayIs() {
    assertEquals("This week: today done, 6 days ahead", Descriptions.week(mapOf(monday to Cell.Done(1)), emptySet(), monday))
    assertEquals("This week: today frozen, 6 days ahead", Descriptions.week(mapOf(monday to Cell.Frozen), emptySet(), monday))
    assertEquals("This week: today day off, 6 days ahead", Descriptions.week(mapOf(monday to Cell.Frozen), setOf(monday), monday))
  }

  @Test
  fun weekOnSundayHasNothingAhead() {
    val sunday = monday.plusDays(6)
    assertEquals(
      "This week: Monday missed, Tuesday missed, Wednesday missed, Thursday missed, Friday missed, Saturday missed, today open",
      Descriptions.week(emptyMap(), emptySet(), sunday),
    )
  }

  @Test
  fun weekWithOneDayAhead() {
    val saturday = monday.plusDays(5)
    assertTrue(Descriptions.week(emptyMap(), emptySet(), saturday).endsWith("today open, 1 day ahead"))
  }

  @Test
  fun heatmapCountsTheLastThirtyDaysWithToday() {
    val cells =
      (0L until 23L).associate { friday.minusDays(it) to Cell.Done(1) as Cell } +
        mapOf(friday.minusDays(25) to Cell.Frozen, friday.minusDays(26) to Cell.Frozen, friday.minusDays(27) to Cell.Frozen) +
        // Outside the window: not counted.
        mapOf(friday.minusDays(30) to Cell.Done(4))
    assertEquals("Done on 23 of the last 30 days, 2 frozen, 1 off", Descriptions.heatmap(cells, setOf(friday.minusDays(27)), friday))
    assertEquals("Done on 0 of the last 30 days", Descriptions.heatmap(emptyMap(), emptySet(), friday))
  }

  @Test
  fun streakReadsAsOneValue() {
    assertEquals("12 days in a row", Descriptions.streak(12))
    assertEquals("1 day in a row", Descriptions.streak(1))
    assertEquals("0 days in a row", Descriptions.streak(0))
  }

  @Test
  fun summaryKeepsDaysOffApartFromFreezes() {
    val records = listOf(LogRecord(monday, EntryType.FREEZE), LogRecord(monday.plusDays(1), EntryType.SESSION))
    val summary = HabitSummaries.build(HabitKind.SIMPLE, records, friday, paused = setOf(monday.plusDays(2), monday.plusDays(1))) { Curriculum(1, emptyList(), emptyList()) }
    // A session on a day off makes it a done day, not a day off.
    assertEquals(setOf(monday.plusDays(2)), summary.daysOff)
    assertEquals(
      "This week: Monday frozen, Tuesday done, Wednesday day off, Thursday missed, today open, 2 days ahead",
      Descriptions.week(summary.cells, summary.daysOff, friday),
    )
  }

  @Test
  fun iconLabelsAreWords() {
    assertEquals("Book", HabitIcon.BOOK.label)
    assertEquals("Dumbbell", HabitIcon.DUMBBELL.label)
  }

  @Test
  fun scoreLabelsAtTheEnds() {
    assertEquals("Didn't get it", TopicPicker.scoreLabel(1))
    assertEquals("Could teach it", TopicPicker.scoreLabel(5))
  }
}
