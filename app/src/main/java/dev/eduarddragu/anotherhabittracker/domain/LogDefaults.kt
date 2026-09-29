package dev.eduarddragu.anotherhabittracker.domain

import java.time.LocalTime

/** Choices the log form makes before he touches it. */
object LogDefaults {
  /** Until this hour, a session logged with yesterday still empty is almost always yesterday's. */
  private const val LATE_UNTIL_HOUR = 4

  /** Open on "yesterday" when it's the small hours and yesterday has nothing logged. */
  fun startOnYesterday(now: LocalTime, yesterdayLogged: Boolean): Boolean = now.hour < LATE_UNTIL_HOUR && !yesterdayLogged
}
