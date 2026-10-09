package dev.eduarddragu.anotherhabittracker.ui.home

import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.widthIn
import dev.eduarddragu.anotherhabittracker.domain.Books
import dev.eduarddragu.anotherhabittracker.domain.Chores
import dev.eduarddragu.anotherhabittracker.domain.Practices
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import dev.eduarddragu.anotherhabittracker.ui.components.CardLabel
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
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
 * The session's clock, ticking once a second while the screen is up and the session runs (never
 * between frames: the arc moves a fraction of a dp per second). Paused or over, nothing on screen
 * moves, so one reading is enough. Finishes the session the moment it runs out, without waiting for
 * the alarm.
 *
 * Read it only where it's needed: in draw (the arc), or through derivedStateOf for what changes once
 * a minute (the numerals, the phase). Read in composition, it recomposes the whole screen every second.
 */
@Composable
fun rememberSessionTick(app: HabitApp, session: FocusSession?): State<Long> {
  val lifecycle = LocalLifecycleOwner.current.lifecycle
  return produceState(app.sessions.now(), session) {
    if (session == null) return@produceState
    value = app.sessions.now()
    if (session.finishedAt != null || session.pausedAt != null) return@produceState
    lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
      while (true) {
        val now = app.sessions.now()
        value = now
        if (session.remaining(now) <= 0) {
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

/**
 * On the disc: minutes left (seconds in the last one), or the minutes done once it's over. Recomposes
 * only when what it shows changes: once a minute, once a second in the last one.
 */
@Composable
fun SessionNumerals(session: FocusSession, now: () -> Long, modifier: Modifier = Modifier) {
  val display by remember(session, now) { derivedStateOf { session.display(now()) } }
  val phase by remember(session, now) { derivedStateOf { session.phase(now()) } }
  val caption =
    when (phase) {
      SessionPhase.PAUSED -> "PAUSED"
      SessionPhase.FINISHED -> "MIN DONE"
      SessionPhase.RUNNING -> if (display.seconds) "SEC LEFT" else "MIN LEFT"
    }
  // Paused, the number dims (the grey arc and the header say why); the caption stays dark ink, since
  // accent on butter is too faint for 10sp.
  val dim by animateFloatAsState(if (phase == SessionPhase.PAUSED) 0.6f else 1f, tween(Motion.LONG, easing = Motion.EaseUi), label = "dim")
  // Part of a fixed-size graphic (the disc doesn't grow with the font size), so the numbers don't
  // either: at 200% they would spill off the disc.
  val density = LocalDensity.current
  CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1f)) {
    Column(modifier.semantics(mergeDescendants = true) {}, horizontalAlignment = Alignment.CenterHorizontally) {
      // A fixed width, drawn from the right: DM Sans has no tabular figures, so seconds would jiggle.
      Box(Modifier.width(96.dp), contentAlignment = Alignment.Center) {
        Text(display.value.toString(), style = NumeralsDisplay, color = DiscInk.copy(alpha = dim), textAlign = TextAlign.Center)
      }
      Text(caption, style = MaterialTheme.typography.labelSmall, color = DiscInk.copy(alpha = 0.7f))
    }
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
  /** The session clock; read through derived state, so the card recomposes on a change of phase, not every second. */
  now: () -> Long,
  status: HabitStatus?,
  curriculum: Curriculum,
  onLog: (habitId: Long, minutes: Int?, day: Long) -> Unit,
  /** A tap on the card opens the habit's page, like the habit cards do. */
  onOpen: (Long) -> Unit,
  /** Discard, once it's over: the caller takes it away and offers it back. */
  onDiscard: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val phase by remember(session, now) { derivedStateOf { session.phase(now()) } }
  // Only read once it's over, when it no longer changes.
  val minutesToLog by remember(session, now) { derivedStateOf { session.minutesToLog(now()) } }
  val topic = session.topicId?.let { curriculum.byId[it] }
  // Warm only for a session that counted; one too short to log stays plain.
  val counted = phase == SessionPhase.FINISHED && minutesToLog != null
  DayCard(done = counted, modifier = modifier.clip(MaterialTheme.shapes.large).clickable(onClickLabel = "Open ${session.habitName}") { onOpen(session.habitId) }) {
    val state = when {
      phase != SessionPhase.FINISHED -> "NOW"
      counted -> "DONE"
      else -> "TOO SHORT"
    }
    CardLabel(lead = listOf(status?.habit?.name?.uppercase() ?: session.habitName.uppercase()), state = state, trail = listOfNotNull(topic?.area?.let { curriculum.areaById[it]?.name?.uppercase() }))
    // Starting sends the page back here, so the plan for the session stays in view while it runs: the
    // topic and its questions, today's practice and its steps, the chores and their checks, the book
    // and its line.
    val habit = status?.habit
    val practice = if (topic == null && habit != null && Practices.appliesTo(habit.name, habit.linkedPackage)) Practices.forDay(session.day) else null
    val chores = if (topic == null && practice == null && habit != null && Chores.appliesTo(habit.kind, habit.name, habit.icon)) Chores.day(session.day) else null
    val shelf = status?.books?.takeIf { topic == null && practice == null && chores == null }
    val book = remember(shelf, session.day) { shelf?.let { Books.day(it, session.day, status.doneToday, suggestedMinutes(status)) } }
    Text(topic?.title ?: practice?.title ?: chores?.motto ?: shelf?.current?.title ?: (habit?.name ?: "Session"), style = MaterialTheme.typography.headlineSmall)
    val steps = topic?.hints ?: practice?.steps ?: chores?.checks?.map { it.check }
    steps?.takeIf { it.isNotEmpty() }?.let { NumberedSteps(it, startDelay = 120L) }
    if (book != null && shelf?.current != null) Text(book.line, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
          val minutes = minutesToLog
          if (minutes != null) Button(onClick = { onLog(session.habitId, minutes, session.day.toEpochDay()) }, shape = MaterialTheme.shapes.medium) { Text("Log $minutes min") }
          TextAction("+5 min", onClick = { Sessions.extend(app) }, modifier = Modifier.padding(horizontal = 12.dp))
          TextAction("Discard", onClick = onDiscard)
        }
      }
    }
  }
}

/** Replaces the greeting while a session runs: where it stands, and one line for it. */
@Composable
fun SessionHeader(session: FocusSession, now: () -> Long, modifier: Modifier = Modifier) {
  // Both change a few times a session (a pause, the last five minutes, the end), not every second.
  val phase by remember(session, now) { derivedStateOf { session.phase(now()) } }
  val line by remember(session, now) { derivedStateOf { SessionTimer.line(session, now()) } }
  val ends by remember(session, now) { derivedStateOf { endsAtClock(session, now()) } }
  Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
    SessionLabel(phase, ends)
    // The same voice as the line of the day it replaces: only the planet changes scale.
    Text(
      line,
      style = lineStyle(),
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
      modifier = Modifier.padding(top = 16.dp).widthIn(max = 320.dp),
    )
  }
}

