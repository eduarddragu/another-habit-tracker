package dev.eduarddragu.anotherhabittracker.ui.detail

import dev.eduarddragu.anotherhabittracker.data.reminderTimesOn
import java.time.LocalTime
import dev.eduarddragu.anotherhabittracker.domain.Motivation
import dev.eduarddragu.anotherhabittracker.domain.formatTime
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.eduarddragu.anotherhabittracker.HabitApp
import dev.eduarddragu.anotherhabittracker.R
import dev.eduarddragu.anotherhabittracker.data.Entry
import dev.eduarddragu.anotherhabittracker.data.HabitStatus
import dev.eduarddragu.anotherhabittracker.data.resolvedIcon
import dev.eduarddragu.anotherhabittracker.domain.BookDay
import dev.eduarddragu.anotherhabittracker.domain.BookRead
import dev.eduarddragu.anotherhabittracker.domain.BookSituation
import dev.eduarddragu.anotherhabittracker.domain.Books
import dev.eduarddragu.anotherhabittracker.domain.Chores
import dev.eduarddragu.anotherhabittracker.domain.ChoresDay
import dev.eduarddragu.anotherhabittracker.domain.Continuation
import dev.eduarddragu.anotherhabittracker.domain.Shelf
import dev.eduarddragu.anotherhabittracker.domain.Descriptions
import dev.eduarddragu.anotherhabittracker.domain.Curriculum
import dev.eduarddragu.anotherhabittracker.domain.EntryType
import dev.eduarddragu.anotherhabittracker.domain.HabitKind
import dev.eduarddragu.anotherhabittracker.domain.Heatmap
import dev.eduarddragu.anotherhabittracker.domain.Milestones
import dev.eduarddragu.anotherhabittracker.domain.PickKind
import dev.eduarddragu.anotherhabittracker.domain.TopicPicker
import dev.eduarddragu.anotherhabittracker.domain.Practice
import dev.eduarddragu.anotherhabittracker.domain.Practices
import dev.eduarddragu.anotherhabittracker.domain.StudiedOn
import dev.eduarddragu.anotherhabittracker.theme.DateLabel
import dev.eduarddragu.anotherhabittracker.theme.Motion
import dev.eduarddragu.anotherhabittracker.theme.NumeralsDisplay
import dev.eduarddragu.anotherhabittracker.ui.components.CardLabel
import dev.eduarddragu.anotherhabittracker.ui.components.CommitPlayback
import dev.eduarddragu.anotherhabittracker.ui.components.DayCard
import dev.eduarddragu.anotherhabittracker.ui.components.Gutter
import dev.eduarddragu.anotherhabittracker.ui.components.HabitViewModel
import dev.eduarddragu.anotherhabittracker.ui.components.HeatmapGrid
import dev.eduarddragu.anotherhabittracker.ui.components.HeatmapLegend
import dev.eduarddragu.anotherhabittracker.ui.components.LogButton
import dev.eduarddragu.anotherhabittracker.ui.components.NumberedSteps
import dev.eduarddragu.anotherhabittracker.ui.components.OpenLinkedAppButton
import dev.eduarddragu.anotherhabittracker.ui.components.RollingNumber
import dev.eduarddragu.anotherhabittracker.ui.components.ScreenTitle
import dev.eduarddragu.anotherhabittracker.ui.components.SectionLabel
import dev.eduarddragu.anotherhabittracker.ui.components.StatBlock
import dev.eduarddragu.anotherhabittracker.ui.components.TextAction
import dev.eduarddragu.anotherhabittracker.reminders.Sessions
import dev.eduarddragu.anotherhabittracker.domain.SessionPhase
import dev.eduarddragu.anotherhabittracker.domain.FocusSession
import dev.eduarddragu.anotherhabittracker.ui.home.rememberSessionTick
import dev.eduarddragu.anotherhabittracker.ui.home.endsAtClock
import dev.eduarddragu.anotherhabittracker.ui.home.suggestedMinutes
import dev.eduarddragu.anotherhabittracker.ui.home.SessionLabel
import dev.eduarddragu.anotherhabittracker.ui.home.startSession
import dev.eduarddragu.anotherhabittracker.ui.components.WeekStrip
import dev.eduarddragu.anotherhabittracker.ui.components.formatDuration
import dev.eduarddragu.anotherhabittracker.ui.components.formatMinutes
import dev.eduarddragu.anotherhabittracker.ui.components.gutter
import dev.eduarddragu.anotherhabittracker.ui.components.rememberArrival
import dev.eduarddragu.anotherhabittracker.ui.components.rememberCommit
import dev.eduarddragu.anotherhabittracker.ui.components.rise
import dev.eduarddragu.anotherhabittracker.ui.components.screenPadding
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class HabitDetailViewModel(app: HabitApp, habitId: Long) : HabitViewModel(app, habitId) {
  /** Marks a topic known and hands back what was newly marked, for the undo. */
  fun markKnown(topicId: String, onMarked: (Set<String>) -> Unit) = once { onMarked(app.repository.markKnown(habitId, listOf(topicId))) }

  fun unmark(topicIds: Set<String>) = app.repository.undoKnown(habitId, topicIds)

  /**
   * Uses this week's freeze on the day before [shownToday], the day the page offered it for (around
   * midnight the clock may already be a day ahead); hands back the freeze's id (null if refused), for
   * the undo.
   */
  fun freezeYesterday(shownToday: LocalDate, onDone: (Long?) -> Unit) = once { onDone(app.repository.freeze(habitId, shownToday.minusDays(1))) }

  /** Undo of [freezeYesterday]: in the app's scope, since the snackbar can outlive this page. */
  fun unfreeze(id: Long) = app.repository.undoFreeze(id)

  /** Takes a freeze back; [onDone] runs once it's gone, for the undo. */
  fun removeFreeze(entry: Entry, onDone: () -> Unit) =
    once {
      app.repository.deleteEntry(entry.id)
      onDone()
    }

  fun restoreFreeze(entry: Entry) = app.repository.restoreFreeze(entry)

  /** Keep going on [topicId] today (a deep dive); null goes back to the picker's topic. */
  fun keepGoing(topicId: String?) = app.repository.keepGoing(habitId, topicId)

  /** Marks the book on the go finished today; hands back the mark's id, for the undo. */
  fun markFinished(book: BookRead, onDone: (Long) -> Unit) = once { onDone(app.repository.markFinished(habitId, book.title, book.author)) }

  /** Undo of [markFinished]: in the app's scope, since the snackbar can outlive this page. */
  fun unfinish(id: Long) = app.repository.undoFinished(id)
}

