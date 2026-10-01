package dev.eduarddragu.anotherhabittracker.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NewFeaturesTest {
  private val sunday = LocalDate.of(2026, 10, 4)

  @Test
  fun milestonesOnlyOnTheExactDay() {
    assertTrue(Milestones.line(7)!!.isNotBlank())
    assertNull(Milestones.line(8))
    assertTrue(Milestones.isMilestone(100))
    assertFalse(Milestones.isMilestone(99))
  }

  @Test
  fun recapCountsTheWeekFromMonday() {
    val study =
      RecapHabit(
        "Study",
        HabitKind.STUDY,
        listOf(
          LogRecord(sunday.minusDays(7), EntryType.SESSION, 5, 30, "old"),
          LogRecord(sunday.minusDays(6), EntryType.SESSION, 4, 30, "a"),
          LogRecord(sunday.minusDays(2), EntryType.SESSION, 2, 45, "b"),
          LogRecord(sunday, EntryType.SESSION, 3, 60, "c"),
        ),
        streak = 3,
      )
    val text = WeeklyRecap.build(listOf(study), sunday) { if (it == "b") "Consensus" else "x" }
    assertTrue(text.body, text.body.contains("Study: 3 of 7 days, 2h 15m, average 3.0."))
    assertTrue(text.body, text.body.contains("Toughest: Consensus (2/5)."))
    assertTrue(text.body.contains("Longest streak: 3 days."))
    assertFalse(text.body.contains("—") || text.body.contains("–"))
  }

  @Test
  fun recapTitleFollowsHowTheWeekWent() {
    val perfect = RecapHabit("Meditation", HabitKind.SIMPLE, (0L..6L).map { LogRecord(sunday.minusDays(it), EntryType.SESSION, minutes = 5) }, 7)
    val empty = RecapHabit("Meditation", HabitKind.SIMPLE, emptyList(), 0)
    assertTrue(WeeklyRecap.build(listOf(perfect), sunday) { null }.title in listOf("Every day. Show-off.", "A clean week. Do it again.", "Seven for seven. Annoyingly consistent."))
    assertTrue(WeeklyRecap.build(listOf(empty), sunday) { null }.title.startsWith("R") || WeeklyRecap.build(listOf(empty), sunday) { null }.title.startsWith("That"))
  }

  @Test
  fun guardWeekSumsFromMonday() {
    val days = listOf(GuardDay(sunday.minusDays(7), 9, 600_000), GuardDay(sunday.minusDays(3), 2, 300_000), GuardDay(sunday, 1, 120_000))
    val summary = GuardWeek.summary(days, sunday)
    assertEquals(GuardWeek.Summary(3, 7), summary)
    assertEquals("Stopped you 3 times this week, 7 minutes let through.", GuardWeek.line(summary))
    assertEquals("Hasn't had to stop you this week.", GuardWeek.line(GuardWeek.Summary(0, 0)))
    assertEquals(2, GuardWeek.trim(days, sunday, keep = 7).size)
  }

  @Test
  fun dueReviewsPutWeakOnesFirst() {
    val a = Topic("x-a", "x", "A")
    val b = Topic("x-b", "x", "B")
    val c = Topic("x-c", "x", "C")
    val curriculum = Curriculum(1, listOf(Area("x", "X", 1.0)), listOf(a, b, c))
    val marks =
      listOf(
        TopicMark("x-a", sunday.minusDays(40), 4),
        TopicMark("x-b", sunday.minusDays(5), 2),
        TopicMark("x-c", sunday.minusDays(1), 3),
      )
    val due = TopicPicker.dueReviews(curriculum, marks, sunday)
    assertEquals(listOf("x-b", "x-a"), due.map { it.topic.id })
  }

  @Test
  fun recapIsSundayEveningAndNeverInThePast() {
    val at = java.time.LocalTime.of(20, 30)
    assertEquals(sunday.atTime(at), WeeklyRecap.nextAt(sunday.minusDays(2).atTime(9, 0)))
    assertEquals(sunday.atTime(at), WeeklyRecap.nextAt(sunday.atTime(20, 29)))
    assertEquals(sunday.plusWeeks(1).atTime(at), WeeklyRecap.nextAt(sunday.atTime(20, 30)))
  }

  @Test
  fun anOpenSundayIsNotCountedAsMissed() {
    val habit = RecapHabit("Meditation", HabitKind.SIMPLE, (1L..6L).map { LogRecord(sunday.minusDays(it), EntryType.SESSION, minutes = 5) }, 6)
    assertTrue(WeeklyRecap.build(listOf(habit), sunday) { null }.body.startsWith("Meditation: 6 of 6 days"))
  }
}
