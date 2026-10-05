package dev.eduarddragu.anotherhabittracker.domain

import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HabitSummaryTest {
  private val today = LocalDate.of(2026, 10, 7)
  private val curriculum =
    Curriculum(1, listOf(Area("a", "A")), listOf(Topic("root", "a", "Root"), Topic("child", "a", "Child", requires = listOf("root"))))

  private fun build(kind: HabitKind, vararg records: LogRecord) = HabitSummaries.build(kind, records.toList(), today) { curriculum }

  @Test
  fun emptyDayIsOpen() {
    val summary = build(HabitKind.SIMPLE)
    assertTrue(summary.dayOpen)
    assertTrue(summary.canFreezeToday)
    assertEquals(emptyMap<LocalDate, Cell>(), summary.cells)
  }

  @Test
  fun sessionTodayClosesTheDay() {
    val summary = build(HabitKind.SIMPLE, LogRecord(today, EntryType.SESSION, minutes = 20))
    assertTrue(summary.doneToday)
    assertFalse(summary.dayOpen)
    assertFalse(summary.canFreezeToday)
    assertEquals(Cell.Done(3), summary.cells[today])
  }

  @Test
  fun freezeClosesTheDayWithoutCountingAsASession() {
    val summary = build(HabitKind.SIMPLE, LogRecord(today, EntryType.FREEZE))
    assertTrue(summary.frozenToday)
    assertFalse(summary.doneToday)
    assertFalse(summary.dayOpen)
    assertEquals(Cell.Frozen, summary.cells[today])
    assertEquals(0, summary.stats.totalSessions)
  }

  @Test
  fun knownMarksDontCloseTheDayOrDrawCells() {
    val summary = build(HabitKind.STUDY, LogRecord(today, EntryType.KNOWN, topicId = "root"))
    assertTrue(summary.dayOpen)
    assertNull(summary.cells[today])
    assertEquals(0, summary.stats.totalSessions)
    // ...but they do unlock dependents right away.
    assertEquals("child", summary.pick?.topic?.id)
    assertTrue(summary.topicMarks.getValue("root").known)
  }

  @Test
  fun studyPickSkipsTopicsAlreadyScoredWell() {
    val summary = build(HabitKind.STUDY, LogRecord(today.minusDays(1), EntryType.SESSION, score = 4, topicId = "root"))
    assertEquals("child", summary.pick?.topic?.id)
    assertEquals(1, summary.stats.streak)
  }

  @Test
  fun simpleHabitsHaveNoPickOrMarks() {
    val summary = build(HabitKind.SIMPLE, LogRecord(today, EntryType.SESSION, score = 5, topicId = "root"))
    assertNull(summary.pick)
    assertEquals(emptyMap<String, TopicMark>(), summary.topicMarks)
  }

  @Test
  fun unscoredStudySessionsDontCountForThePicker() = assertEquals(emptyList<TopicMark>(), HabitSummaries.topicMarks(listOf(LogRecord(today, EntryType.SESSION, topicId = "root"))))

  @Test
  fun lastPassedSlotForCatchUp() {
    val times = listOf(LocalTime.of(9, 30), LocalTime.of(13, 30), LocalTime.of(21, 30))
    assertNull(ReminderPlan.lastPassedSlot(times, LocalTime.of(8, 0)))
    assertEquals(0, ReminderPlan.lastPassedSlot(times, LocalTime.of(9, 30)))
    assertEquals(1, ReminderPlan.lastPassedSlot(times, LocalTime.of(20, 0)))
    assertEquals(2, ReminderPlan.lastPassedSlot(times, LocalTime.of(23, 59)))
  }

  @Test
  fun openingReminderLeadsWithTheGuidingQuestion() {
    val text = ReminderMessages.text("Namespaces", HabitKind.STUDY, Tone.OPENING, 3, today, 0, openingBody = "Which namespaces exist?")
    assertEquals("Today: Namespaces", text.title)
    assertTrue(text.body.startsWith("Which namespaces exist?\n"))
    assertTrue(text.body.contains("3 days"))
  }

  @Test
  fun knownAndSessionOnTheSameDayKnownWins() {
    val marks = listOf(TopicMark("x", today.minusDays(1), 2), TopicMark("x", today.minusDays(1), 5, known = true))
    assertTrue(TopicPicker.latest(marks, today).getValue("x").known)
  }

  @Test
  fun aSessionOnAFrozenDayWinsAndFreesTheWeek() {
    val today = LocalDate.of(2026, 10, 1)
    val yesterday = today.minusDays(1)
    val records =
      listOf(
        LogRecord(yesterday.minusDays(1), EntryType.SESSION, minutes = 10),
        LogRecord(yesterday, EntryType.FREEZE),
        LogRecord(yesterday, EntryType.SESSION, minutes = 10),
      )
    val summary = HabitSummaries.build(HabitKind.SIMPLE, records, today) { error("not study") }
    assertEquals(2, summary.stats.streak)
    assertTrue("the ignored freeze leaves the week's free", summary.canFreezeToday)
  }
}
