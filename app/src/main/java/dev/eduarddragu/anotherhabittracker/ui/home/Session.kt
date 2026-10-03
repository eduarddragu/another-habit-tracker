package dev.eduarddragu.anotherhabittracker.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import dev.eduarddragu.anotherhabittracker.theme.Motion
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import dev.eduarddragu.anotherhabittracker.HabitApp
import dev.eduarddragu.anotherhabittracker.data.HabitStatus
import dev.eduarddragu.anotherhabittracker.domain.Curriculum
import dev.eduarddragu.anotherhabittracker.domain.FocusSession
import dev.eduarddragu.anotherhabittracker.domain.SessionPhase
import dev.eduarddragu.anotherhabittracker.domain.SessionTimer
import dev.eduarddragu.anotherhabittracker.reminders.Sessions
import dev.eduarddragu.anotherhabittracker.theme.NumeralsDisplay
import dev.eduarddragu.anotherhabittracker.ui.components.DayCard
import dev.eduarddragu.anotherhabittracker.ui.components.NumberedSteps
import dev.eduarddragu.anotherhabittracker.ui.components.TextAction
import dev.eduarddragu.anotherhabittracker.ui.components.cardOutline
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

/** The disc stays butter in both themes, so the numbers on it are always dark ink. */
private val DiscInk = Color(0xFF1A1714)

private val ENDS_FORMAT = DateTimeFormatter.ofPattern("HH:mm")

/**
 * The session's clock, ticking once a second while Home is on screen (never between frames: the arc
 * moves a fraction of a dp per second). Finishes the session the moment it runs out, without waiting
 * for the alarm.
 */
@Composable
fun rememberSessionTick(app: HabitApp, session: FocusSession?): State<Long> {
  val lifecycle = LocalLifecycleOwner.current.lifecycle
  return produceState(app.sessions.now(), session) {
    if (session == null) return@produceState
    lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
      while (true) {
        val now = app.sessions.now()
        value = now
        if (session.finishedAt == null && session.pausedAt == null && session.remaining(now) <= 0) {
          Sessions.end(app)
          return@repeatOnLifecycle
        }
        delay(1_000 - (now - session.startedAt) % 1_000)
      }
    }
  }
}

/** When the session will end, as a clock time: "16:40". */
fun endsAtClock(session: FocusSession, now: Long): String =
  Instant.ofEpochMilli(System.currentTimeMillis() + session.remaining(now)).atZone(ZoneId.systemDefault()).toLocalTime().format(ENDS_FORMAT)

/** The screen stays on while a session is running and Home is showing: the phone is the clock on the desk. */
@Composable
fun KeepScreenOn(on: Boolean) {
  val view = LocalView.current
  DisposableEffect(on) {
    view.keepScreenOn = on
    onDispose { view.keepScreenOn = false }
  }
}

/** On the disc: minutes left (seconds in the last one), or the minutes done once it's over. */
@Composable
fun SessionNumerals(session: FocusSession, now: Long, modifier: Modifier = Modifier) {
  val display = session.display(now)
  val caption =
    when (session.phase(now)) {
      SessionPhase.PAUSED -> "PAUSED"
      SessionPhase.FINISHED -> "MIN DONE"
      SessionPhase.RUNNING -> if (display.seconds) "SEC LEFT" else "MIN LEFT"
    }
  // Paused, the number dims (the grey arc and the header say why); the caption stays dark ink, since
  // accent on butter is too faint for 10sp.
  val dim by animateFloatAsState(if (session.phase(now) == SessionPhase.PAUSED) 0.45f else 1f, tween(Motion.LONG, easing = Motion.EaseUi), label = "dim")
  Column(modifier.semantics(mergeDescendants = true) {}, horizontalAlignment = Alignment.CenterHorizontally) {
    // A fixed width, drawn from the right: DM Sans has no tabular figures, so seconds would jiggle.
    Box(Modifier.width(96.dp), contentAlignment = Alignment.Center) {
      Text(display.value.toString(), style = NumeralsDisplay, color = DiscInk.copy(alpha = dim), textAlign = TextAlign.Center)
    }
    Text(caption, style = MaterialTheme.typography.labelSmall, color = DiscInk.copy(alpha = 0.7f))
  }
}

/** Tapping the planet: one button, the session starts on it (a stray tap on the planet alone starts nothing). */
@Composable
fun StartSession(minutes: Int, onStart: () -> Unit, modifier: Modifier = Modifier) {
  OutlinedButton(onClick = onStart, modifier = modifier, shape = MaterialTheme.shapes.medium, border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)) {
    Text("Start $minutes min", color = MaterialTheme.colorScheme.primary)
  }
}

