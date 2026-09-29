package dev.eduarddragu.anotherhabittracker.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrollGuardTest {
  private val day = LocalDate.of(2026, 10, 1)
  private val minute = 60_000L

  @Test
  fun blocksOnOpeningOnlyWhileAHabitIsOpen() {
    val fresh = ScrollBudget(day)
    assertTrue(ScrollGuard.shouldBlock(anyHabitOpen = true, budget = fresh))
    assertFalse(ScrollGuard.shouldBlock(anyHabitOpen = false, budget = fresh))
    assertEquals(GuardMoment.OPENING, ScrollGuard.moment(fresh))
  }

  @Test
  fun tenMinutesFirstThenFiveAtATime() {
    val first = ScrollBudget(day).grant()
    assertEquals(10 * minute, first.remainingMillis)
    assertFalse(ScrollGuard.shouldBlock(true, first))

    val spent = first.use(10 * minute)
    assertTrue(ScrollGuard.shouldBlock(true, spent))
    assertEquals(GuardMoment.OVERTIME, ScrollGuard.moment(spent))
    assertEquals(5 * minute, spent.grant().remainingMillis)
  }

  @Test
  fun leftoverTimeCarriesOverWithinTheDay() {
    val budget = ScrollBudget(day).grant().use(3 * minute)
    assertEquals(7 * minute, budget.remainingMillis)
    assertFalse(ScrollGuard.shouldBlock(true, budget))
  }

  @Test
  fun aNewDayStartsOver() {
    val yesterday = ScrollBudget(day).grant().use(4 * minute)
    assertEquals(yesterday, yesterday.on(day))
    val today = yesterday.on(day.plusDays(1))
    assertEquals(ScrollBudget(day.plusDays(1)), today)
    assertEquals(GuardMoment.OPENING, ScrollGuard.moment(today))
  }

  private fun allTexts(stakes: ScrollGuard.Stakes): List<GuardText> =
    GuardMoment.entries.flatMap { moment -> (0..60).map { ScrollGuard.text(moment, stakes, "Instagram", day.plusDays(it.toLong()), it % 4) } }

  @Test
  fun copyAgreesWithWhatIsOpen() {
    val two = allTexts(ScrollGuard.Stakes(listOf("Study", "Meditation"), studyOpen = true, anythingLogged = false, streak = 12))
    two.forEach { text ->
      assertFalse(text.body, text.body.contains("Meditation is") || text.body.contains("Meditation has"))
      assertFalse(text.body, text.body.contains("{") || text.body.contains("%"))
    }
    assertTrue(two.any { it.body.contains("Study and Meditation") })
  }

  @Test
  fun oneDayIsSingularAndNoStreakMeansNoDays() {
    allTexts(ScrollGuard.Stakes(listOf("Study"), studyOpen = true, anythingLogged = false, streak = 1)).forEach { assertFalse(it.body, it.body.contains("1 days")) }
    allTexts(ScrollGuard.Stakes(listOf("Study"), studyOpen = true, anythingLogged = true, streak = 0)).forEach { assertFalse(it.body, it.body.contains(" 0 ") || it.body.contains("0-day") || it.body.contains("Nothing logged")) }
  }

  @Test
  fun notebookLinesOnlyWhenStudyIsOpen() =
    allTexts(ScrollGuard.Stakes(listOf("Meditation"), studyOpen = false, anythingLogged = false, streak = 5)).forEach { assertFalse(it.body, it.body.contains("otebook")) }

  @Test
  fun grantLabelsFollowTheConstants() {
    assertEquals("10 minutes, then I'm out", ScrollGuard.grantLabel(ScrollBudget(day)))
    assertEquals("Fine, 5 more", ScrollGuard.grantLabel(ScrollBudget(day).grant()))
  }

  @Test
  fun subjectListsHabitsInPlainEnglish() {
    assertEquals("Study", ScrollGuard.subject(listOf("Study")))
    assertEquals("Study and Meditation", ScrollGuard.subject(listOf("Study", "Meditation")))
    assertEquals("Study, Reading and Meditation", ScrollGuard.subject(listOf("Study", "Reading", "Meditation")))
  }
}
