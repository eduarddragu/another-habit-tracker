package dev.eduarddragu.anotherhabittracker.domain

import java.time.LocalDate

/** What the scroll guard's service should do after an event, in order. */
sealed interface GuardAction {
  /** Add [millis] of guarded time to the budget of [day]. */
  data class Use(val day: LocalDate, val millis: Long) : GuardAction

  /** Stop the running timer, if any. */
  data object CancelTimer : GuardAction

  /** Load what's open and the budget, then call [GuardTracker.onEvaluated] with [generation]. */
  data class Evaluate(val generation: Int) : GuardAction

  /** Show the guard over [pkg]. */
  data class Block(val pkg: String) : GuardAction

  /** Call [GuardTracker.onTimeUp] after [millis]. */
  data class Schedule(val millis: Long) : GuardAction
}

/**
 * The scroll guard's bookkeeping, apart from Android so it can be tested: which app is in front,
 * whether the screen is on, and since when guarded time is being counted.
 *
 * Every event that changes the picture bumps [generation]; an evaluation started before it (it loads
 * the statuses off the main thread) is dropped when it comes back, so it can't start counting for an
 * app that's gone or a screen that's off. Counting only runs while a guarded app is in front, the
 * screen is on and something is still open today; the timer never runs past midnight, so the day's
 * budget starts over on time. Time counted across midnight is split between the two days.
 */
class GuardTracker(private val isGuarded: (String) -> Boolean, private val now: () -> Long) {
  var front: String? = null
    private set

  var generation = 0
    private set

  private var screenOn = true
  private var countingSince: Long? = null

  /** The day being counted for, and when (on the [now] clock) it ends. */
  private var countingDay: LocalDate? = null
  private var countingDayEnds = Long.MAX_VALUE

  val counting: Boolean
    get() = countingSince != null

  /**
   * The guard went up over [front]. Its own window is ignored, so [front] stays the guarded app: coming
   * back to it (a notification from the shade, the quick switch) must be looked at again, not taken
   * as the same app still in front.
   */
  private var blocked = false

  /** When the last window change arrived (event time), to tell late scroll events apart. */
  private var frontSince = Long.MIN_VALUE

  /** A window change: [pkg] is in front now. The same app again changes nothing, unless it was blocked. */
  fun onFront(pkg: String, eventTime: Long = now()): List<GuardAction> {
    if (pkg == front && !blocked) return emptyList()
    blocked = false
    val stopped = stop()
    front = pkg
    frontSince = eventTime
    return stopped + evaluate()
  }

  /**
   * A guarded app scrolled: it's in front even if no window change said so (back through Recents).
   * Scroll events are throttled and can arrive after the window change that left the app (a fling,
   * then Home), so one from before that change, or just after it, is ignored.
   */
  fun onScrolled(pkg: String, eventTime: Long): List<GuardAction> {
    if ((pkg == front && !blocked) || !isGuarded(pkg) || eventTime < frontSince + SCROLL_GRACE) return emptyList()
    return onFront(pkg, eventTime)
  }

  fun onScreenOff(): List<GuardAction> {
    screenOn = false
    return stop()
  }

  fun onScreenOn(): List<GuardAction> {
    screenOn = true
    return stop() + evaluate()
  }

  /** The timer went off, the day changed, or a grant was saved: look again. */
  fun onTimeUp(): List<GuardAction> = stop() + evaluate()

  fun onGrant(): List<GuardAction> = onTimeUp()

  fun onDayChanged(): List<GuardAction> = onTimeUp()

  /**
   * The outcome of [GuardAction.Evaluate]: whether anything is still open today, the time left in
   * today's budget (its day is the one counted for), and how long until midnight.
   */
  fun onEvaluated(generation: Int, anyHabitOpen: Boolean, budget: ScrollBudget, millisToMidnight: Long): List<GuardAction> {
    if (generation != this.generation || counting) return emptyList()
    val pkg = front ?: return emptyList()
    if (!screenOn || !isGuarded(pkg) || !anyHabitOpen) return emptyList()
    if (ScrollGuard.shouldBlock(anyHabitOpen, budget)) {
      blocked = true
      return listOf(GuardAction.Block(pkg))
    }
    blocked = false
    val start = now()
    countingSince = start
    countingDay = budget.day
    countingDayEnds = start + millisToMidnight
    return listOf(GuardAction.Schedule(minOf(budget.remainingMillis, millisToMidnight + MIDNIGHT_MARGIN)))
  }

  private fun evaluate(): List<GuardAction> {
    val pkg = front ?: return emptyList()
    if (!screenOn || !isGuarded(pkg)) return emptyList()
    return listOf(GuardAction.Evaluate(generation))
  }

  /**
   * Ends counting, if it was running, and invalidates evaluations still on their way. Time past
   * midnight goes to the next day (the timer looks again just after it, so there is never more).
   */
  private fun stop(): List<GuardAction> {
    generation++
    val since = countingSince ?: return listOf(GuardAction.CancelTimer)
    val day = countingDay!!
    countingSince = null
    countingDay = null
    val end = now()
    val midnight = countingDayEnds.coerceIn(since, maxOf(since, end))
    return listOfNotNull(
      GuardAction.CancelTimer,
      GuardAction.Use(day, midnight - since).takeIf { midnight > since },
      GuardAction.Use(day.plusDays(1), end - midnight).takeIf { end > midnight },
    )
  }

  private companion object {
    /** Past midnight by a little, so the day has turned when the timer looks again. */
    const val MIDNIGHT_MARGIN = 2_000L

    /** How long after a window change a scroll from another app is still taken as a leftover. */
    const val SCROLL_GRACE = 500L
  }
}