private const val YEAR_WEEKS = 53
private const val MIN_WEEKS = 10
private val HEATMAP_GAP = 3.dp
private val WEEKDAY_COLUMN = 30.dp
private const val RECENT_LIMIT = 30

/** Geist Mono's capital height as a share of the font size (from the font's OS/2 table). */
private const val MONO_CAP_HEIGHT = 0.71f

/** Title, streak, topic, actions, numbers, history. */
private val ARRIVAL_DELAYS = longArrayOf(140, 210, 320, 460, 530, 600)
private val DAY_FORMAT = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HabitDetailScreen(
  app: HabitApp,
  habitId: Long,
  onLog: () -> Unit,
  onSettings: () -> Unit,
  onCurriculum: () -> Unit,
  /** Opens the log form on a session already logged, to correct or delete it. */
  onEditEntry: (Long) -> Unit,
  /** Opens the log form for a new session on a given day: a frozen or missed one. */
  onLogOnDay: (LocalDate) -> Unit,
  /** Opens the log form for a finished focus session: its minutes and its day (epoch day). */
  onLogMinutes: (Int, Long) -> Unit,
  /** A session was started here: back to Home, where the planet is the timer (the card under it keeps the topic and its questions). */
  onSessionStarted: () -> Unit,
  /** Shows a message with an Undo action. */
  onUndoable: (String, () -> Unit) -> Unit,
  onMessage: (String) -> Unit,
  modifier: Modifier = Modifier,
  viewModel: HabitDetailViewModel = viewModel(key = "detail-$habitId") { HabitDetailViewModel(app, habitId) },
) {
  val state by viewModel.status.collectAsStateWithLifecycle()
  val status = state ?: return
  val study = status.habit.kind == HabitKind.STUDY
  val commit = rememberCommit(habitId)
  val session by app.sessions.session.collectAsStateWithLifecycle()
  // Only this habit's session: another one's clock has nothing to show here.
  val ownSession = session?.takeIf { it.habitId == habitId }
  val tick = rememberSessionTick(app, ownSession)
  val clock = remember(tick) { { tick.value } }
  var removingFreeze by rememberSaveable { mutableStateOf<Long?>(null) }
  // Title, streak, topic, actions, numbers, history: arriving in that order as the page slides in, each
  // settling after the one above it. The topic card is the page's main content, so it takes its time:
  // slower, from further down.
  val arrival = rememberArrival(6, delayOf = { ARRIVAL_DELAYS[it] }, durationOf = { if (it == 2) 720 else 520 })

  // Vertical padding only: the year heatmap runs edge to edge, everything else sits in the gutter.
  // Order: where the habit stands (streak), what to do now (topic or Log), then the numbers and history.
  LazyColumn(modifier, contentPadding = screenPadding(horizontal = 0.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
    // Stable keys: an item that comes and goes (stats, heatmap, the practice) doesn't make the others
    // lose their state or their scroll anchor.
    item(key = "title") {
      // Settings belong to the habit, not to today's topic, so they sit with the title.
      Row(Modifier.gutter().rise(arrival[0], 16.dp), verticalAlignment = Alignment.Bottom) {
        ScreenTitle("Habit", status.habit.name, Modifier.weight(1f), icon = status.habit.resolvedIcon)
        IconButton(onClick = onSettings) { Icon(painterResource(R.drawable.ic_settings), contentDescription = "Settings", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
      }
    }
    item(key = "streak") {
      StreakBlock(
        status,
        commit,
        onFreezeYesterday = { viewModel.freezeYesterday(status.today) { id -> if (id != null) onUndoable("Yesterday is frozen.") { viewModel.unfreeze(id) } else onMessage("Freeze not available.") } },
        onLogYesterday = { onLogOnDay(status.today.minusDays(1)) },
        modifier = Modifier.gutter().rise(arrival[1], 16.dp),
      )
    }
    if (study) {
      item(key = "topic") {
        TodaysTopic(
          status,
          viewModel.curriculum,
          onLog = onLog,
          onKnown = { id -> viewModel.markKnown(id) { marked -> if (marked.isNotEmpty()) onUndoable("Marked as known.") { viewModel.unmark(marked) } } },
          onKeepGoing = viewModel::keepGoing,
          onCurriculum = onCurriculum,
          onStart = if (session == null) { minutes -> startSession(app, status, minutes); onSessionStarted() } else null,
          session = ownSession,
          now = clock,
          onEndSession = { Sessions.end(app) },
          onLogSession = { minutes, day -> onLogMinutes(minutes, day) },
          modifier = Modifier.gutter().rise(arrival[2], 32.dp),
        )
      }
    }
    // Meditation gets today's practice, with its actions inside the card like the study topic.
    val practice = if (!study && Practices.appliesTo(status.habit.name, status.habit.linkedPackage)) Practices.forDay(status.today) else null
    // Reading gets the book on the go, and a line about where it stands.
    val shelf = status.books
    if (practice != null) {
      item(key = "practice") { TodaysPractice(status, practice, onLog = onLog, onStart = if (session == null) { minutes -> startSession(app, status, minutes); onSessionStarted() } else null, modifier = Modifier.gutter().rise(arrival[2], 32.dp)) }
    } else if (shelf != null) {
      item(key = "book") {
        val book = remember(shelf, status.today, status.doneToday) { Books.day(shelf, status.today, status.doneToday, suggestedMinutes(status)) }
        TodaysBook(status, book, onLog = onLog, onStart = if (session == null) { minutes -> startSession(app, status, minutes); onSessionStarted() } else null, modifier = Modifier.gutter().rise(arrival[2], 32.dp))
      }
    } else if (!study && Chores.appliesTo(status.habit.kind, status.habit.name, status.habit.icon)) {
      item(key = "chores") { TodaysChores(status, Chores.day(status.today), onLog = onLog, onStart = if (session == null) { minutes -> startSession(app, status, minutes); onSessionStarted() } else null, modifier = Modifier.gutter().rise(arrival[2], 32.dp)) }
    } else if (!study) {
      item(key = "today") { TodaysHabit(status, onLog = onLog, onStart = if (session == null) { minutes -> startSession(app, status, minutes); onSessionStarted() } else null, modifier = Modifier.gutter().rise(arrival[2], 32.dp)) }
    } else if (status.habit.linkedPackage != null) {
      item(key = "actions") {
        FlowRow(Modifier.gutter().rise(arrival[3], 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          if (!study) LogButton(status.doneToday, onLog)
          OpenLinkedAppButton(status.habit.linkedPackage)
        }
      }
    }
    // No numbers before there is something to count: a row of zeros only says the app is new.
    if (status.recent.isNotEmpty()) item(key = "stats") { StatRow(status, Modifier.gutter().rise(arrival[4], 16.dp)) }
    if (Heatmap.worthShowing(status.today, status.since)) item(key = "heatmap") { YearHeatmap(status, Modifier.rise(arrival[4], 16.dp)) }
    if (shelf != null && (shelf.current != null || shelf.finished.isNotEmpty())) {
      item(key = "books") {
        BookList(
          shelf,
          onFinish = { book -> viewModel.markFinished(book) { id -> onUndoable("Finished ${book.title}.") { viewModel.unfinish(id) } } },
          modifier = Modifier.gutter().rise(arrival[4], 16.dp),
        )
      }
    }
    // The heading travels with its first entry (8dp inside a section), not a whole section gap above it.
    if (status.recent.isEmpty()) {
      item(key = "recent-empty") {
        Column(Modifier.gutter().rise(arrival[5], 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          SectionLabel("Recent")
          Text("Nothing logged yet.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
    }
    val recent = status.recent.take(RECENT_LIMIT)
    // A deep dive: the history offers to keep going on the last session's topic instead of today's.
    val keepGoing = if (study) Continuation.candidate(status.recent.map { StudiedOn(it.day, it.topicId) }, status.today, status.pick?.topic?.id) else null
    val keepGoingRow = keepGoing?.let { id -> recent.firstOrNull { it.topicId == id }?.id }
    // The session a book was finished with: the latest one of that book on that day.
    val finishedRows =
      shelf?.finished.orEmpty().mapNotNull { book -> status.recent.firstOrNull { it.type == EntryType.SESSION && it.day == book.finished && it.track?.let(Books::key) == Books.key(book.title) }?.id }.toSet()
    itemsIndexed(recent, key = { _, entry -> entry.id }) { index, entry ->
      // The first rows arrive with the RECENT heading; animateItem doesn't animate a list's first items.
      Column(
        Modifier.gutter()
          .rise(if (index < 6) arrival[5] else null, 16.dp)
          .animateItem(fadeInSpec = tween(Motion.SHORT), placementSpec = tween(Motion.LIST, easing = Motion.EaseUi), fadeOutSpec = tween(120))
      ) {
        if (index == 0) {
          SectionLabel("Recent")
          Spacer(Modifier.height(8.dp))
        }
        EntryRow(
          entry,
          study,
          // A session opens in the log form; a freeze has nothing to edit, so it only offers removal.
          onClick = { if (entry.type == EntryType.FREEZE) removingFreeze = entry.id else onEditEntry(entry.id) },
          topic = if (shelf != null) entry.track else entry.topicId?.let { viewModel.curriculum.byId[it]?.title ?: it },
          book = shelf != null,
          finished = entry.id in finishedRows,
          onKeepGoing = if (entry.id == keepGoingRow) ({ viewModel.keepGoing(keepGoing) }) else null,
          last = entry == recent.last(),
        )
      }
    }
  }
  removingFreeze?.let { id ->
    val freeze = status.recent.firstOrNull { it.id == id }
    // Did it after all (past midnight, say): log the session and it replaces the freeze. Or take the
    // freeze back and the day is missed again (with an undo). Cancel leaves it as it is.
    // Buttons, start to end: the destructive one apart at the start, then Cancel, then the main one.
    AlertDialog(
      onDismissRequest = { removingFreeze = null },
      title = { Text("This day is frozen") },
      text = { Text("Did it after all? Log the session and the freeze goes back to this week. Removing the freeze makes the day missed again.") },
      confirmButton = {
        TextButton(
          onClick = {
            removingFreeze = null
            freeze?.day?.let(onLogOnDay)
          },
          enabled = freeze != null,
        ) {
          Text("Log a session")
        }
      },
      dismissButton = {
        TextButton(
          onClick = {
            removingFreeze = null
            freeze?.let { entry -> viewModel.removeFreeze(entry) { onUndoable("Freeze removed.") { viewModel.restoreFreeze(entry) } } }
          },
          enabled = freeze != null,
        ) {
          Text("Remove freeze")
        }
        TextButton(onClick = { removingFreeze = null }) { Text("Cancel") }
      },
    )
  }
}

/**
 * Where the habit stands: the streak, big, and this week under it. During a commit the day fills in,
 * the number rolls and the line under it changes, in that order.
 */
@Composable
private fun StreakBlock(status: HabitStatus, commit: CommitPlayback, onFreezeYesterday: () -> Unit, onLogYesterday: () -> Unit, modifier: Modifier = Modifier) {
  val stats = status.stats
  val colors = MaterialTheme.colorScheme
  val shown = if (commit.rolled) stats.streak else commit.commit?.previousStreak ?: stats.streak
  val rise = with(LocalDensity.current) { 8.dp.roundToPx() }
  Column(modifier) {
    // No streak, no number (a grey 0 as the page's first word reads as a verdict): the week says it.
    // The first day done brings it in as the commit rolls it to 1.
    AnimatedVisibility(
      visible = shown > 0,
      enter = fadeIn(tween(Motion.LONG, easing = Motion.EaseEntrance)) + slideInVertically(tween(Motion.LONG, easing = Motion.EaseEntrance)) { rise } + expandVertically(tween(Motion.LONG, easing = Motion.EaseUi), expandFrom = Alignment.Top),
      exit = fadeOut(tween(Motion.FADE_OUT)) + shrinkVertically(tween(Motion.SHORT, easing = Motion.EaseUi)),
    ) {
      Column {
        // The caption's last line sits on the number's baseline. Number and caption read as one value.
        Row(Modifier.clearAndSetSemantics { contentDescription = Descriptions.streak(shown) }) {
          RollingNumber(shown, NumeralsDisplay, colors.primary, Modifier.alignBy(LastBaseline))
          Spacer(Modifier.width(12.dp))
          Column(Modifier.alignBy(LastBaseline)) {
            Text(if (shown == 1) "DAY" else "DAYS", style = MaterialTheme.typography.labelMedium, color = colors.primary)
            Text("IN A ROW", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
          }
        }
        Spacer(Modifier.height(14.dp))
      }
    }
    WeekStrip(status.cells, status.today, cellSize = 28.dp, gap = 8.dp, initials = true, commit = commit, daysOff = status.summary.daysOff)
    // No running commentary under the week (the squares say it): only a milestone, on its day.
    val milestone = if (commit.settled && status.doneToday) Milestones.line(stats.streak) else null
    if (milestone != null) {
      Spacer(Modifier.height(12.dp))
      Text(milestone, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
    }
    if (status.summary.yesterdayEmpty) Spacer(Modifier.height(12.dp))
    // Yesterday slipped through: log it if it was done after all (finished past midnight), or use
    // the week's freeze while it's free. This is the one way to log for yesterday. A day off needs no
    // saving, only a way to log a session done on it.
    if (status.summary.yesterdayEmpty) {
      Row(Modifier.fillMaxWidth()) {
        Text(if (status.summary.yesterdayOff) "Did it yesterday?" else "Missed yesterday.", style = MaterialTheme.typography.bodyMedium, color = colors.primary, modifier = Modifier.weight(1f).alignByBaseline())
        TextAction("Log it", onClick = onLogYesterday, modifier = Modifier.alignByBaseline())
        if (status.summary.canFreezeYesterday) {
          Spacer(Modifier.width(20.dp))
          TextAction("Freeze it", onClick = onFreezeYesterday, modifier = Modifier.alignByBaseline())
        }
      }
    }
  }
}

/** Meditation's counterpart to today's topic: a small practice for today's session, in three steps. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TodaysPractice(status: HabitStatus, practice: Practice, onLog: () -> Unit, onStart: ((Int) -> Unit)?, modifier: Modifier = Modifier) {
  DayCard(status.doneToday, modifier) {
    // The state (today, done) in the accent, the noun muted, as on the topic card and Home.
    CardLabel(lead = listOf("PRACTICE"), state = if (status.doneToday) "DONE" else "TODAY")
    Text(practice.title, style = MaterialTheme.typography.headlineSmall)
    NumberedSteps(practice.steps, startDelay = 560L)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
      LogButton(status.doneToday, onLog)
      OpenLinkedAppButton(status.habit.linkedPackage)
      if (onStart != null && !status.doneToday) {
        val minutes = suggestedMinutes(status)
        // A text action after the buttons, set off by the same 20dp as other actions on one line.
        TextAction("Start $minutes min", onClick = { onStart(minutes) }, modifier = Modifier.padding(start = 12.dp))
      }
    }
  }
}

/**
 * Chores' counterpart to today's practice: the day's motto as the title, then every check in today's
 * order (the first is where to start), the state the place should be in. Any time spent counts.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TodaysChores(status: HabitStatus, chores: ChoresDay, onLog: () -> Unit, onStart: ((Int) -> Unit)?, modifier: Modifier = Modifier) {
  DayCard(status.doneToday, modifier) {
    CardLabel(lead = listOf("CHORES"), state = if (status.doneToday) "DONE" else "TODAY")
    Text(chores.motto, style = MaterialTheme.typography.headlineSmall)
    NumberedSteps(chores.checks.map { it.check }, startDelay = 560L)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
      LogButton(status.doneToday, onLog)
      OpenLinkedAppButton(status.habit.linkedPackage)
      if (onStart != null && !status.doneToday) {
        val minutes = suggestedMinutes(status)
        TextAction("Start $minutes min", onClick = { onStart(minutes) }, modifier = Modifier.padding(start = 12.dp))
      }
    }
  }
}

/**
 * Any other habit gets the same card as the rest, so a new one doesn't look unfinished: its name, and
 * where the day stands (the next reminder, done, a day off).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TodaysHabit(status: HabitStatus, onLog: () -> Unit, onStart: ((Int) -> Unit)?, modifier: Modifier = Modifier) {
  DayCard(status.doneToday, modifier) {
    CardLabel(state = if (status.doneToday) "DONE" else "TODAY")
    Text(status.habit.name, style = MaterialTheme.typography.headlineSmall)
    val now = LocalTime.now()
    val line =
      when {
        status.doneToday -> "Done for today."
        status.frozenToday -> "Frozen today. The streak is safe."
        status.summary.pausedToday -> Motivation.timeOffLine(status.today, salt = status.habit.id.toInt())
        else -> status.habit.reminderTimesOn(status.today).firstOrNull { it.isAfter(now) }?.let { "Next reminder at ${formatTime(it)}." } ?: "No more reminders today."
      }
    Text(line, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
      LogButton(status.doneToday, onLog)
      OpenLinkedAppButton(status.habit.linkedPackage)
      if (onStart != null && !status.doneToday) {
        val minutes = suggestedMinutes(status)
        TextAction("Start $minutes min", onClick = { onStart(minutes) }, modifier = Modifier.padding(start = 12.dp))
      }
    }
  }
}

/**
 * Reading's counterpart to today's topic: the book on the go, its author, and one line about where it
 * stands (read yesterday, put down for days, just finished, none yet).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TodaysBook(status: HabitStatus, book: BookDay, onLog: () -> Unit, onStart: ((Int) -> Unit)?, modifier: Modifier = Modifier) {
  DayCard(status.doneToday, modifier) {
    when {
      status.doneToday -> CardLabel(lead = listOf("BOOK"), state = "DONE")
      book.situation == BookSituation.JUST_FINISHED -> CardLabel(lead = listOf("BOOK"), state = "FINISHED")
      else -> CardLabel(lead = listOf("BOOK"), state = "TODAY")
    }
    // Title and author read as one block: the author tucked under the title, not a card gap away.
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(book.title ?: "No book yet", style = MaterialTheme.typography.headlineSmall)
      book.author?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
    Text(book.line, style = MaterialTheme.typography.bodyLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
      LogButton(status.doneToday, onLog)
      OpenLinkedAppButton(status.habit.linkedPackage)
      if (onStart != null && !status.doneToday) {
        val minutes = suggestedMinutes(status)
        TextAction("Start $minutes min", onClick = { onStart(minutes) }, modifier = Modifier.padding(start = 12.dp))
      }
    }
  }
}

/** The read list: the book on the go (with a way to call it finished), then every book finished, newest first. */
@Composable
private fun BookList(shelf: Shelf, onFinish: (BookRead) -> Unit, modifier: Modifier = Modifier) {
  val muted = MaterialTheme.colorScheme.onSurfaceVariant
  Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
    SectionLabel("Books")
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
      shelf.current?.let { book ->
        Column {
          Row(Modifier.fillMaxWidth()) {
            Text(book.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).alignByBaseline())
            TextAction("Mark as finished", onClick = { onFinish(book) }, vertical = 0.dp, modifier = Modifier.padding(start = 16.dp).alignByBaseline())
          }
          Text(
            bookFacts(book.author, book.started?.let { "since ${it.format(DAY_FORMAT)}" }, sessionCount(book.sessions), book.minutes.takeIf { it > 0 }?.let(::formatDuration)),
            style = MaterialTheme.typography.bodyMedium,
            color = muted,
          )
        }
      }
      shelf.finished.forEach { book ->
        Column {
          Text(book.title, style = MaterialTheme.typography.titleMedium)
          Text(
            bookFacts(book.author, book.finished?.let { "finished ${it.format(DAY_FORMAT)}" }, book.minutes.takeIf { it > 0 }?.let(::formatDuration)),
            style = MaterialTheme.typography.bodyMedium,
            color = muted,
          )
        }
      }
    }
  }
}

private fun sessionCount(sessions: Int): String = if (sessions == 1) "1 session" else "$sessions sessions"

/** "Author · since Mon 28 Sep · 3 sessions": the author as written, otherwise the line starts with a capital. */
private fun bookFacts(author: String?, vararg facts: String?): String =
  (listOfNotNull(author) + listOfNotNull(*facts).mapIndexed { i, fact -> if (i == 0 && author == null) fact.replaceFirstChar { it.uppercase() } else fact }).joinToString(" · ")

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TodaysTopic(
  status: HabitStatus,
  curriculum: Curriculum,
  onLog: () -> Unit,
  onKnown: (String) -> Unit,
  onKeepGoing: (String?) -> Unit,
  onCurriculum: () -> Unit,
  /** Starts a session of [Int] minutes on today's topic; null when one is already running. */
  onStart: ((Int) -> Unit)?,
  modifier: Modifier = Modifier,
  /** The session running on this habit, if any, with the clock to read it by. */
  session: FocusSession? = null,
  /** The session clock; read through derived state, so the card changes with the phase, not every second. */
  now: () -> Long = { 0L },
  onEndSession: () -> Unit = {},
  /** Log for a finished session: its minutes and day. */
  onLogSession: (Int, Long) -> Unit = { _, _ -> },
) {
  val haptics = LocalHapticFeedback.current
  // The session's state changes a few times a session, not every second: derived, not read directly.
  val phaseNow by remember(session, now) { derivedStateOf { session?.phase(now()) } }
  val endsNow by remember(session, now) { derivedStateOf { session?.let { endsAtClock(it, now()) } } }
  val minutesNow by remember(session, now) { derivedStateOf { session?.minutesToLog(now()) } }
  // The topic shown when the page opened: its questions follow the card in. After "I know this" the
  // next topic's questions come in right behind it instead.
  val firstTopic = rememberSaveable { status.pick?.topic?.id.orEmpty() }
  DayCard(status.doneToday, modifier) {
    // "I know this" swaps the topic: the old one fades out quickly, then the new one rises in while
    // the card eases to its new height. Never two topics on screen at once.
    AnimatedContent(
      targetState = status.pick,
      contentKey = { it?.topic?.id },
      transitionSpec = {
        (fadeIn(tween(220, delayMillis = Motion.FADE_OUT, easing = Motion.EaseUi)) + slideInVertically(tween(300, easing = Motion.EaseUi)) { it / 12 }) togetherWith
          fadeOut(tween(Motion.FADE_OUT)) using SizeTransform { _, _ -> tween(300, easing = Motion.EaseUi) }
      },
      label = "topic",
    ) { pick ->
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (pick == null) {
          CardLabel(lead = listOf("CURRICULUM COMPLETE"))
          Text("Nothing left to pick. Time to add topics.", style = MaterialTheme.typography.bodyLarge)
          TextAction("See the curriculum", onClick = onCurriculum)
          return@Column
        }
        val area = (curriculum.areaById[pick.topic.area]?.name ?: pick.topic.area).uppercase()
        val state = if (status.doneToday) "DONE" else pickLabel(pick.kind)
        // The curriculum link sits on the label's line, top right: the actions below stay on one row.
        // Reviews waiting are the reason to open it, so it says how many.
        val due = remember(status.topicMarks, status.today) { TopicPicker.dueReviews(curriculum, status.topicMarks.values.toList(), status.today).size }
        Row(Modifier.fillMaxWidth()) {
          Box(Modifier.weight(1f).alignByBaseline()) { CardLabel(lead = listOf("TOPIC"), state = state, trail = listOf(area)) }
          TextAction(if (due > 0) "$due due" else "Curriculum", onClick = onCurriculum, vertical = 4.dp, modifier = Modifier.alignByBaseline())
        }
        Text(pick.topic.title, style = MaterialTheme.typography.headlineSmall)
        // The questions arrive one at a time.
        NumberedSteps(pick.topic.hints, startDelay = if (pick.topic.id == firstTopic) 560L else 120L)
        // A topic coming back: what happened last time, so the notes don't have to be dug out of Recent.
        if (pick.kind != PickKind.NEW) {
          status.recent.firstOrNull { it.type == EntryType.SESSION && it.topicId == pick.topic.id }?.let { last ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
              Text(
                listOfNotNull("LAST TIME", last.day.format(DAY_FORMAT).uppercase(), last.score?.let { "$it/5" }).joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              if (last.note.isNotBlank()) Text(last.note, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
          }
        }
        // All the card's actions in one flowing row, like the practice card; undoing a "keep going" too.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
          // One tap, 30 minutes. The page stays here (it's where the scope is checked); the planet on
          // Home is the timer, and the countdown is also in the notification and the status bar.
          val sessionPhase = phaseNow
          val ends = endsNow
          val minutesToLog = minutesNow
          when {
            session != null && (sessionPhase == SessionPhase.RUNNING || sessionPhase == SessionPhase.PAUSED) -> {
              SessionLabel(sessionPhase, ends.orEmpty())
              TextAction("End", onClick = onEndSession, modifier = Modifier.padding(start = 12.dp))
            }
            session != null && sessionPhase == SessionPhase.FINISHED && minutesToLog != null -> {
              Button(onClick = { onLogSession(minutesToLog, session.day.toEpochDay()) }, shape = MaterialTheme.shapes.medium) { Text("Log $minutesToLog min") }
            }
            else -> {
              LogButton(status.doneToday, onLog)
              if (onStart != null && !status.doneToday) {
                val minutes = suggestedMinutes(status)
                TextAction("Start $minutes min", onClick = { onStart(minutes) }, modifier = Modifier.padding(start = 12.dp))
              }
            }
          }
          // Not once today is logged: it would swap out the topic just studied. Text actions, set off
          // from each other by 20dp like other actions on one line.
          if (!status.doneToday) {
            TextAction(
              "I know this",
              onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.ToggleOn)
                onKnown(pick.topic.id)
              },
              modifier = Modifier.padding(start = 12.dp),
            )
          }
          if (status.focused && !status.doneToday) TextAction("Back to today's topic", onClick = { onKeepGoing(null) }, modifier = Modifier.padding(start = 12.dp))
        }
      }
    }
  }
}

fun pickLabel(kind: PickKind) =
  when (kind) {
    PickKind.NEW -> "NEW"
    PickKind.REVIEW -> "REVIEW"
    PickKind.CONTINUE -> "CONTINUING"
  }

@Composable
private fun YearHeatmap(status: HabitStatus, modifier: Modifier = Modifier) {
  // Reverse scrolling starts at the right edge, where today is, from the very first frame (scrolling
  // there after the first frame made the grid jump in the middle of the push transition).
  // The grid starts at the first log and grows with the history. Early on the squares are larger and
  // fill the width; once the history outgrows the screen they settle at 14dp and the grid scrolls.
  val scroll = rememberScrollState()
  Column(modifier) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
      val available = maxWidth - Gutter * 2 - WEEKDAY_COLUMN
      val weeks = Heatmap.visibleWeeks(status.today, status.since, minimum = MIN_WEEKS, maximum = YEAR_WEEKS)
      val cell = ((available + HEATMAP_GAP) / weeks - HEATMAP_GAP).coerceIn(14.dp, 30.dp)
      Row(Modifier.horizontalScroll(scroll, reverseScrolling = true).padding(horizontal = Gutter)) {
        HeatmapGrid(Heatmap.weeks(status.today, weeks), status.cells, status.today, cellSize = cell, gap = HEATMAP_GAP, daysOff = status.summary.daysOff)
      }
    }
    HeatmapLegend(Modifier.gutter().padding(top = 8.dp))
  }
}

/** Three numbers in one borderless row, split by hairlines. Values that don't exist yet are left out. */
@Composable
private fun StatRow(status: HabitStatus, modifier: Modifier = Modifier) {
  val stats = status.stats
  val values =
    listOfNotNull(
      "${stats.daysLast30}/30" to "last 30 days",
      // Units on the number ("1h 45m"), the window in the caption, joined by a dot like every label.
      stats.minutesLast30.takeIf { it > 0 }?.let { formatDuration(it) to "time · 30 days" },
      if (status.habit.kind == HabitKind.STUDY) stats.averageScoreLast30?.let { "%.1f".format(Locale.ENGLISH, it) to "avg score" }
      else averagePerSession(stats.totalMinutes, stats.totalSessions)?.let { it to "per session" },
    )
  val rule = MaterialTheme.colorScheme.outlineVariant
  Row(modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
    values.forEachIndexed { index, (value, caption) ->
      if (index > 0) {
        Spacer(Modifier.width(16.dp))
        Spacer(Modifier.width(1.dp).fillMaxHeight().padding(vertical = 4.dp).drawBehind { drawRect(rule) })
        Spacer(Modifier.width(16.dp))
      }
      StatBlock(value, caption, Modifier.weight(1f))
    }
  }
}

/** One session on a timeline: a dot on a hairline rail, the day in mono, the facts in body text. */
@Composable
private fun EntryRow(
  entry: Entry,
  study: Boolean,
  topic: String?,
  last: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  onKeepGoing: (() -> Unit)? = null,
  /** A reading habit: [topic] is the book, and the author leads the facts. */
  book: Boolean = false,
  /** The session the book was finished with. */
  finished: Boolean = false,
) {
  val frozen = entry.type == EntryType.FREEZE
  val facts =
    if (book) listOfNotNull(entry.module, entry.minutes?.let { formatMinutes(it) })
    else
      listOfNotNull(
        entry.score?.takeIf { study }?.let { "$it/5" },
        entry.minutes?.let { formatMinutes(it) },
        listOfNotNull(entry.track, entry.module).joinToString(" · ").ifEmpty { null },
      )
  val accent = MaterialTheme.colorScheme.primary
  val rail = MaterialTheme.colorScheme.outlineVariant
  // The date heads the entry, so it's sized to sit with the body text under it.
  val dateStyle = DateLabel
  // The dot sits on the middle of the date's capitals, measured from where the date actually lands
  // (its line is taller when it carries the "keep going" button), not guessed.
  // Kept relative to the row, so scrolling (which moves both) writes the same value and draws nothing.
  val row = remember { arrayOfNulls<LayoutCoordinates>(1) }
  var dateTop by remember { mutableFloatStateOf(Float.NaN) }
  var dateBaseline by remember { mutableFloatStateOf(0f) }
  Row(modifier.fillMaxWidth().height(IntrinsicSize.Min).clickable(onClickLabel = if (frozen) "Freeze options" else "Edit session", onClick = onClick).onPlaced { row[0] = it }) {
    Spacer(
      Modifier.width(18.dp).fillMaxHeight().drawBehind {
        val x = 3.dp.toPx()
        val capHeight = dateStyle.fontSize.toPx() * MONO_CAP_HEIGHT
        val dotY = if (dateTop.isNaN()) 7.dp.toPx() else dateTop + dateBaseline - capHeight / 2
        if (!last) drawLine(rail, Offset(x, dotY), Offset(x, size.height + 24.dp.toPx()), strokeWidth = 1.dp.toPx())
        // A freeze kept the streak without adding to it: a hollow dot.
        if (frozen) drawCircle(accent, radius = 2.5.dp.toPx(), center = Offset(x, dotY), style = Stroke(1.dp.toPx()))
        else drawCircle(accent, radius = 3.dp.toPx(), center = Offset(x, dotY))
      }
    )
    Column(Modifier.weight(1f)) {
      // The day, and on the session worth continuing, the way back into it right beside it. Two faces
      // and sizes on one line: they share a baseline, or they read as two levels.
      Row(Modifier.fillMaxWidth()) {
        Text(
          entry.day.format(DAY_FORMAT).uppercase(),
          style = dateStyle,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          onTextLayout = { dateBaseline = it.firstBaseline },
          modifier = Modifier.weight(1f).alignByBaseline().onPlaced { date -> row[0]?.takeIf { it.isAttached }?.let { dateTop = it.localPositionOf(date, Offset.Zero).y } },
        )
        if (onKeepGoing != null) {
          // A text action with no button box: it doesn't make the date line taller than the date. Its
          // touch area still grows to 48dp, and inside it a tap is the action's, not the row's.
          TextAction("Keep going today", onClick = onKeepGoing, modifier = Modifier.alignByBaseline(), vertical = 0.dp)
        }
        // A state, so in the accent, on the date's line.
        if (finished) Text("FINISHED", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.alignByBaseline())
      }
      // A study session leads with what it was about; the numbers follow.
      if (topic != null) {
        // The topic reads as the headline of the session: the strongest face in the row, in ink (the
        // accent is for state and actions).
        Text(topic, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        if (facts.isNotEmpty()) Text(facts.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
      } else {
        Text(if (frozen) "Frozen" else facts.joinToString(" · ").ifEmpty { "Done" }, style = MaterialTheme.typography.bodyLarge, color = if (frozen) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified)
      }
      if (entry.extraTopics.isNotBlank()) Text("Also: ${entry.extraTopics}", style = MaterialTheme.typography.bodyMedium)
      if (entry.note.isNotBlank()) Text(entry.note, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}

private fun averagePerSession(totalMinutes: Int, sessions: Int): String? = if (sessions == 0 || totalMinutes == 0) null else formatDuration(totalMinutes / sessions)
