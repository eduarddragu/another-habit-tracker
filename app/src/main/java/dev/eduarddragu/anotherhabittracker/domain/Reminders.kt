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
   * The next midnight refresh, 30 s into a day, strictly after [now]. A process that starts in the
   * first 30 s of a day gets today's: tomorrow's would replace the alarm still due and skip a rollover.
   */
  fun nextMidnight(now: ZonedDateTime): ZonedDateTime {
    val today = now.toLocalDate().atStartOfDay(now.zone).plusSeconds(30)
    return if (today.isAfter(now)) today else now.toLocalDate().plusDays(1).atStartOfDay(now.zone).plusSeconds(30)
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
   * The reminder times for [day]: the weekend list on Saturday and Sunday when the habit has one,
   * the weekday list otherwise (workdays want lunch and evening; weekends can be spread out).
   */
  fun timesOn(day: LocalDate, weekdays: List<LocalTime>, weekend: List<LocalTime>?): List<LocalTime> =
    if (weekend != null && isWeekend(day)) weekend else weekdays

  fun isWeekend(day: LocalDate): Boolean = day.dayOfWeek == java.time.DayOfWeek.SATURDAY || day.dayOfWeek == java.time.DayOfWeek.SUNDAY

  /**
   * The next time reminder [slot] fires strictly after [now]: the slot-th time of the first day (today
   * or later) whose list has that many. Null when no day of the week has it.
   */
  fun nextSlotTrigger(slot: Int, now: ZonedDateTime, timesFor: (LocalDate) -> List<LocalTime>): ZonedDateTime? {
    for (offset in 0L..7L) {
      val day = now.toLocalDate().plusDays(offset)
      val time = timesFor(day).getOrNull(slot) ?: continue
      val at = ZonedDateTime.of(day, time, now.zone)
      // A slot in a spring-forward gap moves to the first valid instant (ZonedDateTime.of does that).
      if (at.isAfter(now)) return at
    }
    return null
  }

  /** How late a reminder may still go out: past this it's about a moment that has gone. */
  const val MAX_LATE_MILLIS = 60 * 60 * 1000L

  /**
   * Whether the alarm meant for [scheduledAt] fires too late to remind: on another day than it was
   * meant for (a slot of last night delivered after midnight would nag about the new day), or more
   * than [MAX_LATE_MILLIS] after its time (a phone that slept through it). The slot is rescheduled
   * either way. Null (an alarm set before the time was carried) is never late.
   */
  fun tooLate(scheduledAt: ZonedDateTime?, now: ZonedDateTime): Boolean {
    if (scheduledAt == null) return false
    val at = scheduledAt.withZoneSameInstant(now.zone)
    return at.toLocalDate() != now.toLocalDate() || java.time.Duration.between(at, now).toMillis() > MAX_LATE_MILLIS
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
      "Bruh. Keep thirty minutes of tonight for %s.",
      "Work's done. Before the evening fills up: %s.",
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
      "Still no %s today. A few minutes still count.",
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
