package dev.eduarddragu.anotherhabittracker.domain

import java.time.LocalDate
import kotlin.random.Random

/** What the study history says about one topic on one day. */
data class TopicMark(val topicId: String, val day: LocalDate, val score: Int, val known: Boolean = false)

enum class PickKind {
  NEW,
  REVIEW,
  /** Chosen by hand to keep going on the last session's topic: a deep dive, over the picker. */
  CONTINUE,
}

/** A study session as the "keep going" rule sees it. */
data class StudiedOn(val day: LocalDate, val topicId: String?)

object Continuation {
  /** How far back a session still counts as "the one I was in the middle of". */
  private const val WITHIN_DAYS = 2L

  /**
   * The topic worth offering to keep going on today: the most recent session's, from yesterday or the
   * day before, when nothing is logged yet today and it isn't today's topic anyway. [sessions] newest first.
   */
  fun candidate(sessions: List<StudiedOn>, today: LocalDate, currentPick: String?): String? {
    if (sessions.any { it.day == today }) return null
    val last = sessions.firstOrNull { it.topicId != null && it.day.isBefore(today) } ?: return null
    if (last.day.isBefore(today.minusDays(WITHIN_DAYS))) return null
    return last.topicId?.takeIf { it != currentPick }
  }
}

data class TopicPick(val topic: Topic, val kind: PickKind)

enum class TopicState {
  LOCKED,
  AVAILABLE,
  WEAK,
  DONE,
  KNOWN,
}

/**
 * Chooses the day's study topic from the prerequisite graph:
 * 1. a topic scored below [UNLOCK_SCORE] comes back first once its review is due;
 * 2. otherwise usually a new topic whose prerequisites all scored [UNLOCK_SCORE]+, weighted by area and
 *    avoiding the areas of the last [RECENT_SESSIONS] sessions;
 * 3. about one day in five ([REVIEW_CHANCE]) a due review wins over a new topic.
 *
 * The pick is deterministic for a given day and history, so every screen and reminder agree.
 */
object TopicPicker {
  const val UNLOCK_SCORE = 3
  private val REVIEW_AFTER_DAYS = mapOf(1 to 2L, 2 to 4L, 3 to 14L, 4 to 30L, 5 to 90L)

  private val SCORE_MEANINGS = mapOf(1 to "Didn't get it", 2 to "Partially", 3 to "Could explain the gist", 4 to "Could explain it properly", 5 to "Could teach it")

  /**
   * What a score does to the topic, in one line for the log form, built from the same rules the
   * picker uses so the two can't drift apart.
   */
  fun scoreMeaning(score: Int): String {
    val days = REVIEW_AFTER_DAYS.getValue(score.coerceIn(1, 5))
    val effect = if (score < UNLOCK_SCORE) "Back in $days days, unlocks nothing." else if (score == UNLOCK_SCORE) "Unlocks what's next, review in $days days." else "Review in $days days."
    return "${SCORE_MEANINGS.getValue(score.coerceIn(1, 5))}. $effect"
  }
  private const val REVIEW_CHANCE = 0.2
  private const val RECENT_SESSIONS = 2
  private const val RECENT_AREA_PENALTY = 0.25

  /** Latest mark per topic from sessions before [day], plus every "known" mark (effective at once). */
  fun latest(marks: List<TopicMark>, day: LocalDate): Map<String, TopicMark> =
    marks
      .filter { it.day.isBefore(day) || it.known }
      .sortedWith(compareBy<TopicMark> { it.day }.thenBy { it.known })
      .associateBy { it.topicId }

  fun isUnlocked(topic: Topic, latest: Map<String, TopicMark>): Boolean =
    topic.requires.all { req -> latest[req]?.let { it.score >= UNLOCK_SCORE } == true }

  fun dueDate(mark: TopicMark): LocalDate? = if (mark.known) null else mark.day.plusDays(REVIEW_AFTER_DAYS.getValue(mark.score.coerceIn(1, 5)))

