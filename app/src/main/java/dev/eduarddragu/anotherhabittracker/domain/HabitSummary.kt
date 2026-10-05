package dev.eduarddragu.anotherhabittracker.domain

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * One log entry as the domain sees it (the Room entity lives in data/). [track] and [module] are only
 * read for books (title and author, see Books).
 */
data class LogRecord(
  val day: LocalDate,
  val type: EntryType,
  val score: Int? = null,
  val minutes: Int? = null,
  val topicId: String? = null,
  val track: String? = null,
  val module: String? = null,
)

/** Everything the screens, widgets and reminders need to know about one habit on one day. */
data class HabitSummary(
  val today: LocalDate,
  val doneToday: Boolean,
  val frozenToday: Boolean,
  /** Today is a day off (see TimeOff): nothing nags. */
  val pausedToday: Boolean = false,
  /** Today can take the week's freeze: nothing logged, not a day off, and the freeze is free. */
  val canFreezeToday: Boolean,
  /** Yesterday was missed but can still be frozen to keep the streak. */
  val canFreezeYesterday: Boolean,
  /** Yesterday has nothing logged: it can still be logged, and maybe frozen. */
  val yesterdayEmpty: Boolean,
  /** Yesterday was a day off with nothing logged: it can be logged, never frozen (it needs no saving). */
  val yesterdayOff: Boolean = false,
  val stats: HabitStats,
  /** Heatmap cell for every day that has something logged. */
  val cells: Map<LocalDate, Cell>,
  /** Days off with nothing logged, drawn like freezes: kept apart so a screen reader can tell them. */
  val daysOff: Set<LocalDate> = emptySet(),
  /** Study habits only: today's topic, and the latest mark per topic for progress views. */
  val pick: TopicPick?,
  val topicMarks: Map<String, TopicMark>,
) {
  /** A reminder is only worth sending while the day is still open. */
  val dayOpen: Boolean
    get() = !doneToday && !frozenToday && !pausedToday
}

object HabitSummaries {
  /** [curriculum] is only read for study habits. */
  fun build(kind: HabitKind, records: List<LogRecord>, today: LocalDate, paused: Set<LocalDate> = emptySet(), curriculum: () -> Curriculum): HabitSummary {
    val sessions = records.filter { it.type == EntryType.SESSION }
    val sessionDays = sessions.map { it.day }.toSet()
    // A session and a freeze on the same day (data from before sessions replaced freezes): the
    // session wins, and the freeze counts neither for the streak nor as this week's freeze.
    val freezes = records.filter { it.type == EntryType.FREEZE }.map { it.day }.toSet() - sessionDays
    // Days off bridge the streak like freezes, without touching the week's freeze.
    val pausedDays = paused - sessionDays - freezes
    val bridges = freezes + pausedDays
    val cells =
      records
        // Marks (a known topic, a finished book) are not things done on a day.
        .filter { it.type == EntryType.SESSION || it.type == EntryType.FREEZE }
        .groupBy { it.day }
        .mapValues { (_, day) ->
          val daySessions = day.filter { it.type == EntryType.SESSION }
          Heatmap.cell(kind, DayLog(daySessions.map { it.score }, daySessions.map { it.minutes }, frozen = day.any { it.type == EntryType.FREEZE }))
        } + pausedDays.associateWith { Heatmap.cell(kind, DayLog(emptyList(), emptyList(), frozen = true)) }
    val marks = if (kind == HabitKind.STUDY) topicMarks(records) else emptyList()
    return HabitSummary(
      today = today,
      doneToday = today in sessionDays,
      frozenToday = today in freezes,
      pausedToday = today in pausedDays,
      canFreezeToday = today !in pausedDays && Freezes.canFreeze(today, sessionDays, freezes),
      canFreezeYesterday = today.minusDays(1) !in pausedDays && Freezes.canSaveYesterday(today, sessionDays, freezes, bridges),
      // A day off counts as empty here: a session done on it and logged past midnight still needs a way in.
      yesterdayEmpty = Freezes.yesterdayEmpty(today, sessionDays, freezes, bridges),
      yesterdayOff = today.minusDays(1) in pausedDays,
      stats = Stats.of(sessions.map { Session(it.day, it.score, it.minutes) }, bridges, today),
      cells = cells,
      daysOff = pausedDays,
      pick = if (kind == HabitKind.STUDY) TopicPicker.pick(curriculum(), marks, today) else null,
      topicMarks = TopicPicker.latest(marks, today.plusDays(1)),
    )
  }

  /** Scored study sessions and KNOWN marks, as the picker sees them. Known counts as a 5. */
  fun topicMarks(records: List<LogRecord>): List<TopicMark> =
    records.mapNotNull { record ->
      val topicId = record.topicId ?: return@mapNotNull null
      when {
        record.type == EntryType.KNOWN -> TopicMark(topicId, record.day, 5, known = true)
        record.type == EntryType.SESSION && record.score != null -> TopicMark(topicId, record.day, record.score)
        else -> null
      }
    }
}

/**
 * The system clock in whatever zone the phone is in right now. Clock.systemDefaultZone() captures the
 * zone once, so after travelling "today" would stay on the old zone until the process restarts.
 */
object PhoneClock : Clock() {
  override fun getZone(): ZoneId = ZoneId.systemDefault()

  override fun instant(): Instant = Instant.now()

  override fun withZone(zone: ZoneId): Clock = system(zone)
}
