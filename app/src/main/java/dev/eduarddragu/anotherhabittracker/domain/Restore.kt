package dev.eduarddragu.anotherhabittracker.domain

object Restore {
  /**
   * The entries of a backup worth restoring: a freeze that shares its day with a session of the same
   * habit is dropped, since the session replaced it.
   */
  fun entriesToKeep(entries: List<BackupEntry>): List<BackupEntry> {
    val sessionDays = entries.filter { it.type == EntryType.SESSION.name }.map { it.habitId to it.day }.toSet()
    return entries.filterNot { it.type == EntryType.FREEZE.name && (it.habitId to it.day) in sessionDays }
  }
}
