package dev.eduarddragu.anotherhabittracker.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** One habit's week, as the recap needs it. */
/** [since]: the habit's first day with anything logged; the days before it aren't missed ones. */
data class RecapHabit(val name: String, val kind: HabitKind, val records: List<LogRecord>, val streak: Int, val since: LocalDate? = null)

data class RecapText(val title: String, val body: String)

/**
 * The Sunday evening recap: the week (Monday to [today]) per habit, the study topic that went worst,
 * and the longest streak. One notification, a closing line for the week. Original copy, seeded by the
 * week so it doesn't change if posted again.
 */
object WeeklyRecap {
  /** Sunday evening, when the week is as good as over. */
  val AT: java.time.LocalTime = java.time.LocalTime.of(20, 30)

  /** The next recap strictly after [now]: this Sunday's if it hasn't passed, else next week's. */
  fun nextAt(now: java.time.LocalDateTime): java.time.LocalDateTime {
    val thisSunday = now.toLocalDate().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)).atTime(AT)
    return if (thisSunday.isAfter(now)) thisSunday else thisSunday.plusWeeks(1)
  }

  fun weekStart(today: LocalDate): LocalDate = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

  /** [paused]: days off (see TimeOff), which neither count nor are missed, unless something was done on one. */
  fun build(habits: List<RecapHabit>, today: LocalDate, paused: Set<LocalDate> = emptySet(), topicTitle: (String) -> String?): RecapText {
    val start = weekStart(today)
    val daysSoFar = (today.toEpochDay() - start.toEpochDay() + 1).toInt()
    val lines = mutableListOf<String>()
    var doneDays = 0
    var possibleDays = 0
    // Still to do tonight (the recap comes at 20:30, before the last calls), and each habit's share of
    // its week, for a line about the next one.
    val open = mutableListOf<String>()
    val shares = mutableListOf<Pair<String, Double>>()
    for (habit in habits) {
      // A habit never logged (one just added) has no week to miss yet: it stays out of the recap.
      if (habit.since == null && habit.records.none { it.type == EntryType.SESSION }) continue
      val sessions = habit.records.filter { it.type == EntryType.SESSION && !it.day.isBefore(start) && !it.day.isAfter(today) }
      val dayList = sessions.map { it.day }.toSet()
      val days = dayList.size
      // Today counts once it's done; until then (Sunday at 20:30) it's still open, not missed.
      val possible =
        (0 until daysSoFar)
          .map { start.plusDays(it.toLong()) }
          .count { day -> day in dayList || (day != today && day !in paused && (habit.since == null || !day.isBefore(habit.since))) }
      val minutes = sessions.sumOf { it.minutes ?: 0 }
      doneDays += days
      possibleDays += possible
      if (today !in dayList && today !in paused) open += habit.name
      if (possible > 0) shares += habit.name to days.toDouble() / possible
      val time = if (minutes > 0) ", ${formatMinutes(minutes)}" else ""
      val scores = sessions.mapNotNull { it.score }
      val average = if (habit.kind == HabitKind.STUDY && scores.isNotEmpty()) ", average ${"%.1f".format(java.util.Locale.ENGLISH, scores.average())}" else ""
      lines += "${habit.name}: $days of $possible days$time$average."
      if (habit.kind == HabitKind.STUDY) {
        sessions.filter { it.score != null && it.topicId != null }.minByOrNull { it.score!! }?.takeIf { it.score!! <= 2 }?.let { worst ->
          topicTitle(worst.topicId!!)?.let { lines += "Toughest: $it (${worst.score}/5). It'll be back." }
        }
      }
    }
    if (open.isNotEmpty()) lines += "Still open tonight: ${names(open)}. The week isn't over."
    val best = habits.maxOfOrNull { it.streak } ?: 0
    if (best > 0) lines += "Longest streak: ${dayCount(best)}."
    // The habit that showed up least leads next week, when one clearly did.
    val weakest = shares.minByOrNull { it.second }
    if (weakest != null && shares.size > 1 && weakest.second < 1.0 && shares.count { it.second == weakest.second } == 1) lines += "Next week: ${weakest.first} first."
    val share = if (possibleDays == 0) 0.0 else doneDays.toDouble() / possibleDays
    val pool =
      when {
        share >= 1.0 -> perfect
        share >= 0.7 -> good
        share >= 0.4 -> mixed
        else -> rough
      }
    val title = pool[(start.toEpochDay() % pool.size).toInt()]
    return RecapText(title, lines.joinToString("\n"))
  }

  /** "Study", "Study and Chores", "Study, Reading and Chores". */
  private fun names(list: List<String>): String = if (list.size == 1) list.first() else list.dropLast(1).joinToString(", ") + " and " + list.last()

  private fun formatMinutes(minutes: Int): String {
    val hours = minutes / 60
    val rest = minutes % 60
    return when {
      hours == 0 -> "$rest min"
      rest == 0 -> "${hours}h"
      else -> "${hours}h ${rest}m"
    }
  }

  private val perfect = listOf("Every day. Show-off.", "A clean week. Do it again.", "Seven for seven. Annoyingly consistent.")
  private val good = listOf("A solid week.", "Most days showed up. Good.", "Not perfect, clearly not lazy.")
  private val mixed = listOf("Half a week. The other half is next week's problem.", "Some days yes, some days no. Pick yes.")
  private val rough = listOf("Rough week. A new one starts tomorrow.", "That week happened. Monday doesn't care.")
}
