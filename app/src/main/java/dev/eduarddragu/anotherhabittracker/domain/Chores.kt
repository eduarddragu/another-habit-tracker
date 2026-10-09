package dev.eduarddragu.anotherhabittracker.domain

import java.time.LocalDate
import kotlin.random.Random

/**
 * One thing the home should look like: [title] is what to do about it, [check] the state it asks for,
 * [nudges] the questions a reminder asks about it.
 */
data class ChoreCheck(val title: String, val check: String, val nudges: List<String>)

/**
 * Today's chores card: its [motto] (the title), the check to start with, and every check in the order
 * to go through them.
 */
data class ChoresDay(val motto: String, val lead: ChoreCheck, val checks: List<ChoreCheck>)

/**
 * A chores habit: no schedule per task (the work is too irregular for one), but a few checks on how
 * the place should look. Every day starts from a different one, so the easiest never always comes
 * first; any time spent putting things right counts. Original copy.
 */
object Chores {
  val checks =
    listOf(
      ChoreCheck(
        "Clear the drying rack",
        "Nothing drying in the way, unless it has to be.",
        listOf(
          "Is the drying rack still in the middle of the room?",
          "Dry clothes on the rack are just a wardrobe with extra steps. Fold them.",
          "If it's dry, it goes away. Then the rack goes away too.",
        ),
      ),
      ChoreCheck(
        "Do the laundry",
        "No laundry waiting, in the basket or in the drum.",
        listOf(
          "How full is the basket? Honestly.",
          "Anything forgotten in the drum? Wet laundry doesn't keep.",
          "One wash now is less than three on Sunday.",
        ),
      ),
      ChoreCheck(
        "Water the plants",
        "Plants watered. Check the soil first.",
        listOf(
          "The plants can't text you. Water them.",
          "Finger in the soil. Dry? Water.",
          "When did the plants last get water? If you can't say, now.",
        ),
      ),
      ChoreCheck(
        "Clean the bedroom",
        "Bedroom clean: clothes away, surfaces and floor clear.",
        listOf(
          "Look at the bedroom chair. Is it still a chair?",
          "Clothes away, surfaces clear. The bedroom first.",
          "Whatever is lying around the bedroom has a place. Put it there.",
        ),
      ),
    )

  /** The card's title: short, one line on Home. */
  val mottos =
    listOf(
      "Clean room, clean mind.",
      "Tidy space, quiet head.",
      "Clear floor, clear head.",
      "Everything back where it lives.",
      "Small mess now, or a big one Sunday.",
      "Make it easy to come home to.",
    )

  /** Today's card, the same all day: the checks rotate by one each day, the motto on its own cycle. */
  fun day(day: LocalDate): ChoresDay {
    val start = Math.floorMod(day.toEpochDay(), checks.size.toLong()).toInt()
    val ordered = checks.indices.map { checks[(start + it) % checks.size] }
    return ChoresDay(mottos[Math.floorMod(day.toEpochDay(), mottos.size.toLong()).toInt()], ordered.first(), ordered)
  }

  /**
   * What a reminder asks on [day] at [slot]: the first one points at the check to start with, later
   * ones ask about another check each, so a day's reminders cover different corners.
   */
  fun reminderLine(day: LocalDate, slot: Int): String {
    val today = day(day)
    if (slot == 0) return "Start with this: ${today.lead.title.lowercase()}."
    val check = today.checks[slot % today.checks.size]
    return check.nudges[Random(day.toEpochDay() * 17 + slot).nextInt(check.nudges.size)]
  }

  /** A simple habit with the house icon, or "chore" in its name. */
  fun appliesTo(kind: HabitKind, name: String, icon: String?): Boolean =
    kind == HabitKind.SIMPLE && (icon == HabitIcon.HOUSE.name || name.contains("chore", ignoreCase = true))
}
