package dev.eduarddragu.anotherhabittracker.domain

import java.time.LocalDate
import kotlin.random.Random

/** Where a session is: counting down, paused, or over (ran out or ended) and waiting to be logged. */
enum class SessionPhase {
  RUNNING,
  PAUSED,
  FINISHED,
}

/** What a session is for: it sets the lines under the planet and how its end sounds. */
enum class SessionKind {
  STUDY,
  MEDITATION,
  READING,
  CHORES,
  OTHER;

  companion object {
    fun of(kind: HabitKind, name: String, icon: String?, linkedPackage: String?): SessionKind =
      when {
        kind == HabitKind.STUDY -> STUDY
        Books.appliesTo(kind, name, icon) -> READING
        Chores.appliesTo(kind, name, icon) -> CHORES
        Practices.appliesTo(name, linkedPackage) -> MEDITATION
        else -> OTHER
      }

    /** A session saved before it carried its kind: one with a topic was study. */
    fun guess(topicId: String?): SessionKind = if (topicId != null) STUDY else OTHER
  }
}

/**
 * A focus session: the planet on Home turned into a timer. All times are on one monotonic clock
 * (milliseconds, the phone's elapsed realtime), so a clock correction can't bend it. [day] is the
 * day it started, the day it's logged for.
 */
data class FocusSession(
  val habitId: Long,
  /** Kept with the session: a notification posted from a cold process has no statuses to ask. */
  val habitName: String,
  val topicId: String?,
  val day: LocalDate,
  val plannedMillis: Long,
  val startedAt: Long,
  val extendedMillis: Long = 0,
  val pausedAt: Long? = null,
  val pausedTotalMillis: Long = 0,
  val finishedAt: Long? = null,
  val kind: SessionKind = SessionKind.guess(topicId),
) {
  val totalMillis: Long
    get() = plannedMillis + extendedMillis

  fun phase(now: Long): SessionPhase =
    when {
      finishedAt != null -> SessionPhase.FINISHED
      pausedAt != null -> SessionPhase.PAUSED
      remaining(now) <= 0 -> SessionPhase.FINISHED
      else -> SessionPhase.RUNNING
    }

  /** Time actually counted: wall time since the start, minus the pauses, frozen once finished. */
  fun elapsed(now: Long): Long {
    val until = finishedAt ?: pausedAt ?: now
    return (until - startedAt - pausedTotalMillis).coerceIn(0, totalMillis)
  }

  fun remaining(now: Long): Long = (totalMillis - elapsed(now)).coerceAtLeast(0)

  /** How much of the session is left, 1 at the start, 0 at the end: the arc on the planet. */
  fun left(now: Long): Float = if (totalMillis == 0L) 0f else remaining(now).toFloat() / totalMillis

  /** When it ends on the same clock, if it keeps running from [now]. */
  fun endsAt(now: Long): Long = now + remaining(now)

  fun pause(now: Long): FocusSession = if (phase(now) == SessionPhase.RUNNING) copy(pausedAt = now) else this

  /** Nothing to resume once it's over: a stale Resume must not shrink the time counted. */
  fun resume(now: Long): FocusSession = if (finishedAt != null) this else pausedAt?.let { copy(pausedAt = null, pausedTotalMillis = pausedTotalMillis + (now - it)) } ?: this

  fun extend(now: Long, millis: Long = SessionTimer.EXTEND_MILLIS): FocusSession =
    when (phase(now)) {
      // Finished by running out: five more minutes pick up from now, not from the old end.
      SessionPhase.FINISHED -> {
        val elapsedSoFar = elapsed(now)
        copy(extendedMillis = elapsedSoFar - plannedMillis + millis, finishedAt = null, pausedAt = null, pausedTotalMillis = now - startedAt - elapsedSoFar)
      }
      else -> copy(extendedMillis = extendedMillis + millis)
    }

  /**
   * Ends it: at the pause if paused, else now, but never past the natural end (the alarm can arrive
   * late, and those minutes weren't studied).
   */
  fun finish(now: Long): FocusSession =
    if (finishedAt != null) this else copy(finishedAt = pausedAt ?: minOf(now, startedAt + pausedTotalMillis + totalMillis))

  /**
   * Ended by the midnight refresh: a session from an earlier day left paused (or past its end with the
   * alarm lost). One still running carries on past midnight and ends on its own alarm.
   */
  fun endsAtMidnight(today: LocalDate, now: Long): Boolean = finishedAt == null && day < today && phase(now) != SessionPhase.RUNNING

  /**
   * The minutes to put in the log form: the full length when it ran out, whole minutes done when it
   * was ended early, nothing when it was too short to count (see SessionTimer.minimumMinutes).
   */
  fun minutesToLog(now: Long): Int? {
    val minutes = (elapsed(now) / 60_000).toInt()
    return minutes.takeIf { it >= SessionTimer.minimumMinutes(plannedMillis) }
  }

  /** The same session on a clock that moved by [millis]: every saved time moves with it. */
  fun shifted(millis: Long): FocusSession =
    if (millis == 0L) this else copy(startedAt = startedAt + millis, pausedAt = pausedAt?.plus(millis), finishedAt = finishedAt?.plus(millis))

  /** What the disc shows: minutes left rounded up, or seconds in the last minute. */
  fun display(now: Long): SessionTimer.Display {
    val left = remaining(now)
    return if (left < 60_000 && phase(now) != SessionPhase.FINISHED) SessionTimer.Display(((left + 999) / 1000).toInt(), seconds = true)
    else if (phase(now) == SessionPhase.FINISHED) SessionTimer.Display((elapsed(now) / 60_000).toInt(), seconds = false)
    else SessionTimer.Display(((left + 59_999) / 60_000).toInt(), seconds = false)
  }
}

