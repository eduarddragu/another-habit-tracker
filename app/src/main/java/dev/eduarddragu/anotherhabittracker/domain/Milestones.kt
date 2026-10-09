package dev.eduarddragu.anotherhabittracker.domain

/**
 * Streak lengths worth a moment of their own: a line instead of the usual one, on the habit's page
 * and on Home, on the day the streak reaches it. Only these, and quietly: no confetti.
 */
object Milestones {
  private val lines =
    mapOf(
      3 to "Three days. That's a pattern now.",
      7 to "A week straight. That's a habit starting.",
      14 to "Two weeks. The excuses are running out of material.",
      30 to "Thirty days. This is just what you do now.",
      50 to "Fifty days. Halfway to three digits.",
      100 to "A hundred days. Three digits. Take a second.",
      200 to "Two hundred days. Nobody asked, you did it anyway.",
      365 to "A year. Every single day of it.",
    )

  /** The milestone line when [streak] is exactly one of them, else null. */
  fun line(streak: Int): String? = lines[streak]
}