/**
 * Under the planet while a session runs: today's topic and its questions, always in view, so checking
 * the scope never means leaving the timer. Then the controls, or Log once it's over.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SessionCard(
  app: HabitApp,
  session: FocusSession,
  now: Long,
  status: HabitStatus?,
  curriculum: Curriculum,
  onLog: (habitId: Long, minutes: Int?, day: Long) -> Unit,
  /** A tap on the card opens the habit's page, like the habit cards do. */
  onOpen: (Long) -> Unit,
  modifier: Modifier = Modifier,
) {
  val phase = session.phase(now)
  val topic = session.topicId?.let { curriculum.byId[it] }
  // Warm only for a session that counted; one too short to log stays plain.
  DayCard(done = phase == SessionPhase.FINISHED && session.minutesToLog(now) != null, modifier = modifier.clip(MaterialTheme.shapes.large).clickable(onClickLabel = "Open ${session.habitName}") { onOpen(session.habitId) }) {
    val state = when {
      phase != SessionPhase.FINISHED -> "NOW"
      session.minutesToLog(now) != null -> "DONE"
      else -> "TOO SHORT"
    }
    val label = listOfNotNull(status?.habit?.name?.uppercase() ?: session.habitName.uppercase(), state, topic?.area?.let { curriculum.areaById[it]?.name?.uppercase() }).joinToString(" · ")
    Text(
      buildAnnotatedString {
        label.split(" · ").forEachIndexed { i, part ->
          if (i > 0) append(" · ")
          if (part == state) withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) { append(part) } else append(part)
        }
      },
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(topic?.title ?: (status?.habit?.name ?: "Session"), style = MaterialTheme.typography.headlineSmall)
    topic?.hints?.takeIf { it.isNotEmpty() }?.let { NumberedSteps(it, startDelay = 120L) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
      when (phase) {
        SessionPhase.RUNNING -> {
          OutlinedButton(onClick = { Sessions.pause(app) }, shape = MaterialTheme.shapes.medium, border = cardOutline()) { Text("Pause") }
          TextAction("+5 min", onClick = { Sessions.extend(app) }, modifier = Modifier.padding(horizontal = 12.dp))
          TextAction("End", onClick = { Sessions.end(app) })
        }
        SessionPhase.PAUSED -> {
          Button(onClick = { Sessions.resume(app) }, shape = MaterialTheme.shapes.medium) { Text("Resume") }
          TextAction("+5 min", onClick = { Sessions.extend(app) }, modifier = Modifier.padding(horizontal = 12.dp))
          TextAction("End", onClick = { Sessions.end(app) })
        }
        SessionPhase.FINISHED -> {
          val minutes = session.minutesToLog(now)
          if (minutes != null) Button(onClick = { onLog(session.habitId, minutes, session.day.toEpochDay()) }, shape = MaterialTheme.shapes.medium) { Text("Log $minutes min") }
          TextAction("+5 min", onClick = { Sessions.extend(app) }, modifier = Modifier.padding(horizontal = 12.dp))
          TextAction("Discard", onClick = { Sessions.clear(app) })
        }
      }
    }
  }
}

/** Replaces the greeting while a session runs: where it stands, and one line for it. */
@Composable
fun SessionHeader(session: FocusSession, now: Long, modifier: Modifier = Modifier) {
  val phase = session.phase(now)
  Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
    val label =
      when (phase) {
        SessionPhase.RUNNING -> "IN SESSION · ENDS ${endsAtClock(session, now)}"
        SessionPhase.PAUSED -> "IN SESSION · PAUSED"
        SessionPhase.FINISHED -> "SESSION OVER"
      }
    // The state in the accent, as on every card label.
    Text(label, style = MaterialTheme.typography.labelMedium, color = if (phase == SessionPhase.RUNNING) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary)
    Text(
      SessionTimer.line(session, now),
      style = MaterialTheme.typography.titleLarge,
      color = MaterialTheme.colorScheme.onSurface,
      textAlign = TextAlign.Center,
      modifier = Modifier.padding(top = 16.dp),
    )
  }
}

/** Starts a session on a habit and today's topic, from Home or from the topic card. */
fun startSession(app: HabitApp, status: HabitStatus, minutes: Int) =
  Sessions.start(app, status.habit.id, status.habit.name, status.pick?.topic?.id, status.today, minutes)

/** A session's length for [status]'s habit: one tap, no choosing. */
fun suggestedMinutes(status: HabitStatus): Int = SessionTimer.defaultMinutes(status.habit.kind)

/** The haptic when a session ends while Home is showing; not again on every return to it. */
@Composable
fun FinishHaptic(session: FocusSession?, now: Long, onFinish: () -> Unit) {
  val finishedAt = session?.finishedAt
  LaunchedEffect(finishedAt) { if (finishedAt != null && now - finishedAt < 2_000) onFinish() }
}