object SessionTimer {
  const val EXTEND_MILLIS = 5 * 60_000L
  const val MIN_LOG_MINUTES = 5

  /**
   * The least a session must run to count: [MIN_LOG_MINUTES], or half the planned length when that's
   * shorter (a five-minute meditation ended at 4:55 still counts). At least a minute.
   */
  fun minimumMinutes(plannedMillis: Long): Int = minOf(MIN_LOG_MINUTES, maxOf(1, (plannedMillis / 60_000 / 2).toInt()))

  /**
   * Length choices: the log form's presets. A habit with its own session length gets half of it, it,
   * and half again (20 minutes: 10, 20, 30), in steps of five.
   */
  fun presets(kind: HabitKind, sessionMinutes: Int? = null): List<Int> {
    if (sessionMinutes == null) return if (kind == HabitKind.STUDY) listOf(30, 45, 60) else listOf(5, 10, 15)
    val half = maxOf(MIN_LOG_MINUTES, (sessionMinutes / 2 + 2) / 5 * 5)
    return listOf(sessionMinutes - half, sessionMinutes, sessionMinutes + half).filter { it > 0 }.distinct()
  }

  /**
   * Minutes typed by hand in the log form ("Custom"): whole minutes from 1 to a day, the most a backup
   * accepts, or nothing.
   */
  fun customMinutes(text: String): Int? = text.trim().toIntOrNull()?.takeIf { it in 1..Backups.MAX_MINUTES }

  /**
   * What "Done" on a reminder logs: the last session's length, unless it was longer than the longest
   * preset (one long day typed by hand), then the habit's usual length. Nothing when no session had
   * minutes.
   */
  fun doneMinutes(last: Int?, kind: HabitKind, sessionMinutes: Int? = null): Int? =
    last?.let { if (it > presets(kind, sessionMinutes).max()) defaultMinutes(kind, sessionMinutes) else it }

  /** What the settings offer as a habit's session length. */
  val LENGTHS = listOf(5, 10, 15, 20, 25, 30, 45, 60)

