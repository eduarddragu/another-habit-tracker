package dev.eduarddragu.anotherhabittracker.domain

import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MotivationTest {
  private val day = LocalDate.of(2026, 10, 1)

  @Test
  fun greetingFollowsTheHour() {
    val week = (0L until 14L).map { day.plusDays(it) }
    fun leads(hour: Int) = week.map { Motivation.greeting(LocalTime.of(hour, 0), it) }.toSet()
    assert(leads(9).any { "morning" in it.lead.lowercase() })
    assert(leads(14).any { "afternoon" in it.lead.lowercase() })
    assert(leads(19).any { "evening" in it.lead.lowercase() })
    assert(leads(2).all { it.question } && leads(9).none { it.question })
  }

  @Test
  fun greetingIsStableWithinADayAndVariesAcrossDays() {
    assertEquals(Motivation.greeting(LocalTime.of(9, 0), day), Motivation.greeting(LocalTime.of(11, 30), day))
    assert((0L until 14L).map { Motivation.greeting(LocalTime.of(19, 0), day.plusDays(it)) }.toSet().size > 1)
  }

  @Test
  fun lineIsStableWithinADay() = assertEquals(Motivation.line(day, false), Motivation.line(day, false))

  @Test
  fun lineChangesOnceEverythingIsDone() = assertNotEquals(Motivation.line(day, false), Motivation.line(day, true))

  @Test
  fun linesVaryAcrossDays() {
    val week = (0L until 14L).map { Motivation.line(day.plusDays(it), false) }.toSet()
    assert(week.size > 4) { "expected variety, got $week" }
  }

  @Test
  fun aStreakGetsCalledOutOnSomeDays() {
    val lines = (0L until 30L).map { Motivation.line(day.plusDays(it), false, streak = 12) }
    assert(lines.any { "12" in it }) { "expected the streak in some line, got $lines" }
    assert(lines.any { "12" !in it }) { "expected plain lines too, got $lines" }
  }

  @Test
  fun shortStreaksAreNotCalledOut() {
    val lines = (0L until 30L).map { Motivation.line(day.plusDays(it), false, streak = 2) }
    assert(lines.none { it.any(Char::isDigit) && "thirty" !in it }) { "unexpected number in $lines" }
    assertEquals(lines, (0L until 30L).map { Motivation.line(day.plusDays(it), false) })
  }

  @Test
  fun timeOffLinesAreStableThroughTheDayAndDifferPerCard() {
    val day = java.time.LocalDate.of(2026, 10, 5)
    assertEquals(Motivation.timeOffLine(day), Motivation.timeOffLine(day))
    org.junit.Assert.assertNotEquals(Motivation.timeOffLine(day, 1), Motivation.timeOffLine(day, 2))
    (0L..30L).forEach { org.junit.Assert.assertFalse(Motivation.timeOffLine(day.plusDays(it)).contains("—")) }
  }

  @Test
  fun aSlipIsNamedAndMinutesAreTheHabitsOwn() {
    assertNotEquals(Motivation.line(day, false), Motivation.line(day, false, slipped = true))
    val lines = (0L..60L).map { Motivation.line(day.plusDays(it), false, minutes = 20) }
    assertTrue("no hard-coded thirty", lines.none { "thirty" in it.lowercase() || "{" in it })
    assertEquals("30 minutes, a few minutes", withMinutes("{minutes}", 30) + ", " + withMinutes("{minutes}", null))
    assertEquals("A few minutes.", withMinutes("{Minutes}.", null))
  }
}
