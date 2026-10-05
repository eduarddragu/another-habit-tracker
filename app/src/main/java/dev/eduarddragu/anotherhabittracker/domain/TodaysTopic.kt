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
    val chosen = focusTopicId?.takeIf { focusDay == today }?.let { curriculum.byId[it] }?.takeIf { marks[it.id]?.known != true }
    if (chosen != null) {
      val due = marks[chosen.id]?.let { TopicPicker.dueDate(it)?.isAfter(today) == false } == true
      return TodaysTopic(TopicPick(chosen, if (due) PickKind.REVIEW else PickKind.CONTINUE), focused = true)
    }
    val sessionDays = records.filter { it.type == EntryType.SESSION }.map { it.day }.toSet()
    val held =
      TimeOff.heldSince(timeOff, today, sessionDays)?.let { start ->
        TopicPicker.pickOn(curriculum, HabitSummaries.topicMarks(records), start)?.topic?.takeIf { marks[it.id]?.known != true }
      }
    return TodaysTopic(held?.let { TopicPick(it, PickKind.CONTINUE) } ?: picked, focused = false)
  }
}
