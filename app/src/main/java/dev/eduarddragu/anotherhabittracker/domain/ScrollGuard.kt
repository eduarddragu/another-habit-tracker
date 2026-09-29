package dev.eduarddragu.anotherhabittracker.domain

import java.time.LocalDate
import kotlin.random.Random

/**
 * The scroll guard: while a habit is still open today, opening a guarded app (Instagram, say) brings up
 * a screen that asks to do the habit first. It's a soft block: "Ten minutes" lets the feed through, and
 * once that time in guarded apps is used up the screen comes back, asking a little harder, with five
 * more minutes on offer each time. Once everything is logged (or frozen), the guard steps aside.
 *
 * Time is a budget for the day, counted only while a guarded app is on screen: leaving after three
 * minutes keeps seven for later. It resets when the day changes.
 */
data class ScrollBudget(val day: LocalDate, val usedMillis: Long = 0, val allowedMillis: Long = 0, val grants: Int = 0) {
  val remainingMillis: Long
    get() = (allowedMillis - usedMillis).coerceAtLeast(0)

  val exhausted: Boolean
    get() = usedMillis >= allowedMillis

  /** A fresh budget when [today] is a new day, otherwise this one. */
  fun on(today: LocalDate): ScrollBudget = if (today == day) this else ScrollBudget(today)

  fun use(millis: Long): ScrollBudget = copy(usedMillis = usedMillis + millis.coerceAtLeast(0))

  /** Lets the feed through: the first time of the day for [ScrollGuard.FIRST_GRANT], then [ScrollGuard.MORE_GRANT]. */
  fun grant(): ScrollBudget = copy(allowedMillis = usedMillis + ScrollGuard.grantMillis(grants), grants = grants + 1)
}

/** Which screen the guard shows: on opening a guarded app, or when the time let through ran out. */
enum class GuardMoment {
  OPENING,
  OVERTIME,
}

data class GuardText(val title: String, val body: String)

object ScrollGuard {
  const val FIRST_GRANT_MINUTES = 10
  const val MORE_GRANT_MINUTES = 5

  /** Suggested when the guard is first turned on and no app is chosen yet. */
  const val DEFAULT_PACKAGE = "com.instagram.android"

  fun grantMillis(grantsSoFar: Int): Long = (if (grantsSoFar == 0) FIRST_GRANT_MINUTES else MORE_GRANT_MINUTES) * 60_000L

  /** The guard only speaks while something is still open today and no time is left in the budget. */
  fun shouldBlock(anyHabitOpen: Boolean, budget: ScrollBudget): Boolean = anyHabitOpen && budget.exhausted

  /** OPENING until the feed has been let through once today, OVERTIME after. */
  fun moment(budget: ScrollBudget): GuardMoment = if (budget.grants == 0) GuardMoment.OPENING else GuardMoment.OVERTIME

  /** The way through: the first grant of the day, the second, and every one after (with a sigh). */
  fun grantLabel(budget: ScrollBudget): String =
    when (budget.grants) {
      0 -> "$FIRST_GRANT_MINUTES minutes, then I'm out"
      1 -> "Fine, $MORE_GRANT_MINUTES more"
      else -> "$MORE_GRANT_MINUTES more. I know, I know."
    }

  /**
   * What the guard is up against: what's still open ([names]), whether study is among it (the notebook
   * lines only fit study), whether anything at all is logged today, and the longest streak at stake.
   */
  data class Stakes(val names: List<String>, val studyOpen: Boolean, val anythingLogged: Boolean, val streak: Int)

  private val titles =
    mapOf(
      GuardMoment.OPENING to listOf("Not yet.", "Hold on.", "Nice try.", "Caught you.", "Put it down."),
      GuardMoment.OVERTIME to listOf("Time's up.", "Still here?", "Really?"),
    )

  // Placeholders: {s} what's open, {is}/{has} agreeing with it, {app}, {streak} the number, {days}
  // "1 day" or "12 days", {min} the minutes just used up.
  private val openingStreak =
    listOf(
      "{app} isn't going anywhere. Your {streak}-day streak might. {s} first.",
      "You opened this on autopilot. {s} first, feed after.",
      "A {streak}-day streak, still open, and you're here? {s} first.",
    )
  private val openingStreakStudy =
    listOf(
      "Yo. {days} on the line and you're here? Notebook. Agenda. Go.",
      "Your {streak}-day streak isn't safe yet. Grab the notebook and the agenda, not the feed.",
      "Put the phone down. Notebook, agenda, {s}. Then scroll all you want.",
    )
  private val openingNoStreak =
    listOf(
      "{s} {has}n't happened yet today. {app} can wait.",
      "You opened this on autopilot. {s} first, feed after.",
      "{app} can wait. {s} can't.",
    )
  private val openingNothingLogged = listOf("Nothing logged today. {app} can wait. {s} can't.")
  private val overtimeStreak =
    listOf(
      "{min} minutes, as promised. You didn't keep it. {s} {is} still waiting.",
      "Still scrolling? {s} {is} still open. So is a {streak}-day streak.",
      "That's plenty of doom. {s} now, and your {streak}-day streak lives.",
    )
  private val overtimeNoStreak =
    listOf(
      "That's plenty of doom. {s} {is} still waiting.",
      "{min} minutes, as promised. You didn't keep it. {s} now.",
    )
  private val overtimeStudy = listOf("Still scrolling? The notebook's getting lonely.", "The notebook hasn't moved. Neither have you.")

  /**
   * The guard's words for [moment]. Seeded by day and by how many times the feed was let through, so
   * the screen keeps its wording if it comes back for the same reason.
   */
  fun text(moment: GuardMoment, stakes: Stakes, appName: String, day: LocalDate, grants: Int): GuardText {
    val random = Random(day.toEpochDay() * 17 + grants)
    val streak = stakes.streak > 0
    val pool =
      when (moment) {
        GuardMoment.OPENING ->
          when {
            streak -> openingStreak + if (stakes.studyOpen) openingStreakStudy else emptyList()
            !stakes.anythingLogged -> openingNoStreak + openingNothingLogged
            else -> openingNoStreak
          }
        GuardMoment.OVERTIME -> (if (streak) overtimeStreak else overtimeNoStreak) + if (stakes.studyOpen) overtimeStudy else emptyList()
      }
    val plural = stakes.names.size > 1
    val body =
      pool
        .random(random)
        .replace("{s}", subject(stakes.names))
        .replace("{is}", if (plural) "are" else "is")
        .replace("{has}", if (plural) "have" else "has")
        .replace("{app}", appName)
        .replace("{streak}", stakes.streak.toString())
        .replace("{days}", dayCount(stakes.streak))
        .replace("{min}", (if (grants <= 1) FIRST_GRANT_MINUTES else MORE_GRANT_MINUTES).toString())
    return GuardText(titles.getValue(moment).random(random), body.replaceFirstChar { it.uppercase() })
  }

  /** "Study", "Study and Meditation", "Study, Reading and Meditation". */
  fun subject(names: List<String>): String =
    when (names.size) {
      0 -> ""
      1 -> names[0]
      else -> names.dropLast(1).joinToString(", ") + " and " + names.last()
    }
}