  /** A topic whose review date has come: when it came, and the last score it had. */
  data class DueReview(val topic: Topic, val due: LocalDate, val score: Int)

  /**
   * Every topic due for review on [day], the weakest and longest-waiting first: what the picker
   * brings back one at a time, shown in full so a review can be chosen by hand.
   */
  fun dueReviews(curriculum: Curriculum, marks: List<TopicMark>, day: LocalDate): List<DueReview> {
    val latest = latest(marks, day)
    return curriculum.topics
      .mapNotNull { topic -> latest[topic.id]?.let { mark -> dueDate(mark)?.takeIf { !it.isAfter(day) }?.let { DueReview(topic, it, mark.score) } } }
      .sortedWith(compareBy<DueReview> { it.score >= UNLOCK_SCORE }.thenBy { it.due })
  }

  /**
   * The pick as it was on [day], for a session logged late ("yesterday"): only what was known by then
   * counts. A topic marked as known today must not change yesterday's topic.
   */
  fun pickOn(curriculum: Curriculum, marks: List<TopicMark>, day: LocalDate): TopicPick? =
    pick(curriculum, marks.filter { it.day.isBefore(day) || (it.known && !it.day.isAfter(day)) }, day)

  fun pick(curriculum: Curriculum, marks: List<TopicMark>, day: LocalDate, only: Set<String>? = null): TopicPick? {
    val random = Random(day.toEpochDay() * 7919 + 30)
    val roll = random.nextDouble()
    val latest = latest(marks, day)
    val candidates = curriculum.topics.filter { only == null || it.id in only }

    val due =
      candidates
        .mapNotNull { topic -> latest[topic.id]?.let { mark -> dueDate(mark)?.takeIf { !it.isAfter(day) }?.let { Triple(it, topic, mark) } } }
        .sortedBy { it.first }
    due.firstOrNull { it.third.score < UNLOCK_SCORE }?.let { return TopicPick(it.second, PickKind.REVIEW) }

    val fresh = candidates.filter { it.id !in latest && isUnlocked(it, latest) }
    val plainDue = if (only != null) emptyList() else due
    if (plainDue.isNotEmpty() && (fresh.isEmpty() || roll < REVIEW_CHANCE)) return TopicPick(plainDue.first().second, PickKind.REVIEW)

    if (fresh.isNotEmpty()) {
      val recentAreas =
        marks
          .filter { !it.known && it.day.isBefore(day) }
          .sortedBy { it.day }
          .takeLast(RECENT_SESSIONS)
          .mapNotNull { curriculum.byId[it.topicId]?.area }
          .toSet()
      val weights = fresh.map { (curriculum.areaById[it.area]?.weight ?: 1.0) * if (it.area in recentAreas) RECENT_AREA_PENALTY else 1.0 }
      var target = random.nextDouble() * weights.sum()
      fresh.forEachIndexed { index, topic ->
        target -= weights[index]
        if (target < 0) return TopicPick(topic, PickKind.NEW)
      }
      return TopicPick(fresh.last(), PickKind.NEW)
    }

    if (only != null) return null
    // Everything seen and nothing due: revisit the oldest topic that wasn't marked as known.
    return latest.values.filter { !it.known && it.topicId in curriculum.byId }.minByOrNull { it.day }?.let {
      TopicPick(curriculum.byId.getValue(it.topicId), PickKind.REVIEW)
    }
  }

  fun state(topic: Topic, latest: Map<String, TopicMark>): TopicState {
    val mark = latest[topic.id]
    return when {
      mark?.known == true -> TopicState.KNOWN
      mark != null && mark.score >= UNLOCK_SCORE -> TopicState.DONE
      mark != null -> TopicState.WEAK
      isUnlocked(topic, latest) -> TopicState.AVAILABLE
      else -> TopicState.LOCKED
    }
  }
}
