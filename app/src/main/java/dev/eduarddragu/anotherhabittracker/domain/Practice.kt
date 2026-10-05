package dev.eduarddragu.anotherhabittracker.domain

import java.time.LocalDate
import kotlin.random.Random

/** One meditation practice: something small to try in today's session, in three short steps. */
data class Practice(val title: String, val steps: List<String>)

/**
 * Meditation's counterpart to the study topic: one practice a day, drawn from a fixed pool in a fixed
 * shuffled order, so every practice comes round once before any repeats. Original copy.
 */
object Practices {
  val all =
    listOf(
      Practice("Count ten breaths", listOf("Count each out-breath, one to ten.", "Lost count? Start again at one, no fuss.", "Getting to ten is not the point. Noticing you drifted is.")),
      Practice("Make the exhale longer", listOf("Breathe in for four.", "Breathe out for six, slowly.", "Keep it for a few rounds, then let the breath find its own pace.")),
      Practice("Settle the body first", listOf("Sit so your back holds itself up without effort.", "Drop your shoulders and unclench your jaw.", "Rest your hands where they feel heavy.")),
      Practice("Scan from head to feet", listOf("Move your attention slowly down from the top of your head.", "Stop at each part for a breath or two.", "Notice what's there. You don't have to change it.")),
      Practice("Name what pulls you away", listOf("When a thought takes you, give it a one-word label: planning, worrying, remembering.", "Say the word silently, once.", "Go back to the breath.")),
      Practice("Listen instead", listOf("Use sounds as the anchor instead of the breath.", "Let them come and go without naming the source.", "Near, far, loud, soft. Just hearing.")),
      Practice("Coming back is the practice", listOf("You will drift. Everyone does.", "Noticing you drifted is the rep.", "Come back. Repeat, as often as needed.")),
      Practice("A few minutes is enough", listOf("A short session done beats a long one skipped.", "Pick a length you can keep on a bad day.", "Let it grow on its own later.")),
      Practice("Same time, same place", listOf("Sit where you sat yesterday, if you can.", "Tie the session to something you already do, like coffee.", "Let the routine do the deciding.")),
      Practice("Eyes half open", listOf("Lower your gaze to a spot on the floor a metre ahead.", "Keep it soft, unfocused.", "Useful when closed eyes make you sleepy.")),
      Practice("Feel the breath in one place", listOf("Choose the nostrils, the chest or the belly.", "Stay with the sensation right there.", "When you wander, come back to that same place.")),
      Practice("Before the phone", listOf("Sit before you check any screen.", "Even three minutes counts.", "Notice how the rest of the morning feels.")),
      Practice("Walk slowly", listOf("Walk ten steps, slowly, and turn around.", "Feel each foot lift, move and land.", "When the mind runs ahead, feel the next step.")),
      Practice("Notice the pause", listOf("There's a short pause after each out-breath.", "Don't stretch it. Just notice it's there.", "Let the next breath start on its own.")),
      Practice("Kind wishes", listOf("Picture someone easy to like.", "Silently wish them well: may you be safe, may you be at ease.", "Then send the same wish to yourself.")),
      Practice("Pleasant, unpleasant, neutral", listOf("As sensations come up, notice their tone.", "Is it pleasant, unpleasant or neither?", "Just note it. No need to hold on or push away.")),
      Practice("Open awareness", listOf("Start with the breath for a minute.", "Then widen: sounds, body, thoughts, all at once.", "Don't pick anything. Just keep noticing.")),
      Practice("End gently", listOf("Don't jump up when the timer ends.", "Take three slow breaths and feel the room around you.", "Then get up. Slowly.")),
      Practice("Don't chase calm", listOf("Some sessions are busy. That's fine.", "The aim is to notice what's here, not to feel a certain way.", "A restless sit still counts.")),
      Practice("When you feel sleepy", listOf("Sit up a little straighter.", "Open your eyes slightly and let in some light.", "Take a few fuller breaths to wake the body.")),
      Practice("When you feel restless", listOf("Let the breath be a bit deeper for a minute.", "Feel the weight of your body on the seat.", "Count breaths until the rush settles.")),
      Practice("One thing afterwards", listOf("After the session, notice one thing you felt.", "Write it in the log note, a few words is plenty.", "Over weeks it shows you patterns.")),
      Practice("Let a guide lead", listOf("Pick a guided session in Medito today.", "Follow the voice, even if it's a bit too soothing.", "Guided and silent sessions both count.")),
      Practice("Count down", listOf("Count breaths backwards from twenty.", "Say each number on the out-breath.", "If you reach one, just keep breathing without counting.")),
      Practice("Feel your hands", listOf("Bring your attention to your hands.", "Warmth, tingling, the air on the skin.", "When the mind wanders, come back to the hands.")),
      Practice("Soften the face", listOf("Notice your forehead, eyes, jaw and tongue.", "Let each one soften on an out-breath.", "Come back to the face whenever you notice tension.")),
      Practice("Thoughts as passing traffic", listOf("Imagine sitting by a road.", "Thoughts are cars: watch them go by.", "If you find yourself in one, step back out to the side.")),
      Practice("Just this breath", listOf("Don't think about the whole session.", "Only this breath, then only the next one.", "Then the next one. That's the whole trick.")),
    )

  private val order = all.indices.shuffled(Random(PRACTICE_SEED))

  /** Today's practice; the same all day. */
  fun forDay(day: LocalDate): Practice = all[order[Math.floorMod(day.toEpochDay(), all.size.toLong()).toInt()]]

  /** Which habits get a daily practice: meditation, by name or by the app it opens. */
  fun appliesTo(name: String, linkedPackage: String?): Boolean =
    name.contains("medit", ignoreCase = true) || linkedPackage?.contains("medit", ignoreCase = true) == true

  private const val PRACTICE_SEED = 17
}
