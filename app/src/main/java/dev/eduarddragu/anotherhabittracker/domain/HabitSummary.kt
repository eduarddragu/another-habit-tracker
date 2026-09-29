package dev.eduarddragu.anotherhabittracker.domain

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** One log entry as the domain sees it (the Room entity lives in data/). */
data class LogRecord(val day: LocalDate, val type: EntryType, val score: Int? = null, val minutes: Int? = null, val topicId: String? = null)

/** Everything the screens, widgets and reminders need to know about one habit on one day. */
data class HabitSummary(
  val today: LocalDate,
  val doneToday: Boolean,
  val frozenToday: Boolean,
  val canFreezeToday: Boolean,
  /** The day this week's freeze went on, or null while it's still available. */
  val freezeUsedOn: LocalDate?,
  /** Yesterday was missed but can still be frozen to keep the streak. */
  val canFreezeYesterday: Boolean,
  val stats: HabitStats,
  /** Heatmap cell for every day that has something logged. */
  val cells: Map<LocalDate, Cell>,
  /** Study habits only: today's topic, and the latest mark per topic for progress views. */
  val pick: TopicPick?,
  val topicMarks: Map<String, TopicMark>,
) {
  /** A reminder is only worth sending while the day is still open. */
  val dayOpen: Boolean
    get() = !doneToday && !frozenToday
}

object HabitSummaries {
  /** [curriculum] is only read for study habits. */
  fun build(kind: HabitKind, records: List<LogRecord>, today: LocalDate, curriculum: () -> Curriculum): HabitSummary {
    val sessions = records.filter { it.type == EntryType.SESSION }
    val sessionDays = sessions.map { it.day }.toSet()
    val freezes = records.filter { it.type == EntryType.FREEZE }.map { it.day }.toSet()
    val cells =
      records
        .filter { it.type != EntryType.KNOWN }
        .groupBy { it.day }
        .mapValues { (_, day) ->
          val daySessions = day.filter { it.type == EntryType.SESSION }
          Heatmap.cell(kind, DayLog(daySessions.map { it.score }, daySessions.map { it.minutes }, frozen = day.any { it.type == EntryType.FREEZE }))
        }
    val marks = if (kind == HabitKind.STUDY) topicMarks(records) else emptyList()
    return HabitSummary(
      today = today,
      doneToday = today in sessionDays,
      frozenToday = today in freezes,
      canFreezeToday = Freezes.canFreeze(today, sessionDays, freezes),
      freezeUsedOn = Freezes.usedThisWeek(today, freezes),
      canFreezeYesterday = Freezes.canSaveYesterday(today, sessionDays, freezes),
      stats = Stats.of(sessions.map { Session(it.day, it.score, it.minutes) }, freezes, today),
      cells = cells,
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
