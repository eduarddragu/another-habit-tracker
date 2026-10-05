package dev.eduarddragu.anotherhabittracker.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.random.Random

/**
 * One read of a book, from its sessions: [started] and [lastRead] are the first and last session days
 * (null for a book marked finished without a session), [finished] the day it was marked finished.
 */
data class BookRead(
  val title: String,
  val author: String?,
  val started: LocalDate?,
  val lastRead: LocalDate?,
  val sessions: Int,
  val minutes: Int,
  val finished: LocalDate? = null,
)

/** A reading habit's books: the one on the go (if any) and the read list, newest first. */
data class Shelf(val current: BookRead?, val finished: List<BookRead>)

/** Which situation the reading card speaks to today. */
enum class BookSituation {
  /** No book on the go, nothing read today. */
  NO_BOOK,
  /** Read today, no book named. */
  READ_TODAY_UNNAMED,
  READ_TODAY,
  /** Read the current book yesterday, not today yet. */
  READ_YESTERDAY,
  /** Two days or more since the current book was opened. */
  AWAY,
  /** A book was finished today or yesterday, and nothing newer was read since. */
  JUST_FINISHED,
}

/** The reading card of the day: the book it's about (null when there is none) and one line on it. */
data class BookDay(val situation: BookSituation, val title: String?, val author: String?, val line: String)

/**
 * Books for reading habits. A session carries its book in the entry's track and the author in its
 * module; a FINISHED mark (same track and module) closes the book on its day. Sessions on the day of
 * the mark count for that read, even when logged after it; a session after that day starts a new one
 * (a re-read). Titles match ignoring case and extra spaces, and show as they were last written.
 */
object Books {
  /** A simple habit with the bookmark icon, or "read" in its name. Study keeps its own course or book field. */
  fun appliesTo(kind: HabitKind, name: String, icon: String?): Boolean =
    kind == HabitKind.SIMPLE && (icon == HabitIcon.BOOKMARK.name || name.contains("read", ignoreCase = true))

  /** How two titles are told apart: case and extra spaces don't make another book. */
  fun key(title: String): String = title.trim().replace(Regex("\\s+"), " ").lowercase()

  private class Open(var title: String, var author: String?, val started: LocalDate, var lastRead: LocalDate, var sessions: Int, var minutes: Int, var order: Int)

  fun shelf(records: List<LogRecord>): Shelf {
    val open = linkedMapOf<String, Open>()
    val finished = mutableListOf<Pair<BookRead, Int>>()
    var order = 0
    // Stable: within a day, sessions come before the marks, in the order they were logged.
    val sorted = records.sortedWith(compareBy<LogRecord> { it.day }.thenBy { if (it.type == EntryType.FINISHED) 1 else 0 })
    for (record in sorted) {
      val title = record.track?.trim()?.takeIf { it.isNotEmpty() } ?: continue
      val author = record.module?.trim()?.takeIf { it.isNotEmpty() }
      val key = key(title)
      order++
      when (record.type) {
        EntryType.SESSION -> {
          val book = open[key]
          if (book == null) {
            open[key] = Open(title, author, record.day, record.day, 1, record.minutes ?: 0, order)
          } else {
            book.title = title
            if (author != null) book.author = author
            book.lastRead = record.day
            book.sessions++
            book.minutes += record.minutes ?: 0
            book.order = order
          }
        }
        EntryType.FINISHED -> {
          val book = open.remove(key)
          if (book == null) {
            // The same book marked twice on one day: once is enough.
            if (finished.lastOrNull { key(it.first.title) == key }?.first?.finished == record.day) continue
            finished += BookRead(title, author, null, null, 0, 0, record.day) to order
          } else {
            finished += BookRead(title, author ?: book.author, book.started, book.lastRead, book.sessions, book.minutes, record.day) to order
          }
        }
        else -> Unit
      }
    }
    val current = open.values.maxWithOrNull(compareBy<Open> { it.lastRead }.thenBy { it.order })?.let { BookRead(it.title, it.author, it.started, it.lastRead, it.sessions, it.minutes) }
    return Shelf(current, finished.sortedWith(compareByDescending<Pair<BookRead, Int>> { it.first.finished }.thenByDescending { it.second }).map { it.first })
  }

