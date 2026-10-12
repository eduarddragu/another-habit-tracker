package dev.eduarddragu.anotherhabittracker.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CertsTest {
  private val today = LocalDate.of(2026, 10, 12)
  private val cert = Cert("cert-x", "X-01", "Cert X", "a", listOf("a-root"), listOf("Which section today?"))

  @Test
  fun countdownLeadsTheLines() {
    val pick = Certs.pick(cert, LocalDate.of(2026, 12, 15), today)
    assertEquals(PickKind.CERT, pick.kind)
    assertEquals("Cert X", pick.topic.title)
    assertEquals(listOf("64 days to the exam.", "Which section today?"), pick.topic.hints)
  }

  @Test
  fun countdownAroundTheExam() {
    assertEquals("The exam is tomorrow.", Certs.countdown(today.plusDays(1), today))
    assertEquals("Exam day.", Certs.countdown(today, today))
    assertEquals("How did the exam go?", Certs.countdown(today.minusDays(3), today))
    assertFalse(Certs.examReached(today.plusDays(1), today))
    assertTrue(Certs.examReached(today, today))
    assertFalse(Certs.examReached(null, today))
  }

  @Test
  fun certsAreCheckedAgainstTheGraph() {
    val bad = Curriculum(1, listOf(Area("a", "A")), listOf(Topic("a-root", "a", "Root")), listOf(Cert("a-root", "X", "X", "z", listOf("gone"))))
    assertEquals(listOf("a-root: a cert can't share a topic's id", "a-root: unknown area 'z'", "a-root: unknown topic 'gone'"), bad.problems())
  }

  @Test
  fun aCertReadsAsATopicButIsNeverPicked() {
    val c = Curriculum(1, listOf(Area("a", "A")), listOf(Topic("a-root", "a", "Root")), listOf(cert))
    assertEquals("Cert X", c.byId.getValue("cert-x").title)
    assertEquals("a-root", TopicPicker.pick(c, emptyList(), today)!!.topic.id)
  }

  @Test
  fun extraTopicsAreReadAsTrimmedIds() = assertEquals(listOf("a-root", "b-one"), extraTopicIds(" a-root, ,b-one ,"))

  @Test
  fun aPassedCertNeverComesBackAsAReview() {
    val c = Curriculum(1, listOf(Area("a", "A")), listOf(Topic("a-root", "a", "Root")), listOf(cert))
    // Everything seen, nothing due: the oldest mark is the cert's, but only graph topics come back.
    val marks = listOf(TopicMark("cert-x", today.minusDays(5), 5), TopicMark("a-root", today.minusDays(3), 5))
    assertEquals("a-root", TopicPicker.pick(c, marks, today)!!.topic.id)
  }

  @Test
  fun aCertIsNeverAPrerequisite() {
    val c = Curriculum(1, listOf(Area("a", "A")), listOf(Topic("a-root", "a", "Root", listOf("cert-x"))), listOf(cert.copy(grants = emptyList())))
    assertEquals(listOf("a-root: unknown prerequisite 'cert-x'"), c.problems())
  }
}
