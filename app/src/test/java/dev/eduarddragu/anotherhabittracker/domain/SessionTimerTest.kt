package dev.eduarddragu.anotherhabittracker.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionTimerTest {
  private val min = 60_000L
  private val day = LocalDate.of(2026, 10, 1)
  private val start = 1_000_000L
  private fun session(minutes: Int = 45) = SessionTimer.start(1, "Study", "t", day, minutes, start)

  @Test
  fun countsDownAndFinishesByItself() {
    val s = session()
    assertEquals(SessionPhase.RUNNING, s.phase(start + 10 * min))
    assertEquals(35 * min, s.remaining(start + 10 * min))
    assertEquals(SessionPhase.FINISHED, s.phase(start + 45 * min))
    assertEquals(45, s.minutesToLog(start + 50 * min))
  }

  @Test
  fun pausesDontCount() {
    val paused = session().pause(start + 10 * min)
    assertEquals(35 * min, paused.remaining(start + 30 * min))
    val resumed = paused.resume(start + 30 * min)
    assertEquals(35 * min, resumed.remaining(start + 30 * min))
    assertEquals(SessionPhase.FINISHED, resumed.phase(start + 65 * min))
  }

  @Test
  fun endingEarlyLogsWholeMinutesDone() {
    val ended = session().finish(start + 23 * min + 40_000)
    assertEquals(SessionPhase.FINISHED, ended.phase(start + 99 * min))
    assertEquals(23, ended.minutesToLog(start + 99 * min))
    assertNull("too short", session().finish(start + 4 * min).minutesToLog(start + 5 * min))
  }

  @Test
  fun aLateFinishNeverCountsExtraTime() {
    val ended = session(30).finish(start + 50 * min)
    assertEquals(30, ended.minutesToLog(start + 60 * min))
  }

  @Test
  fun fiveMoreMinutesWhileRunningOrAfterTheEnd() {
    assertEquals(40 * min, session().extend(start + 10 * min).remaining(start + 10 * min))
    val over = session(30)
    val extended = over.extend(start + 40 * min)
    assertEquals(SessionPhase.RUNNING, extended.phase(start + 40 * min))
    assertEquals(5 * min, extended.remaining(start + 40 * min))
    assertEquals(35, extended.finish(start + 50 * min).minutesToLog(start + 50 * min))
  }

  @Test
  fun displayRoundsUpThenShowsSeconds() {
    val s = session()
    assertEquals(SessionTimer.Display(45, false), s.display(start))
    assertEquals(SessionTimer.Display(45, false), s.display(start + 1_000))
    assertEquals(SessionTimer.Display(59, true), s.display(start + 44 * min + 1_500))
  }

  @Test
  fun oneTapLengths() {
    assertEquals(30, SessionTimer.defaultMinutes(HabitKind.STUDY))
    assertEquals(5, SessionTimer.defaultMinutes(HabitKind.SIMPLE))
    assertEquals(20, SessionTimer.defaultMinutes(HabitKind.SIMPLE, 20))
  }

  @Test
  fun presetsFollowTheHabitsOwnLength() {
    assertEquals(listOf(30, 45, 60), SessionTimer.presets(HabitKind.STUDY))
    assertEquals(listOf(5, 10, 15), SessionTimer.presets(HabitKind.SIMPLE))
    assertEquals(listOf(10, 20, 30), SessionTimer.presets(HabitKind.SIMPLE, 20))
    assertEquals(listOf(15, 30, 45), SessionTimer.presets(HabitKind.STUDY, 30))
    assertEquals(listOf(5, 10), SessionTimer.presets(HabitKind.SIMPLE, 5))
  }

  @Test
  fun customMinutesAreWholeMinutesUpToADay() {
    assertEquals(120, SessionTimer.customMinutes(" 120 "))
    assertEquals(1440, SessionTimer.customMinutes("1440"))
    assertNull(SessionTimer.customMinutes(""))
    assertNull(SessionTimer.customMinutes("0"))
    assertNull(SessionTimer.customMinutes("1441"))
    assertNull(SessionTimer.customMinutes("1.5"))
  }

  @Test
  fun doneRepeatsOnlyAUsualLength() {
    assertEquals(10, SessionTimer.doneMinutes(10, HabitKind.SIMPLE))
    assertEquals(15, SessionTimer.doneMinutes(120, HabitKind.SIMPLE, 15))
    assertEquals("a focus session's real length", 25, SessionTimer.doneMinutes(25, HabitKind.SIMPLE, 20))
    assertEquals(5, SessionTimer.doneMinutes(37, HabitKind.SIMPLE))
    assertNull(SessionTimer.doneMinutes(null, HabitKind.SIMPLE, 15))
  }

  @Test
  fun aShortSessionCountsFromHalfItsLength() {
    assertEquals(5, SessionTimer.minimumMinutes(30 * min))
    assertEquals(2, SessionTimer.minimumMinutes(5 * min))
    assertEquals(1, SessionTimer.minimumMinutes(1 * min))
    val meditation = SessionTimer.start(2, "Meditation", null, day, 5, start)
    assertEquals("ended at 4:55", 4, meditation.finish(start + 4 * min + 55_000).minutesToLog(start + 6 * min))
    assertNull(meditation.finish(start + 1 * min + 30_000).minutesToLog(start + 6 * min))
  }

  @Test
  fun eachKindOfSessionHasItsOwnWords() {
    val study = session()
    val meditation = SessionTimer.start(2, "Meditation", null, day, 5, start, SessionKind.MEDITATION)
    assertTrue(SessionTimer.line(study, start + min) in listOf("Phone down. Pen up.", "I keep time. You keep going.", "Checking the scope is allowed. Scrolling isn't.", "The questions are below. The answers go on paper."))
    assertTrue("no pen in a meditation", "pen" !in SessionTimer.line(meditation, start + min).lowercase())
    assertEquals("Five minutes. Finish the thought.", SessionTimer.line(study, start + 41 * min))
    // Five minutes long: the wind-down is its last third, not all of it.
    assertEquals("Almost done. Stay with the breath.", SessionTimer.line(meditation, start + 4 * min))
    assertTrue(SessionTimer.line(meditation, start + 2 * min) != "Almost done. Stay with the breath.")
    assertEquals("Time. Open your eyes slowly.", SessionTimer.line(meditation, start + 5 * min))
    assertEquals(SessionKind.STUDY, SessionKind.guess("topic"))
    assertEquals(SessionKind.CHORES, SessionKind.of(HabitKind.SIMPLE, "Chores", null, null))
    assertEquals(SessionKind.READING, SessionKind.of(HabitKind.SIMPLE, "Reading", null, null))
  }

  @Test
  fun resumeAfterAPausedEndKeepsTheTime() {
    val ended = session().pause(start + 25 * min).finish(start + 26 * min)
    val stale = ended.resume(start + 40 * min)
    assertEquals(25, stale.minutesToLog(start + 41 * min))
  }

  @Test
  fun extendAfterAnEarlyEndGivesFiveMore() {
    val ended = session().finish(start + 20 * min)
    val more = ended.extend(start + 30 * min)
    assertEquals(5 * min, more.remaining(start + 30 * min))
    assertEquals(20 * min, more.elapsed(start + 30 * min))
  }

  @Test
  fun finishIsIdempotent() {
    val once = session().finish(start + 10 * min)
    assertEquals(once, once.finish(start + 20 * min))
  }

  @Test
  fun lastMinuteBoundaries() {
    assertEquals(SessionTimer.Display(1, false), session().display(start + 44 * min))
    assertEquals(SessionTimer.Display(60, true), session().display(start + 44 * min + 1))
  }

  @Test
  fun midnightEndsOnlyWhatIsLeftBehind() {
    val tomorrow = day.plusDays(1)
    // Started at 23:50: still running at 00:00:30, so it carries on and ends on its own alarm.
    assertEquals(false, session(30).endsAtMidnight(tomorrow, start + 10 * min))
    assertEquals(true, session(30).pause(start + 5 * min).endsAtMidnight(tomorrow, start + 10 * min))
    assertEquals("alarm lost", true, session(30).endsAtMidnight(tomorrow, start + 40 * min))
    assertEquals("today's", false, session(30).pause(start).endsAtMidnight(day, start + 10 * min))
  }

  // A reboot: saved at [savedElapsed] on the old boot's clock, read [later] wall time after, [sinceBoot] into the new boot.
  private fun afterReboot(saved: FocusSession, savedElapsed: Long, later: Long, sinceBoot: Long = 2 * min): FocusSession {
    val wall = 1_800_000_000_000L
    return saved.shifted(SessionTimer.rebootShift(sameBoot = false, savedWall = wall, savedElapsed = savedElapsed, nowWall = wall + later, nowElapsed = sinceBoot))
  }

  @Test
  fun aRebootKeepsTheTimeThatPassed() {
    val s = afterReboot(session(30), savedElapsed = start, later = 10 * min)
    assertEquals(SessionPhase.RUNNING, s.phase(2 * min))
    assertEquals(20 * min, s.remaining(2 * min))
  }

  @Test
  fun aRebootPastTheEndFinishesItCappedAtItsLength() {
    val s = afterReboot(session(30), savedElapsed = start, later = 40 * min)
    assertEquals(SessionPhase.FINISHED, s.phase(2 * min))
    assertEquals(30 * min, s.elapsed(2 * min))
    assertEquals(30, s.minutesToLog(2 * min))
  }

  @Test
  fun aRebootKeepsAPausedSessionsRemainingTime() {
    val paused = session(30).pause(start + 5 * min)
    val s = afterReboot(paused, savedElapsed = start + 5 * min, later = 60 * min)
    assertEquals(SessionPhase.PAUSED, s.phase(2 * min))
    assertEquals(25 * min, s.remaining(2 * min))
    assertEquals(25 * min, s.resume(10 * min).remaining(10 * min))
  }

  @Test
  fun theSameBootDoesntShift() {
    assertEquals(0L, SessionTimer.rebootShift(sameBoot = true, savedWall = 0, savedElapsed = start, nowWall = 60 * min, nowElapsed = start + 60 * min))
    assertEquals(session(), session().shifted(0))
  }
}
