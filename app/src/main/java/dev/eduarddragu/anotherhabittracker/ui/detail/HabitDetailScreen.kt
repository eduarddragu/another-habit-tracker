package dev.eduarddragu.anotherhabittracker.ui.detail

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.eduarddragu.anotherhabittracker.HabitApp
import dev.eduarddragu.anotherhabittracker.R
import dev.eduarddragu.anotherhabittracker.data.Entry
import dev.eduarddragu.anotherhabittracker.data.HabitStatus
import dev.eduarddragu.anotherhabittracker.data.resolvedIcon
import dev.eduarddragu.anotherhabittracker.domain.Continuation
import dev.eduarddragu.anotherhabittracker.domain.Curriculum
import dev.eduarddragu.anotherhabittracker.domain.EntryType
import dev.eduarddragu.anotherhabittracker.domain.HabitKind
import dev.eduarddragu.anotherhabittracker.domain.Heatmap
import dev.eduarddragu.anotherhabittracker.domain.PickKind
import dev.eduarddragu.anotherhabittracker.domain.Practice
import dev.eduarddragu.anotherhabittracker.domain.Practices
import dev.eduarddragu.anotherhabittracker.domain.StudiedOn
import dev.eduarddragu.anotherhabittracker.theme.DateLabel
import dev.eduarddragu.anotherhabittracker.theme.Motion
import dev.eduarddragu.anotherhabittracker.theme.NumeralsDisplay
import dev.eduarddragu.anotherhabittracker.theme.fadeThrough
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
import dev.eduarddragu.anotherhabittracker.ui.components.WeekStrip
import dev.eduarddragu.anotherhabittracker.ui.components.formatMinutes
import dev.eduarddragu.anotherhabittracker.ui.components.gutter
import dev.eduarddragu.anotherhabittracker.ui.components.rememberArrival
import dev.eduarddragu.anotherhabittracker.ui.components.rememberCommit
import dev.eduarddragu.anotherhabittracker.ui.components.rise
import dev.eduarddragu.anotherhabittracker.ui.components.screenPadding
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

class HabitDetailViewModel(app: HabitApp, habitId: Long) : HabitViewModel(app, habitId) {
  /** Marks a topic known and hands back what was newly marked, for the undo. */
  fun markKnown(topicId: String, onMarked: (Set<String>) -> Unit) = once { onMarked(app.repository.markKnown(habitId, listOf(topicId))) }

  fun unmark(topicIds: Set<String>) = viewModelScope.launch { topicIds.forEach { app.repository.unmarkKnown(habitId, it) } }

  /** Uses this week's freeze on yesterday, which was missed. */
  fun freezeYesterday(onDone: (Boolean) -> Unit) = once { onDone(app.repository.freeze(habitId, app.repository.today().minusDays(1))) }

  fun deleteEntry(id: Long) = once { app.repository.deleteEntry(id) }

  /** Keep going on [topicId] today (a deep dive); null goes back to the picker's topic. */
  fun keepGoing(topicId: String?) = app.repository.keepGoing(habitId, topicId)
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
  var removingFreeze by rememberSaveable { mutableStateOf<Long?>(null) }
  // Title, streak, topic, actions, numbers, history: arriving in that order as the page slides in, each
  // settling after the one above it. The topic card is the page's main content, so it takes its time:
  // slower, from further down.
  val arrival = rememberArrival(6, delayOf = { ARRIVAL_DELAYS[it] }, durationOf = { if (it == 2) 720 else 520 })

