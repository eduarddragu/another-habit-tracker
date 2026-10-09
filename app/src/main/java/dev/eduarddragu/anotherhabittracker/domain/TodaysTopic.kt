package dev.eduarddragu.anotherhabittracker.domain

import java.time.LocalDate

/** Today's study topic once the choices made by hand are applied, and whether one of them won. */
data class TodaysTopic(val pick: TopicPick?, val focused: Boolean)

object TodaysTopics {
  /**
   * The topic every screen, the widget and the reminders show today, in order of precedence:
   * 1. a topic chosen for [today] ([focusDay], [focusTopicId]: "keep going" or a review picked from the
   *    curriculum), as REVIEW when it's due, else CONTINUE; once marked as known it gives way;
   * 2. back from (or during) time off with nothing studied since it began, the topic of its first day
   *    ([TimeOff.heldSince]), as CONTINUE, until it's logged or marked as known;
   * 3. the picker's own [picked].
   * Once a session is logged today, the card shows what was studied: a held topic stays through the day
   * it's logged on, and a session on another topic (picked by hand in the log form) takes the place of
   * the pick, rather than the card turning to a topic not studied, marked as done.
   * [marks] are the summary's latest marks per topic; [records] the habit's whole log. Habits other
   * than study keep [picked] as it is.
   */
  fun resolve(
    kind: HabitKind,
    picked: TopicPick?,
    marks: Map<String, TopicMark>,
    records: List<LogRecord>,
    today: LocalDate,
    focusDay: LocalDate?,
    focusTopicId: String?,
    timeOff: List<TimeOffPeriod>,
    curriculum: Curriculum,
  ): TodaysTopic {
    if (kind != HabitKind.STUDY) return TodaysTopic(picked, focused = false)
    val resolved = resolveBeforeLogs(picked, marks, records, today, focusDay, focusTopicId, timeOff, curriculum)
    val studied = records.filter { it.type == EntryType.SESSION && it.day == today }.mapNotNull { it.topicId }
    if (studied.isEmpty() || resolved.pick?.topic?.id in studied) return resolved
    val topic = studied.asReversed().firstNotNullOfOrNull { curriculum.byId[it] } ?: return resolved
    // Seen before today: a review; otherwise new.
    val seen = records.any { it.topicId == topic.id && it.day.isBefore(today) }
    return TodaysTopic(TopicPick(topic, if (seen) PickKind.REVIEW else PickKind.NEW), focused = resolved.focused)
  }

  private fun resolveBeforeLogs(
    picked: TopicPick?,
    marks: Map<String, TopicMark>,
    records: List<LogRecord>,
    today: LocalDate,
    focusDay: LocalDate?,
    focusTopicId: String?,
    timeOff: List<TimeOffPeriod>,
    curriculum: Curriculum,
  ): TodaysTopic {
    val chosen = focusTopicId?.takeIf { focusDay == today }?.let { curriculum.byId[it] }?.takeIf { marks[it.id]?.known != true }
    if (chosen != null) {
      val due = marks[chosen.id]?.let { TopicPicker.dueDate(it)?.isAfter(today) == false } == true
      return TodaysTopic(TopicPick(chosen, if (due) PickKind.REVIEW else PickKind.CONTINUE), focused = true)
    }
    // Today's own sessions don't end the hold: the topic stays on the card through the day it's logged.
    val sessionDays = records.filter { it.type == EntryType.SESSION && it.day != today }.map { it.day }.toSet()
    val held =
      TimeOff.heldSince(timeOff, today, sessionDays)?.let { start ->
        TopicPicker.pickOn(curriculum, HabitSummaries.topicMarks(records), start)?.topic?.takeIf { marks[it.id]?.known != true }
      }
    return TodaysTopic(held?.let { TopicPick(it, PickKind.CONTINUE) } ?: picked, focused = false)
  }
}
