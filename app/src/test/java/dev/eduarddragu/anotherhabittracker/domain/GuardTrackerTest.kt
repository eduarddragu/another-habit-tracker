package dev.eduarddragu.anotherhabittracker.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuardTrackerTest {
  private val ig = "com.instagram.android"
  private val day = LocalDate.of(2026, 10, 1)
  private val hour = 3_600_000L
  private var clock = 0L
  private val tracker = GuardTracker(isGuarded = { it == ig }, now = { clock })

  private fun List<GuardAction>.evaluation() = filterIsInstance<GuardAction.Evaluate>().single().generation

  @Test
  fun openingAGuardedAppWithNoTimeLeftBlocks() {
    val generation = tracker.onFront(ig).evaluation()
    assertEquals(listOf(GuardAction.Block(ig)), tracker.onEvaluated(generation, anyHabitOpen = true, budget = ScrollBudget(day), millisToMidnight = hour))
  }

  @Test
  fun nothingOpenOrAnotherAppMeansNothingToDo() {
    assertTrue(tracker.onFront("com.example.maps").none { it is GuardAction.Evaluate })
    val generation = tracker.onFront(ig).evaluation()
    assertTrue(tracker.onEvaluated(generation, anyHabitOpen = false, budget = ScrollBudget(day), millisToMidnight = hour).isEmpty())
  }

  @Test
  fun countsWhileInFrontAndChargesOnLeaving() {
    val budget = ScrollBudget(day).grant()
    val actions = tracker.onEvaluated(tracker.onFront(ig).evaluation(), true, budget, hour)
    assertEquals(listOf(GuardAction.Schedule(10 * 60_000L)), actions)
    clock += 4 * 60_000L
    assertTrue(GuardAction.Use(4 * 60_000L) in tracker.onFront("com.example.maps"))
    assertFalse(tracker.counting)
  }

  @Test
  fun sameAppAgainChangesNothing() {
    tracker.onEvaluated(tracker.onFront(ig).evaluation(), true, ScrollBudget(day).grant(), hour)
    assertTrue(tracker.onFront(ig).isEmpty())
    assertTrue(tracker.counting)
  }

  @Test
  fun screenOffDuringAnEvaluationNeverStartsCounting() {
    val generation = tracker.onFront(ig).evaluation()
    tracker.onScreenOff()
    assertTrue(tracker.onEvaluated(generation, true, ScrollBudget(day).grant(), hour).isEmpty())
    assertFalse(tracker.counting)
  }

  @Test
  fun screenOffStopsTheCountAndUnlockingLooksAgain() {
    tracker.onEvaluated(tracker.onFront(ig).evaluation(), true, ScrollBudget(day).grant(), hour)
    clock += 60_000L
    assertTrue(GuardAction.Use(60_000L) in tracker.onScreenOff())
    clock += 8 * hour
    val back = tracker.onScreenOn()
    assertTrue(back.none { it is GuardAction.Use })
    back.evaluation()
  }

  @Test
  fun aGrantStartsCountingWithoutAWindowChange() {
    assertEquals(listOf(GuardAction.Block(ig)), tracker.onEvaluated(tracker.onFront(ig).evaluation(), true, ScrollBudget(day), hour))
    val generation = tracker.onGrant().evaluation()
    assertEquals(listOf(GuardAction.Schedule(10 * 60_000L)), tracker.onEvaluated(generation, true, ScrollBudget(day).grant(), hour))
  }

  @Test
  fun theTimerNeverRunsPastMidnight() {
    val actions = tracker.onEvaluated(tracker.onFront(ig).evaluation(), true, ScrollBudget(day).grant(), millisToMidnight = 60_000L)
    assertEquals(listOf(GuardAction.Schedule(62_000L)), actions)
  }
}
