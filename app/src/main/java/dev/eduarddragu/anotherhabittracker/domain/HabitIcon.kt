package dev.eduarddragu.anotherhabittracker.domain

/** The line icons a habit can wear. Stored by name, so reorder freely but never rename. */
enum class HabitIcon {
  BOOK,
  LOTUS,
  PEN,
  SPARK,
  DROP,
  MOON,
  DUMBBELL;

  companion object {
    /** The stored choice, or a sensible default: a book for study, a lotus for anything linked to a meditation app. */
    fun resolve(stored: String?, kind: HabitKind, linkedPackage: String?): HabitIcon =
      entries.firstOrNull { it.name == stored }
        ?: when {
          kind == HabitKind.STUDY -> BOOK
          linkedPackage?.contains("medit", ignoreCase = true) == true -> LOTUS
          else -> SPARK
        }
  }
}
