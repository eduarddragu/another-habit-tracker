package dev.eduarddragu.anotherhabittracker.domain

import java.io.File
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TopicPickerTest {
  private val day = LocalDate.of(2026, 10, 1)
  private val areas = listOf(Area("a", "A"), Area("b", "B"))

  private fun topic(id: String, area: String = "a", vararg requires: String) = Topic(id, area, id, requires.toList())

  private fun curriculum(vararg topics: Topic) = Curriculum(1, areas, topics.toList())

  private fun mark(id: String, score: Int, daysAgo: Long) = TopicMark(id, day.minusDays(daysAgo), score)

  @Test
  fun emptyHistoryPicksARoot() {
    val c = curriculum(topic("root"), topic("child", "a", "root"))
    assertEquals("root", TopicPicker.pick(c, emptyList(), day)!!.topic.id)
  }

  @Test
  fun scoreThreeUnlocksDependents() {
    val c = curriculum(topic("root"), topic("child", "a", "root"))
    val pick = TopicPicker.pick(c, listOf(mark("root", 3, 1)), day)!!
    assertEquals("child" to PickKind.NEW, pick.topic.id to pick.kind)
  }

  @Test
  fun lowScoreDoesNotUnlockAndComesBack() {
    val c = curriculum(topic("root"), topic("child", "a", "root"))
    val pick = TopicPicker.pick(c, listOf(mark("root", 2, 4)), day)!!
    assertEquals("root" to PickKind.REVIEW, pick.topic.id to pick.kind)
  }

  @Test
  fun weakReviewWaitsForItsInterval() {
    val c = curriculum(topic("weak"), topic("fresh"))
    assertEquals("fresh", TopicPicker.pick(c, listOf(mark("weak", 2, 1)), day)!!.topic.id)
  }

  @Test
  fun weakReviewBeatsNewTopics() {
    val c = curriculum(topic("weak"), topic("x"), topic("y"))
    assertEquals("weak", TopicPicker.pick(c, listOf(mark("weak", 1, 2)), day)!!.topic.id)
  }

  @Test
  fun knownUnlocksTheSameDayAndIsNeverReviewed() {
    val c = curriculum(topic("root"), topic("child", "a", "root"))
    val known = listOf(TopicMark("root", day, 5, known = true))
    assertEquals("child", TopicPicker.pick(c, known, day)!!.topic.id)
    assertEquals("child", TopicPicker.pick(c, known, day.plusDays(365))!!.topic.id)
  }

  @Test
  fun todaysSessionDoesNotChangeTodaysPick() {
    val c = curriculum(*(0 until 10).map { topic("t$it", if (it % 2 == 0) "a" else "b") }.toTypedArray())
    val before = TopicPicker.pick(c, emptyList(), day)!!
    val after = TopicPicker.pick(c, listOf(TopicMark(before.topic.id, day, 4)), day)!!
    assertEquals(before, after)
  }

  @Test
  fun recentAreaIsAvoided() {
    val c = curriculum(topic("a-done"), topic("a1"), topic("a2"), topic("a3"), topic("b1", "b"), topic("b2", "b"), topic("b3", "b"))
    val history = listOf(mark("a-done", 4, 1))
    val areas = (0L until 25L).map { TopicPicker.pick(c, history, day.plusDays(it))!!.topic.area }
    assertTrue("b should dominate: $areas", areas.count { it == "b" } > 2 * areas.count { it == "a" })
  }

  @Test
  fun everythingDoneRevisitsTheOldest() {
    val c = curriculum(topic("x"), topic("y"))
    val pick = TopicPicker.pick(c, listOf(mark("x", 5, 10), mark("y", 5, 5)), day)!!
    assertEquals("x" to PickKind.REVIEW, pick.topic.id to pick.kind)
  }

  @Test
  fun nothingLeftToPick() = assertNull(TopicPicker.pick(curriculum(topic("x")), listOf(TopicMark("x", day, 5, known = true)), day))

  @Test
  fun withPrerequisitesFollowsTheGraph() {
    val c = curriculum(topic("base"), topic("mid", "a", "base"), topic("target", "a", "mid"), topic("other"))
    assertEquals(setOf("base", "mid", "target"), c.withPrerequisites(listOf("target")))
  }

  @Test
  fun topicStates() {
    val c = curriculum(topic("root"), topic("child", "a", "root"), topic("weak"), topic("known"))
    val latest = TopicPicker.latest(listOf(mark("root", 4, 1), mark("weak", 2, 1), TopicMark("known", day, 5, known = true)), day)
    assertEquals(TopicState.DONE, TopicPicker.state(c.byId.getValue("root"), latest))
    assertEquals(TopicState.AVAILABLE, TopicPicker.state(c.byId.getValue("child"), latest))
    assertEquals(TopicState.WEAK, TopicPicker.state(c.byId.getValue("weak"), latest))
    assertEquals(TopicState.KNOWN, TopicPicker.state(c.byId.getValue("known"), latest))
    val none = TopicPicker.latest(emptyList(), day)
    assertEquals(TopicState.LOCKED, TopicPicker.state(c.byId.getValue("child"), none))
  }

  @Test
  fun problemsAreReported() {
    val c = Curriculum(1, areas, listOf(topic("x", "a", "y"), topic("y", "a", "x"), topic("z", "zz", "nope"), topic("z")))
    val problems = c.problems().joinToString("\n")
    listOf("cycle", "unknown prerequisite 'nope'", "unknown area 'zz'", "duplicate id: z").forEach { assertTrue(problems, problems.contains(it)) }
  }

  @Test
  fun bundledCurriculumIsValidAndListsDirectPrerequisitesOnly() {
    val c = Curriculum.parse(File("src/main/assets/curriculum.json").readText())
    assertEquals(emptyList<String>(), c.problems())
    assertTrue(c.topics.size >= 100)
    val redundant =
      c.topics.flatMap { t -> t.requires.filter { r -> r in c.withPrerequisites(t.requires - r) }.map { "${t.id} -> $it" } }
    assertEquals(emptyList<String>(), redundant)
  }

  @Test
  fun pickOnIgnoresWhatHappenedAfterThatDay() {
    val c = curriculum(topic("root"), topic("child", "a", "root"))
    val yesterday = day.minusDays(1)
    val before = TopicPicker.pick(c, emptyList(), yesterday)!!.topic.id
    // Today "root" was logged for yesterday and marked known: yesterday's topic stays what it was.
    val later = listOf(TopicMark("root", yesterday, 4), TopicMark("root", day, 5, known = true))
    assertEquals(before, TopicPicker.pickOn(c, later, yesterday)!!.topic.id)
  }
}
