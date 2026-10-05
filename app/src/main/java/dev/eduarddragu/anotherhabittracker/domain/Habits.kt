package dev.eduarddragu.anotherhabittracker.domain

import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** How a session is logged. SIMPLE: done plus minutes. STUDY: score, minutes and study details. */
enum class HabitKind {
  SIMPLE,
  STUDY,
}

/** What a log entry means for the day it belongs to. */
enum class EntryType {
  SESSION,
  FREEZE,
  /** A study topic marked as already known: unlocks its dependents, never reviewed, no streak credit. */
  KNOWN,
  /**
   * A book finished on its day (reading habits, see Books): track and module carry the book. Like
   * KNOWN, a mark: no session, no streak credit, no minutes.
   */
  FINISHED,
}

private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")

/** Reminder times are stored as "08:00,11:00". Invalid or duplicate items are dropped; the result is sorted. */
fun parseReminderTimes(raw: String): List<LocalTime> =
  raw
    .split(",")
    .map { it.trim() }
    .filter { it.isNotEmpty() }
    .mapNotNull { runCatching { LocalTime.parse(it, TIME_FORMAT) }.getOrNull() }
    .distinct()
    .sorted()

fun formatReminderTimes(times: List<LocalTime>): String = times.distinct().sorted().joinToString(",") { it.format(TIME_FORMAT) }

fun formatTime(time: LocalTime): String = time.format(TIME_FORMAT)