/** Home's line role, under the greeting or the session: the serif italic, small and muted. */
@Composable
@ReadOnlyComposable
internal fun lineStyle(): TextStyle = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, lineHeight = 26.sp, fontStyle = FontStyle.Italic, fontWeight = FontWeight.Medium)

/**
 * Where a session stands, as a card label: IN SESSION, then the state (now, paused) in the accent and
 * when it ends. Shared by Home's header and the habit page.
 */
@Composable
fun SessionLabel(phase: SessionPhase, ends: String, modifier: Modifier = Modifier) =
  when (phase) {
    SessionPhase.RUNNING -> CardLabel(lead = listOf("IN SESSION"), state = "NOW", trail = listOf("ENDS $ends"), modifier = modifier)
    SessionPhase.PAUSED -> CardLabel(lead = listOf("IN SESSION"), state = "PAUSED", modifier = modifier)
    SessionPhase.FINISHED -> CardLabel(state = "SESSION OVER", modifier = modifier)
  }

/**
 * Starts a session on a habit and today's topic, from Home or from the topic card. The day comes from
 * the clock: just after midnight the shown status can still be yesterday's until the minute poll moves,
 * and then its topic is yesterday's too, so the session starts without one (the log form falls back to
 * the day's pick).
 */
fun startSession(app: HabitApp, status: HabitStatus, minutes: Int) {
  val today = app.repository.today()
  Sessions.start(app, status.habit.id, status.habit.name, status.pick?.topic?.id?.takeIf { status.today == today }, today, minutes)
}

/** A session's length for [status]'s habit: one tap, no choosing. */
fun suggestedMinutes(status: HabitStatus): Int = SessionTimer.defaultMinutes(status.habit.kind, status.habit.sessionMinutes)

/** The haptic when a session ends while Home is showing; not again on every return to it. */
@Composable
fun FinishHaptic(session: FocusSession?, now: () -> Long, onFinish: () -> Unit) {
  val finishedAt = session?.finishedAt
  LaunchedEffect(finishedAt) { if (finishedAt != null && now() - finishedAt < 2_000) onFinish() }
}
