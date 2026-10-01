package dev.eduarddragu.anotherhabittracker.domain

/** What the scroll guard's service should do after an event, in order. */
sealed interface GuardAction {
  /** Add [millis] of guarded time to today's budget. */
  data class Use(val millis: Long) : GuardAction

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
 * budget starts over on time.
 */
class GuardTracker(private val isGuarded: (String) -> Boolean, private val now: () -> Long) {
  var front: String? = null
    private set

  var generation = 0
    private set

  private var screenOn = true
  private var countingSince: Long? = null

  val counting: Boolean
    get() = countingSince != null

  /** When the last window change arrived (event time), to tell late scroll events apart. */
  private var frontSince = Long.MIN_VALUE

  /** A window change: [pkg] is in front now. The same app again changes nothing. */
  fun onFront(pkg: String, eventTime: Long = now()): List<GuardAction> {
    if (pkg == front) return emptyList()
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
    if (pkg == front || !isGuarded(pkg) || eventTime < frontSince + SCROLL_GRACE) return emptyList()
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
   * The outcome of [GuardAction.Evaluate]: whether anything is still open today, the time left in the
   * budget, and how long until midnight.
   */
  fun onEvaluated(generation: Int, anyHabitOpen: Boolean, budget: ScrollBudget, millisToMidnight: Long): List<GuardAction> {
    if (generation != this.generation || counting) return emptyList()
    val pkg = front ?: return emptyList()
    if (!screenOn || !isGuarded(pkg) || !anyHabitOpen) return emptyList()
    if (ScrollGuard.shouldBlock(anyHabitOpen, budget)) return listOf(GuardAction.Block(pkg))
    countingSince = now()
    return listOf(GuardAction.Schedule(minOf(budget.remainingMillis, millisToMidnight + MIDNIGHT_MARGIN)))
  }

  private fun evaluate(): List<GuardAction> {
    val pkg = front ?: return emptyList()
    if (!screenOn || !isGuarded(pkg)) return emptyList()
    return listOf(GuardAction.Evaluate(generation))
  }

  /** Ends counting, if it was running, and invalidates evaluations still on their way. */
  private fun stop(): List<GuardAction> {
    generation++
    val since = countingSince ?: return listOf(GuardAction.CancelTimer)
    countingSince = null
    return listOf(GuardAction.CancelTimer, GuardAction.Use(now() - since))
  }

  private companion object {
    /** Past midnight by a little, so the day has turned when the timer looks again. */
    const val MIDNIGHT_MARGIN = 2_000L

    /** How long after a window change a scroll from another app is still taken as a leftover. */
    const val SCROLL_GRACE = 500L
  }
}