  /** The situation for [today]; [doneToday] is the habit's (a session without a book counts too). */
  fun situation(shelf: Shelf, today: LocalDate, doneToday: Boolean): BookSituation {
    val current = shelf.current
    val lastFinished = shelf.finished.firstOrNull()?.takeIf { !it.finished!!.isBefore(today.minusDays(1)) }
    return when {
      lastFinished != null && (current == null || current.lastRead!!.isBefore(lastFinished.finished)) -> BookSituation.JUST_FINISHED
      current == null -> if (doneToday) BookSituation.READ_TODAY_UNNAMED else BookSituation.NO_BOOK
      doneToday || current.lastRead == today -> BookSituation.READ_TODAY
      current.lastRead == today.minusDays(1) -> BookSituation.READ_YESTERDAY
      else -> BookSituation.AWAY
    }
  }

  /**
   * Today's card: the book and one line for the situation, the same all day (seeded by the date).
   * [sessionMinutes] is the habit's usual session, which the copy asks for.
   */
  fun day(shelf: Shelf, today: LocalDate, doneToday: Boolean, sessionMinutes: Int): BookDay {
    val situation = situation(shelf, today, doneToday)
    val book = if (situation == BookSituation.JUST_FINISHED) shelf.finished.first() else shelf.current
    val pool = lines.getValue(situation)
    val away = book?.lastRead?.let { ChronoUnit.DAYS.between(it, today).toInt() } ?: 0
    val line =
      pool[Random(today.toEpochDay() * 13 + situation.ordinal).nextInt(pool.size)]
        .replace("{book}", book?.title.orEmpty())
        .replace("{minutes}", if (sessionMinutes == 1) "1 minute" else "$sessionMinutes minutes")
        .replace("{days}", dayCount(away))
    return BookDay(situation, book?.title, book?.author, line)
  }

  /** Original copy, in the app's voice. Placeholders: {book}, {minutes} ("20 minutes"), {days} ("3 days"). */
  val lines: Map<BookSituation, List<String>> =
    mapOf(
      BookSituation.READ_YESTERDAY to
        listOf(
          "{book} won't finish itself. {minutes} more today.",
          "Come on, {book} has to be finished at some point. Today works.",
          "You left off somewhere in {book} yesterday. Go find the page.",
          "Same book, another {minutes}. That's how books end.",
          "{book} is right where you left it. Pick it up.",
        ),
      BookSituation.AWAY to
        listOf(
          "{book} has been waiting {days}. It hasn't moved.",
          "{days} since you opened {book}. The bookmark is getting comfortable.",
          "Remember {book}? {minutes} and it's back on.",
          "{days} without {book}. Find it, open it, {minutes}.",
        ),
      BookSituation.READ_TODAY to
        listOf(
          "{book} got its pages today. Same again tomorrow.",
          "Read today. {book} is a little shorter now.",
          "Done for today. {book} will be there tomorrow.",
          "Today's pages are in. Don't start a second book out of enthusiasm.",
        ),
      BookSituation.READ_TODAY_UNNAMED to
        listOf(
          "Read today. Next time, tell me what.",
          "Pages turned, title unknown. Add it when you log and it goes on the list.",
        ),
      BookSituation.JUST_FINISHED to
        listOf(
          "Done with {book}. What's next?",
          "{book}: finished. The list grows by one. Pick the next.",
          "Finished {book}. Don't let the gap between books get long.",
          "One more on the read list. The next book won't pick itself.",
        ),
      BookSituation.NO_BOOK to
        listOf(
          "Pick a book. Any book. Log it with its title.",
          "No book on the go. The first page won't read itself.",
          "Grab anything with pages. Name it when you log and it goes on the list.",
          "{minutes} and a book. Any book. Say which when you log.",
        ),
    )
}
