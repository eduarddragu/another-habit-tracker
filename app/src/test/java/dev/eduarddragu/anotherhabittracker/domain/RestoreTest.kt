package dev.eduarddragu.anotherhabittracker.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class RestoreTest {
  private fun entry(id: Long, habitId: Long, day: String, type: EntryType) = BackupEntry(id, habitId, day, type.name, null, null, "", "", null, null, 0, null)

  @Test
  fun aFreezeUnderASessionOfTheSameHabitIsDropped() {
    val session = entry(1, 1, "2026-10-05", EntryType.SESSION)
    val sameDayFreeze = entry(2, 1, "2026-10-05", EntryType.FREEZE)
    val otherHabitsFreeze = entry(3, 2, "2026-10-05", EntryType.FREEZE)
    val otherDayFreeze = entry(4, 1, "2026-10-06", EntryType.FREEZE)
    val known = entry(5, 1, "2026-10-05", EntryType.KNOWN)
    val finished = entry(6, 1, "2026-10-05", EntryType.FINISHED)
    assertEquals(
      listOf(session, otherHabitsFreeze, otherDayFreeze, known, finished),
      Restore.entriesToKeep(listOf(session, sameDayFreeze, otherHabitsFreeze, otherDayFreeze, known, finished)),
    )
  }
}
