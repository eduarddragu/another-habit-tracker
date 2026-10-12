package dev.eduarddragu.anotherhabittracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OrbitTest {
  @Test
  fun stagesStepOnMilestonesOnly() {
    assertEquals(listOf(0, 1, 1, 2, 3, 4, 8, 8), listOf(2, 3, 6, 7, 14, 30, 365, 900).map(Orbit::stage))
  }

  @Test
  fun aMoonGrowsOnTheDayItsMilestoneIsLogged() {
    assertNull(Orbit.moon(2, doneToday = true))
    assertEquals(Orbit.Moon(1, grewToday = true), Orbit.moon(3, doneToday = true))
    // Still open today: the streak runs up to yesterday, nothing grew today.
    assertEquals(Orbit.Moon(1), Orbit.moon(3, doneToday = false))
    assertEquals(Orbit.Moon(2), Orbit.moon(8, doneToday = true))
  }

  @Test
  fun aSlipFadesTheMoonForADay() {
    assertEquals(Orbit.Moon(2, fading = true), Orbit.moon(0, doneToday = false, lost = 9))
    assertNull(Orbit.moon(0, doneToday = false, lost = 2))
    // Logged again after the slip: a new streak of 1, no moon yet.
    assertNull(Orbit.moon(1, doneToday = true, lost = 9))
  }

  @Test
  fun moonsAreDescribedByTheirMilestone() {
    assertEquals("Moons: Study, 7 days; Chores, 3 days, fading", Descriptions.moons(listOf("Study" to Orbit.Moon(2), "Chores" to Orbit.Moon(1, fading = true))))
    assertNull(Descriptions.moons(emptyList()))
  }
}
