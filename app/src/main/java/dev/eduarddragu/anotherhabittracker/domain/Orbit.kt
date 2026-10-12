package dev.eduarddragu.anotherhabittracker.domain

/**
 * The planet's moons on Home: one per habit with a streak of at least the first milestone, so every
 * habit's progress shows apart from the others. A moon grows only on milestones (3, 7, 14, 30 days...),
 * so each step is noticeable, and the day after a slip it fades instead of vanishing at once.
 */
object Orbit {
  /** [stage] runs from 1 (3 days) to Milestones.days.size; [grewToday] when today's log reached it. */
  data class Moon(val stage: Int, val grewToday: Boolean = false, val fading: Boolean = false)

  /** How many milestones [streak] has passed: 0 below the first one. */
  fun stage(streak: Int): Int = Milestones.days.count { streak >= it }

  /** The habit's moon today, or null. [lost] is the streak that ended with yesterday's slip. */
  fun moon(streak: Int, doneToday: Boolean, lost: Int = 0): Moon? =
    when {
      stage(streak) > 0 -> Moon(stage(streak), grewToday = doneToday && streak in Milestones.days)
      streak == 0 && stage(lost) > 0 -> Moon(stage(lost), fading = true)
      else -> null
    }
}
