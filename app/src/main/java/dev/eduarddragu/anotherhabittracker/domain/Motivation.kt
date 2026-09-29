package dev.eduarddragu.anotherhabittracker.domain

import java.time.LocalDate
import java.time.LocalTime
import kotlin.random.Random

/** The home screen's greeting and line of the day. Original copy, no borrowed quotes. */
object Motivation {
  private val daily =
    listOf(
      "Nobody is coming to do this for you. Good news: it takes thirty minutes.",
      "Nobody asked if you felt like it.",
      "Be annoyingly consistent.",
      "Don't negotiate with yourself. Start.",
      "Thirty minutes. You've spent longer picking a series.",
      "Excuses don't count as days.",
      "Do it now and stop thinking about it.",
      "The hard part is the first minute. Go get it over with.",
      "Tired counts. Busy counts. Skipping doesn't.",
      "Do it badly if you have to. Just do it today.",
      "Boring days count the same. Annoying, but true.",
    )

  /** Used on some days once any habit has a streak of at least STREAK_LINES_FROM. */
  private val streaking =
    listOf(
      "Day %d. Don't you dare stop now.",
      "%d days straight. Keep feeding it.",
      "%d days in a row. Today is not the day it breaks.",
      "A %d-day streak doesn't defend itself.",
      "%d days. The chain is getting heavy. Good.",
      "%1\$d days. Quitting now wastes %1\$d days.",
    )

  private val allDone =
    listOf(
      "Done. Nothing else is asking for you today.",
      "All logged. Your phone has nothing on you until tomorrow.",
      "Nothing left open. The rest of the day is yours.",
      "Everything's logged. Go do something unscheduled.",
      "That's today handled. Same time tomorrow.",
    )

  private const val STREAK_LINES_FROM = 3

  /** A greeting around the name: "Good evening, Eduard" or "Still up, Eduard?". */
  data class Greeting(val lead: String, val question: Boolean = false)

  private val mornings = listOf(Greeting("Good morning"), Greeting("Morning"), Greeting("Hello"), Greeting("Morning again"))
  private val afternoons = listOf(Greeting("Good afternoon"), Greeting("Afternoon"), Greeting("Hey"))
  private val evenings = listOf(Greeting("Good evening"), Greeting("Evening"), Greeting("Back again"), Greeting("Still going"))
  private val nights = listOf(Greeting("Still up", question = true), Greeting("Late one", question = true), Greeting("Can't sleep", question = true))

  /** Follows the hour; which variant is the same all day (seeded by the date), so it doesn't flicker. */
  fun greeting(time: LocalTime, day: LocalDate): Greeting {
    val pool =
      when (time.hour) {
        in 5..11 -> mornings
        in 12..17 -> afternoons
        in 18..21 -> evenings
        else -> nights
      }
    return pool.random(Random(day.toEpochDay() * 31 + pool.size))
  }

  /**
   * Same line all day (seeded by the date); a different pool once everything is logged. With a streak
   * going, about half the days call it out by its length.
   */
  fun line(day: LocalDate, everythingDone: Boolean, streak: Int = 0): String {
    val random = Random(day.toEpochDay())
    if (everythingDone) return allDone.random(random)
    return if (streak >= STREAK_LINES_FROM && random.nextBoolean()) streaking.random(random).format(streak) else daily.random(random)
  }
}
