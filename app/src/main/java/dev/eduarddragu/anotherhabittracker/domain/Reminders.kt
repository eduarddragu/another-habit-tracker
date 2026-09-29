package dev.eduarddragu.anotherhabittracker.domain

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import kotlin.random.Random

/** How hard a reminder pushes, from the first slot of the day to the last. */
enum class Tone {
  OPENING,
  NUDGE,
  PUSH,
  LAST_CALL,
}

object ReminderPlan {
  /** How long "On it" keeps a habit's reminders quiet. */
  const val ON_IT_MILLIS = 2 * 60 * 60 * 1000L

  /**
   * Whether a reminder stays quiet because "On it" was tapped: only until [quietUntil], and never the
   * last call, which is the one that saves the streak.
   */
  fun quieted(tone: Tone, nowMillis: Long, quietUntil: Long?): Boolean = tone != Tone.LAST_CALL && quietUntil != null && nowMillis < quietUntil

  /**
   * The next time [slot] fires strictly after [now]. Uses the zone's rules, so a slot that falls in a
   * spring-forward gap moves to the first valid instant instead of being skipped.
   */
  fun nextTrigger(slot: LocalTime, now: ZonedDateTime): ZonedDateTime {
    val today = ZonedDateTime.of(now.toLocalDate(), slot, now.zone)
    return if (today.isAfter(now)) today else ZonedDateTime.of(now.toLocalDate().plusDays(1), slot, now.zone)
  }

  /** First slot opens the day, last one is the last call, the rest get louder in the second half. */
  fun tone(index: Int, count: Int): Tone =
    when {
      index == 0 && count > 1 -> Tone.OPENING
      index == count - 1 -> Tone.LAST_CALL
      index * 2 >= count - 1 -> Tone.PUSH
      else -> Tone.NUDGE
    }

  /**
   * The latest slot of today that has already passed, if any. Used to catch up once when alarms were
   * lost (phone off or rebooting at reminder time, clock changed).
   */
  fun lastPassedSlot(times: List<LocalTime>, now: LocalTime): Int? = times.indexOfLast { !it.isAfter(now) }.takeIf { it >= 0 }
}

data class ReminderText(val title: String, val body: String)

/** Reminder copy. Picks are seeded by day and slot, so a re-posted reminder keeps its wording. */
object ReminderMessages {
  private val studyNudges =
    listOf(
      "%s is still waiting. Thirty minutes, not three hours.",
      "The laptop is right there. So is the notebook. Only %s is missing.",
      "Friendly reminder (for now): %s.",
      "Half an hour. One. %s. Go.",
    )
  private val studyPushes =
    listOf(
      "Haven't seen you study today. %s, remember?",
      "One episode less tonight, thirty minutes of %s more.",
    )
  private val simpleNudges =
    listOf(
      "%s: a few minutes now beats none later.",
      "Friendly reminder (for now): %s.",
      "%s. Sit down, start the timer.",
    )
  private val simplePushes =
    listOf(
      "Still no %s today. Ten minutes is enough.",
      "The day is getting away. %s first.",
    )
  private val lastCallStreak =
    listOf(
      "Your %2\$d-day streak dies at midnight. %1\$s. Now.",
      "Throwing away %3\$s for a night on the couch? %1\$s.",
      "Last call. %3\$s, gone at midnight. %1\$s.",
    )
  private val lastCallNoStreak =
    listOf(
      "Nothing logged today. There's still time for %1\$s.",
      "Streak at zero. Zero plus one is one: %1\$s.",
    )

  /**
   * [subject] is what the reminder is about: the habit name, or today's topic for a study habit.
   * [openingBody] replaces the default streak line of the first reminder (e.g. the topic's first question).
   */
  fun text(subject: String, kind: HabitKind, tone: Tone, streak: Int, day: LocalDate, slot: Int, openingBody: String? = null): ReminderText {
    val random = Random(day.toEpochDay() * 31 + slot)
    val streakLine = if (streak > 0) "${dayCount(streak)} so far." else "No streak yet. Today works."
    return when (tone) {
      Tone.OPENING -> ReminderText("Today: $subject", openingBody?.let { "$it\n$streakLine" } ?: streakLine)
      Tone.NUDGE -> ReminderText(subject, pool(kind, studyNudges, simpleNudges).random(random).format(subject))
      Tone.PUSH -> ReminderText(subject, pool(kind, studyPushes, simplePushes).random(random).format(subject))
      Tone.LAST_CALL ->
        ReminderText(
          "$subject · last call",
          (if (streak > 0) lastCallStreak else lastCallNoStreak).random(random).format(subject, streak, dayCount(streak)),
        )
    }
  }

  private fun pool(kind: HabitKind, study: List<String>, simple: List<String>) = if (kind == HabitKind.STUDY) study else simple
}
