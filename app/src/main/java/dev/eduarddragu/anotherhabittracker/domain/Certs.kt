package dev.eduarddragu.anotherhabittracker.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Studying for a certification: from the day it's chosen to the exam, the study habit's topic is the
 * cert itself (no topic a day: the course sets the order), and its first line counts down to the exam.
 * Passing it marks the cert's topics as known.
 */
object Certs {
  /** Today's pick while studying for [cert]: the countdown leads its lines. */
  fun pick(cert: Cert, examDate: LocalDate?, today: LocalDate): TopicPick =
    TopicPick(cert.topic.copy(hints = listOf(countdown(examDate, today)) + cert.hints), PickKind.CERT)

  fun countdown(examDate: LocalDate?, today: LocalDate): String {
    if (examDate == null) return "No exam date yet."
    val days = ChronoUnit.DAYS.between(today, examDate)
    return when {
      days > 1 -> "$days days to the exam."
      days == 1L -> "The exam is tomorrow."
      days == 0L -> "Exam day."
      else -> "How did the exam go?"
    }
  }

  /** From the exam's day on, the card asks how it went. */
  fun examReached(examDate: LocalDate?, today: LocalDate): Boolean = examDate != null && !today.isBefore(examDate)
}
