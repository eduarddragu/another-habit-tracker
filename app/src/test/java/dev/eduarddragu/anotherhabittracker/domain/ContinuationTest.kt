package dev.eduarddragu.anotherhabittracker.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ContinuationTest {
  private val today = LocalDate.of(2026, 9, 29)

  private fun on(daysAgo: Long, topic: String?) = StudiedOn(today.minusDays(daysAgo), topic)

  @Test
  fun offersYesterdaysTopic() = assertEquals("go-concurrency", Continuation.candidate(listOf(on(1, "go-concurrency")), today, "linux-basics"))

  @Test
  fun stillOffersItTheDayAfter() = assertEquals("go-concurrency", Continuation.candidate(listOf(on(2, "go-concurrency")), today, "linux-basics"))

  @Test
  fun notWhenItIsTodaysTopicAnyway() = assertNull(Continuation.candidate(listOf(on(1, "go-concurrency")), today, "go-concurrency"))

  @Test
  fun notOnceTodayIsLogged() = assertNull(Continuation.candidate(listOf(on(0, "linux-basics"), on(1, "go-concurrency")), today, "linux-basics"))

  @Test
  fun notForAnOldSession() = assertNull(Continuation.candidate(listOf(on(3, "go-concurrency")), today, "linux-basics"))

  @Test
  fun skipsSessionsWithoutATopic() = assertEquals("go-concurrency", Continuation.candidate(listOf(on(1, null), on(2, "go-concurrency")), today, "linux-basics"))
}
