package dev.eduarddragu.anotherhabittracker.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TodaysTopicTest {
  private val today = LocalDate.of(2026, 10, 10)
  private val curriculum =
    Curriculum(
      1,
      listOf(Area("a", "A"), Area("b", "B")),
      listOf(Topic("a-root", "a", "Root"), Topic("a-child", "a", "Child", listOf("a-root")), Topic("b-one", "b", "One"), Topic("b-two", "b", "Two")),
    )

  private fun session(daysAgo: Long, topic: String, score: Int = 4) = LogRecord(today.minusDays(daysAgo), EntryType.SESSION, score, 30, topic)

  private fun known(topic: String) = LogRecord(today, EntryType.KNOWN, topicId = topic)

  /** Resolves the way the repository does: over the summary built from the same records. */
  private fun resolve(
    records: List<LogRecord>,
    focusDay: LocalDate? = null,
    focusTopic: String? = null,
    timeOff: List<TimeOffPeriod> = emptyList(),
    kind: HabitKind = HabitKind.STUDY,
  ): Pair<TodaysTopic, TopicPick?> {
    val built = HabitSummaries.build(kind, records, today) { curriculum }
    return TodaysTopics.resolve(kind, built.pick, built.topicMarks, records, today, focusDay, focusTopic, timeOff, curriculum) to built.pick
  }

  private fun topic(id: String) = curriculum.byId.getValue(id)

  @Test
  fun aTopicChosenForTodayIsContinued() {
    val (topic, _) = resolve(listOf(session(1, "a-root")), today, "a-root")
    assertEquals(TopicPick(topic("a-root"), PickKind.CONTINUE), topic.pick)
    assertTrue(topic.focused)
  }

  @Test
  fun aChosenTopicDueForReviewReadsAsAReview() {
    // Scored 2 five days ago: its review came due yesterday.
    val (topic, _) = resolve(listOf(session(5, "b-one", score = 2)), today, "b-one")
    assertEquals(TopicPick(topic("b-one"), PickKind.REVIEW), topic.pick)
    assertTrue(topic.focused)
  }

  @Test
  fun aChosenTopicMarkedKnownGivesWayToThePicker() {
    val (topic, picked) = resolve(listOf(session(1, "a-root"), known("b-one")), today, "b-one")
    assertEquals(picked, topic.pick)
    assertFalse(topic.focused)
  }

  @Test
  fun aChoiceFromYesterdayIsIgnored() {
    val (topic, picked) = resolve(listOf(session(1, "a-root")), today.minusDays(1), "a-root")
    assertEquals(picked, topic.pick)
    assertFalse(topic.focused)
  }

  @Test
  fun anUnknownTopicIsIgnored() {
    val (topic, picked) = resolve(listOf(session(1, "a-root")), today, "gone")
    assertEquals(picked, topic.pick)
    assertFalse(topic.focused)
  }

  @Test
  fun timeOffHoldsTheTopicOfItsFirstDay() {
    val start = today.minusDays(4)
    val records = listOf(session(5, "a-root"))
    val (topic, _) = resolve(records, timeOff = listOf(TimeOffPeriod(start = start, end = today.minusDays(1))))
    val held = TopicPicker.pickOn(curriculum, HabitSummaries.topicMarks(records), start)!!.topic
    assertEquals(TopicPick(held, PickKind.CONTINUE), topic.pick)
    assertFalse(topic.focused)
  }

  @Test
  fun aTopicChosenByHandWinsOverTheHeldOne() {
    val (topic, _) = resolve(listOf(session(5, "a-root")), today, "b-two", timeOff = listOf(TimeOffPeriod(start = today.minusDays(4))))
    assertEquals(TopicPick(topic("b-two"), PickKind.CONTINUE), topic.pick)
    assertTrue(topic.focused)
  }

  @Test
  fun aSessionAfterTheTimeOffStartedEndsTheHold() {
    val records = listOf(session(5, "a-root"), session(1, "b-one"))
    val (topic, picked) = resolve(records, timeOff = listOf(TimeOffPeriod(start = today.minusDays(4), end = today.minusDays(2))))
    assertEquals(picked, topic.pick)
    assertNotEquals(PickKind.CONTINUE, topic.pick?.kind)
  }

  @Test
  fun theHeldTopicStaysOnTheDayItsLogged() {
    val start = today.minusDays(4)
    val before = listOf(session(5, "a-root"))
    val held = TopicPicker.pickOn(curriculum, HabitSummaries.topicMarks(before), start)!!.topic
    val (topic, _) = resolve(before + session(0, held.id), timeOff = listOf(TimeOffPeriod(start = start, end = today.minusDays(1))))
    assertEquals(TopicPick(held, PickKind.CONTINUE), topic.pick)
    // The day after, the hold is over.
    val (tomorrow, picked) = resolve(before + session(1, held.id), timeOff = listOf(TimeOffPeriod(start = start.minusDays(1), end = today.minusDays(2))))
    assertEquals(picked, tomorrow.pick)
  }

  @Test
  fun aSessionOnAnotherTopicTodayTakesThePicksPlace() {
    val (before, _) = resolve(listOf(session(3, "a-root")))
    val other = listOf("a-root", "a-child", "b-one", "b-two").first { it != before.pick?.topic?.id }
    val (topic, _) = resolve(listOf(session(3, "a-root"), session(0, other)))
    assertEquals(other, topic.pick?.topic?.id)
    assertEquals(if (other == "a-root") PickKind.REVIEW else PickKind.NEW, topic.pick?.kind)
  }

  @Test
  fun otherHabitsKeepTheirPick() {
    val (topic, picked) = resolve(listOf(LogRecord(today.minusDays(1), EntryType.SESSION, minutes = 5)), today, "a-root", kind = HabitKind.SIMPLE)
    assertEquals(picked, topic.pick)
    assertEquals(null, topic.pick)
    assertFalse(topic.focused)
  }
}
