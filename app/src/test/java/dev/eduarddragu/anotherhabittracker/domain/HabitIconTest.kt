package dev.eduarddragu.anotherhabittracker.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class HabitIconTest {
  @Test
  fun storedChoiceWins() = assertEquals(HabitIcon.MOON, HabitIcon.resolve("MOON", HabitKind.STUDY, null))

  @Test
  fun defaultsFollowTheHabit() {
    assertEquals(HabitIcon.BOOK, HabitIcon.resolve(null, HabitKind.STUDY, null))
    assertEquals(HabitIcon.LOTUS, HabitIcon.resolve(null, HabitKind.SIMPLE, "meditofoundation.medito"))
    assertEquals(HabitIcon.SPARK, HabitIcon.resolve(null, HabitKind.SIMPLE, null))
  }

  @Test
  fun unknownNamesFallBackToTheDefault() = assertEquals(HabitIcon.BOOK, HabitIcon.resolve("ROCKET", HabitKind.STUDY, null))
}