  // Vertical padding only: the year heatmap runs edge to edge, everything else sits in the gutter.
  // Order: where the habit stands (streak), what to do now (topic or Log), then the numbers and history.
  LazyColumn(modifier, contentPadding = screenPadding(horizontal = 0.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
    item {
      // Settings belong to the habit, not to today's topic, so they sit with the title.
      Row(Modifier.gutter().rise(arrival[0], 16.dp), verticalAlignment = Alignment.Bottom) {
        ScreenTitle("Habit", status.habit.name, Modifier.weight(1f), icon = status.habit.resolvedIcon)
        IconButton(onClick = onSettings) { Icon(painterResource(R.drawable.ic_settings), contentDescription = "Settings", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
      }
    }
    item {
      StreakBlock(
        status,
        commit,
        onFreezeYesterday = { viewModel.freezeYesterday { ok -> onMessage(if (ok) "Yesterday is frozen" else "Freeze not available") } },
        modifier = Modifier.gutter().rise(arrival[1], 16.dp),
      )
    }
    if (study) {
      item {
        TodaysTopic(
          status,
          viewModel.curriculum,
          onLog = onLog,
          onKnown = { id -> viewModel.markKnown(id) { marked -> if (marked.isNotEmpty()) onUndoable("Marked as known") { viewModel.unmark(marked) } } },
          onKeepGoing = viewModel::keepGoing,
          onCurriculum = onCurriculum,
          modifier = Modifier.gutter().rise(arrival[2], 32.dp),
        )
      }
    }
    // Meditation gets today's practice, with its actions inside the card like the study topic.
    val practice = if (!study && Practices.appliesTo(status.habit.name, status.habit.linkedPackage)) Practices.forDay(status.today) else null
    if (practice != null) {
      item { TodaysPractice(status, practice, onLog = onLog, modifier = Modifier.gutter().rise(arrival[2], 32.dp)) }
    } else if (!study || status.habit.linkedPackage != null) {
      item {
        FlowRow(Modifier.gutter().rise(arrival[3], 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          if (!study) LogButton(status.doneToday, onLog)
          OpenLinkedAppButton(status.habit.linkedPackage)
        }
      }
    }
    // No numbers before there is something to count: a row of zeros only says the app is new.
    if (status.recent.isNotEmpty()) item { StatRow(status, Modifier.gutter().rise(arrival[4], 16.dp)) }
    if (Heatmap.worthShowing(status.today, status.since)) item { YearHeatmap(status, Modifier.rise(arrival[4], 16.dp)) }
    // The heading travels with its first entry (8dp inside a section), not a whole section gap above it.
    if (status.recent.isEmpty()) {
      item {
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
          topic = entry.topicId?.let { viewModel.curriculum.byId[it]?.title ?: it },
          onKeepGoing = if (entry.id == keepGoingRow) ({ viewModel.keepGoing(keepGoing) }) else null,
          last = entry == recent.last(),
        )
      }
    }
  }
  removingFreeze?.let { id ->
    AlertDialog(
      onDismissRequest = { removingFreeze = null },
      title = { Text("Remove this freeze?") },
      text = { Text("The day goes back to missed, and this week's freeze is free again.") },
      confirmButton = {
        TextButton(
          onClick = {
            removingFreeze = null
            viewModel.deleteEntry(id)
          }
        ) {
          Text("Remove")
        }
      },
      dismissButton = { TextButton(onClick = { removingFreeze = null }) { Text("Cancel") } },
    )
  }
}

/**
 * Where the habit stands: the streak, big, and this week under it. During a commit the day fills in,
 * the number rolls and the line under it changes, in that order.
 */
@Composable
private fun StreakBlock(status: HabitStatus, commit: CommitPlayback, onFreezeYesterday: () -> Unit, modifier: Modifier = Modifier) {
  val stats = status.stats
  val colors = MaterialTheme.colorScheme
  val shown = if (commit.rolled) stats.streak else commit.commit?.previousStreak ?: stats.streak
  val numberColor by animateColorAsState(if (shown > 0) colors.primary else colors.onSurfaceVariant, tween(Motion.LONG, easing = Motion.EaseUi), label = "streak")
  Column(modifier) {
    // The caption's last line sits on the number's baseline.
    Row {
      RollingNumber(shown, NumeralsDisplay, numberColor, Modifier.alignBy(LastBaseline))
      Spacer(Modifier.width(12.dp))
      Column(Modifier.alignBy(LastBaseline)) {
        Text(if (shown == 1) "DAY" else "DAYS", style = MaterialTheme.typography.labelMedium, color = colors.primary)
        Text("IN A ROW", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
      }
    }
    Spacer(Modifier.height(14.dp))
    WeekStrip(status.cells, status.today, cellSize = 28.dp, gap = 8.dp, initials = true, commit = commit)
    Spacer(Modifier.height(12.dp))
    // The week, and whether this week's freeze is still there to use.
    val freeze = if (status.summary.freezeUsedOn != null) "freeze used" else "freeze ready"
    val week = "${stats.daysThisWeek}/7 this week, $freeze"
    val line =
      when {
        !commit.settled -> null
        stats.streak == 0 && stats.best == 0 -> "Log today to start the count."
        stats.streak == 0 -> "Best was ${stats.best}. Log today to start again."
        status.doneToday && stats.streak >= stats.best -> "Day ${stats.streak}, your best so far. See you tomorrow."
        status.doneToday -> "Day ${stats.streak}. See you tomorrow."
        stats.streak >= stats.best -> "Your best so far. $week."
        else -> "Best: ${stats.best}. $week."
      }
    AnimatedContent(
      targetState = line,
      transitionSpec = { fadeThrough() },
      label = "streak line",
    ) { text ->
      Text(text ?: " ", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
    }
    // Yesterday slipped through but the streak can still be saved: offer the freeze, once.
    if (status.summary.canFreezeYesterday && !status.doneToday) {
      Row(Modifier.fillMaxWidth()) {
        Text("Missed yesterday.", style = MaterialTheme.typography.bodyMedium, color = colors.primary, modifier = Modifier.weight(1f).alignByBaseline())
        TextAction("Freeze it", onClick = onFreezeYesterday, modifier = Modifier.alignByBaseline())
      }
    }
  }
}

/** Meditation's counterpart to today's topic: a small practice for today's session, in three steps. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TodaysPractice(status: HabitStatus, practice: Practice, onLog: () -> Unit, modifier: Modifier = Modifier) {
  DayCard(status.doneToday, modifier) {
    CardLabel(listOf(if (status.doneToday) "DONE TODAY" else "TODAY", "PRACTICE"), accent = if (status.doneToday) "DONE TODAY" else "PRACTICE")
    Text(practice.title, style = MaterialTheme.typography.headlineSmall)
    NumberedSteps(practice.steps, startDelay = 560L)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      LogButton(status.doneToday, onLog)
      OpenLinkedAppButton(status.habit.linkedPackage)
    }
  }
}

/** A card's mono label: muted, with the one part that says the state in the accent (same as Home). */
@Composable
private fun CardLabel(parts: List<String>, accent: String?) {
  val colors = MaterialTheme.colorScheme
  Text(
    buildAnnotatedString {
      parts.forEachIndexed { index, part ->
        if (index > 0) append(" · ")
        if (part == accent) withStyle(SpanStyle(color = colors.primary)) { append(part) } else append(part)
      }
    },
    style = MaterialTheme.typography.labelMedium,
    color = colors.onSurfaceVariant,
  )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TodaysTopic(
  status: HabitStatus,
  curriculum: Curriculum,
  onLog: () -> Unit,
  onKnown: (String) -> Unit,
  onKeepGoing: (String?) -> Unit,
  onCurriculum: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val haptics = LocalHapticFeedback.current
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
          CardLabel(listOf("CURRICULUM COMPLETE"), accent = null)
          Text("Nothing left to pick. Time to add topics.", style = MaterialTheme.typography.bodyLarge)
          TextAction("See the curriculum", onClick = onCurriculum)
          return@Column
        }
        val area = (curriculum.areaById[pick.topic.area]?.name ?: pick.topic.area).uppercase()
        val state = if (status.doneToday) "DONE TODAY" else pickLabel(pick.kind)
        CardLabel(listOfNotNull("TODAY".takeIf { !status.doneToday }, state, area), accent = state)
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
          LogButton(status.doneToday, onLog)
          // Not once today is logged: it would swap out the topic just studied.
          if (!status.doneToday) {
            TextButton(
              onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.ToggleOn)
                onKnown(pick.topic.id)
              }
            ) {
              Text("I know this")
            }
          }
          if (pick.kind == PickKind.CONTINUE && !status.doneToday) TextButton(onClick = { onKeepGoing(null) }) { Text("Back to today's topic") }
          TextButton(onClick = onCurriculum) { Text("See the curriculum") }
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
        HeatmapGrid(Heatmap.weeks(status.today, weeks), status.cells, status.today, cellSize = cell, gap = HEATMAP_GAP)
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
      stats.minutesLast30.takeIf { it > 0 }?.let { formatMinutes(it) to "minutes, 30 days" },
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
) {
  val frozen = entry.type == EntryType.FREEZE
  val facts =
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
  var rowTop by remember { mutableFloatStateOf(0f) }
  var dateTop by remember { mutableFloatStateOf(Float.NaN) }
  var dateBaseline by remember { mutableFloatStateOf(0f) }
  Row(modifier.fillMaxWidth().height(IntrinsicSize.Min).clickable(onClick = onClick).onGloballyPositioned { rowTop = it.positionInRoot().y }) {
    Spacer(
      Modifier.width(18.dp).fillMaxHeight().drawBehind {
        val x = 3.dp.toPx()
        val capHeight = dateStyle.fontSize.toPx() * MONO_CAP_HEIGHT
        val dotY = if (dateTop.isNaN()) 7.dp.toPx() else dateTop - rowTop + dateBaseline - capHeight / 2
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
          modifier = Modifier.weight(1f).alignByBaseline().onGloballyPositioned { dateTop = it.positionInRoot().y },
        )
        if (onKeepGoing != null) {
          // A text action with no button box: it doesn't make the date line taller than the date.
          TextAction("Keep going today", onClick = onKeepGoing, modifier = Modifier.alignByBaseline(), vertical = 0.dp)
        }
      }
      // A study session leads with what it was about; the numbers follow.
      if (topic != null) {
        // The same face and accent as the actions: the topic reads as the headline of the session.
        Text(topic, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        if (facts.isNotEmpty()) Text(facts.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
      } else {
        Text(if (frozen) "Frozen" else facts.joinToString(" · ").ifEmpty { "Done" }, style = MaterialTheme.typography.bodyLarge, color = if (frozen) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified)
      }
      if (entry.extraTopics.isNotBlank()) Text("Also: ${entry.extraTopics}", style = MaterialTheme.typography.bodyMedium)
      if (entry.note.isNotBlank()) Text(entry.note, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}

private fun averagePerSession(totalMinutes: Int, sessions: Int): String? = if (sessions == 0 || totalMinutes == 0) null else formatMinutes(totalMinutes / sessions)