  /**
   * One tap starts a session of this length: the habit's own length when it has one, else 30 minutes
   * for study and 5 for the rest (meditation).
   * Longer is "+5 min" as many times as needed.
   */
  fun defaultMinutes(kind: HabitKind, sessionMinutes: Int? = null): Int = sessionMinutes ?: if (kind == HabitKind.STUDY) 30 else 5

  /**
   * How far times saved at [savedElapsed] (wall clock [savedWall]) move to be on the elapsed clock
   * read now ([nowElapsed], wall clock [nowWall]). Within the same boot they stay as they are. After a
   * reboot that clock restarts, so they move by how the two clocks drifted apart since the save: the
   * time the phone spent off counts as time that passed.
   */
  fun rebootShift(sameBoot: Boolean, savedWall: Long, savedElapsed: Long, nowWall: Long, nowElapsed: Long): Long =
    if (sameBoot) 0L else (nowElapsed - (nowWall - savedWall)) - savedElapsed

  data class Display(val value: Int, val seconds: Boolean)

  fun start(habitId: Long, habitName: String, topicId: String?, day: LocalDate, minutes: Int, now: Long, kind: SessionKind = SessionKind.guess(topicId)): FocusSession =
    FocusSession(habitId, habitName, topicId, day, minutes * 60_000L, now, kind = kind)

  /** The lines under the planet for one kind of session. */
  private class Lines(val running: List<String>, val windDown: String, val paused: String, val over: String)

  private val lines =
    mapOf(
      SessionKind.STUDY to Lines(
        listOf("Phone down. Pen up.", "I keep time. You keep going.", "Checking the scope is allowed. Scrolling isn't.", "The questions are below. The answers go on paper."),
        windDown = "Nearly there. Finish the thought.",
        paused = "Paused. The pen is waiting.",
        over = "Time. Put the pen down.",
      ),
      SessionKind.MEDITATION to Lines(
        listOf("Breathe. I'll keep time.", "Nothing to fix. Just notice.", "Thoughts come. Let them walk past.", "Eyes closed is fine. I'll ring."),
        windDown = "Almost done. Stay with the breath.",
        paused = "Paused. Sit back down when you can.",
        over = "Time. Open your eyes slowly.",
      ),
      SessionKind.READING to Lines(
        listOf("Pages, not pixels.", "Phone face down. Book face up.", "One more page is how this works.", "I keep time. You keep reading."),
        windDown = "Nearly there. Finish the page.",
        paused = "Paused. Keep your finger on the page.",
        over = "Time. Bookmark it.",
      ),
      SessionKind.CHORES to Lines(
        listOf("Rack, laundry, plants, bedroom. Go.", "Start with whatever you see first.", "Put one thing where it lives. Then the next.", "Music on, phone down, hands busy."),
        windDown = "Nearly there. Finish what's in your hands.",
        paused = "Paused. The mess will wait, sadly.",
        over = "Time. Look around: better.",
      ),
      SessionKind.OTHER to Lines(
        listOf("Phone down. Start.", "I keep time. You keep going."),
        windDown = "Nearly there.",
        paused = "Paused.",
        over = "Time. Well done.",
      ),
    )

  /** How long before the end the line turns to the wind-down: five minutes, or a third of a short session. */
  fun windDownMillis(session: FocusSession): Long = minOf(5 * 60_000L, session.totalMillis / 3)

  /** The line under the planet: one per session while running, then by phase, in the session's own words. */
  fun line(session: FocusSession, now: Long): String {
    val pool = lines.getValue(session.kind)
    return when (session.phase(now)) {
      SessionPhase.PAUSED -> pool.paused
      SessionPhase.FINISHED -> if (session.minutesToLog(now) == null) "Too short to count. Next time." else if (session.remaining(now) > 0) "Short one. Still counts." else pool.over
      SessionPhase.RUNNING ->
        if (session.remaining(now) <= windDownMillis(session)) {
          if (session.kind == SessionKind.STUDY && windDownMillis(session) == 5 * 60_000L) "Five minutes. Finish the thought." else pool.windDown
        } else pool.running[Random(session.startedAt).nextInt(pool.running.size)]
    }
  }
}
