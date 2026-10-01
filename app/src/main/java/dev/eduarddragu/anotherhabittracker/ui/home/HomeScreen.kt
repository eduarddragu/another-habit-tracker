package dev.eduarddragu.anotherhabittracker.ui.home

import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.EnterTransition
import dev.eduarddragu.anotherhabittracker.domain.HabitKind
import dev.eduarddragu.anotherhabittracker.domain.SessionTimer
import dev.eduarddragu.anotherhabittracker.domain.SessionPhase
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.Box
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.expandVertically
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.eduarddragu.anotherhabittracker.HabitApp
import dev.eduarddragu.anotherhabittracker.R
import dev.eduarddragu.anotherhabittracker.backup.NightlyState
import dev.eduarddragu.anotherhabittracker.data.HabitRepository
import dev.eduarddragu.anotherhabittracker.data.HabitStatus
import dev.eduarddragu.anotherhabittracker.data.resolvedIcon
import dev.eduarddragu.anotherhabittracker.domain.Curriculum
import dev.eduarddragu.anotherhabittracker.domain.Milestones
import dev.eduarddragu.anotherhabittracker.domain.Motivation
import dev.eduarddragu.anotherhabittracker.domain.dayCount
import dev.eduarddragu.anotherhabittracker.domain.Practices
import dev.eduarddragu.anotherhabittracker.domain.formatTime
import dev.eduarddragu.anotherhabittracker.domain.parseReminderTimes
import dev.eduarddragu.anotherhabittracker.theme.Motion
import dev.eduarddragu.anotherhabittracker.theme.NumeralsLarge
import dev.eduarddragu.anotherhabittracker.theme.fadeThrough
import dev.eduarddragu.anotherhabittracker.ui.backup.formatSaved
import dev.eduarddragu.anotherhabittracker.ui.components.CommitPlayback
import dev.eduarddragu.anotherhabittracker.ui.components.Commits
import dev.eduarddragu.anotherhabittracker.ui.components.HabitIconImage
import dev.eduarddragu.anotherhabittracker.ui.components.Planet
import dev.eduarddragu.anotherhabittracker.ui.components.RollingNumber
import dev.eduarddragu.anotherhabittracker.ui.components.TextAction
import dev.eduarddragu.anotherhabittracker.ui.components.WeekStrip
import dev.eduarddragu.anotherhabittracker.ui.components.pressScale
import dev.eduarddragu.anotherhabittracker.ui.components.rememberCommit
import dev.eduarddragu.anotherhabittracker.ui.components.rememberReducedMotion
import dev.eduarddragu.anotherhabittracker.ui.components.rise
import dev.eduarddragu.anotherhabittracker.ui.components.screenPadding
import dev.eduarddragu.anotherhabittracker.ui.detail.pickLabel
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeState(val today: LocalDate, val habits: List<HabitStatus>)

class HomeViewModel(private val repository: HabitRepository) : ViewModel() {
  val state: StateFlow<HomeState?> =
    repository
      .observeStatuses()
      .map { statuses -> HomeState(statuses.firstOrNull()?.today ?: repository.today(), statuses) }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

  val curriculum: Curriculum
    get() = repository.curriculum
}

private val DATE_FORMAT = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)
private const val HERO_STEPS = 4
private const val MAX_CARDS = 6

/** Room around the name for its blur, which the offscreen layer would otherwise clip. */
private val BLUR_ROOM = 10.dp

/**
 * The front door: the date and how much of today is done, a greeting, the line of the day, the planet,
 * then one card per habit with this week and the streak. Details live one tap away on the habit's page.
 */
@Composable
fun HomeScreen(
  app: HabitApp,
  notificationsEnabled: Boolean,
  deliveryProblems: List<String>,
  onEnableNotifications: () -> Unit,
  onOpen: (Long) -> Unit,
  onBackup: () -> Unit,
  onGuard: () -> Unit,
  /** Opens the log form for a finished session: its habit, the minutes it counted, its day. */
  onLogSession: (habitId: Long, minutes: Int?, day: Long) -> Unit,
  modifier: Modifier = Modifier,
  viewModel: HomeViewModel = viewModel { HomeViewModel(app.repository) },
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val current = state ?: return
  val done = current.habits.count { it.doneToday || it.frozenToday }
  val everythingDone = current.habits.isNotEmpty() && done == current.habits.size
  val streak = current.habits.maxOfOrNull { it.streak } ?: 0
  // Read before rememberIntro marks the entrance as played.
  val firstRun = remember { !HomeOnce.introPlayed }
  val now = rememberMinute()
  val intro = rememberIntro(current.today)
  val pulse = rememberLastCallPulse(current.today, current.habits.any { it.summary.dayOpen && lastCall(it, now) != null }, firstRun)

  // Three zones down the screen: who and when at the top, the planet in the middle, what to do at the
  // bottom where the thumb is. When the content outgrows the screen it simply scrolls.
  BoxWithConstraints(modifier) {
    Column(
      Modifier.verticalScroll(rememberScrollState()).heightIn(min = maxHeight).padding(screenPadding(top = 16.dp, bottom = 40.dp)),
      verticalArrangement = Arrangement.SpaceBetween,
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      // A focus session turns the planet into its timer, and the page around it into the session.
      val session by app.sessions.session.collectAsStateWithLifecycle()
      val tick by rememberSessionTick(app, session)
      val reduced = rememberReducedMotion()
      // The session stays on screen until the planet has turned back, so leaving is as soft as arriving.
      var shown by remember { mutableStateOf(session) }
      if (session != null) shown = session
      val morph = remember { Animatable(if (session != null) 1f else 0f) }
      LaunchedEffect(session != null) {
        val target = if (session != null) 1f else 0f
        if (reduced) morph.snapTo(target) else morph.animateTo(target, tween(720, easing = if (session != null) Motion.EaseEntrance else Motion.EaseUi))
        if (session == null) shown = null
      }
      val active = shown
      val phase = active?.phase(tick)
      // The ring closes once it's over (not for one too short to count): one calm "done".
      val closed by animateFloatAsState(if (phase == SessionPhase.FINISHED && active.minutesToLog(tick) != null) 1f else 0f, tween(if (reduced) 0 else Motion.LONG * 2, easing = Motion.EaseEntrance), label = "closed")
      KeepScreenOn(phase == SessionPhase.RUNNING)
      val haptics = LocalHapticFeedback.current
      FinishHaptic(active, tick) { haptics.performHapticFeedback(HapticFeedbackType.Confirm) }
      val study = current.habits.firstOrNull { it.habit.kind == HabitKind.STUDY }
      var choosing by rememberSaveable { mutableStateOf(false) }
      // A tap on the planet offers the session for a few seconds, then lets it go.
      LaunchedEffect(choosing) {
        if (choosing) {
          delay(6_000)
          choosing = false
        }
      }
      val rise24 = with(LocalDensity.current) { 24.dp.roundToPx() }
      val sizeSpec = tween<androidx.compose.ui.unit.IntSize>(if (reduced) 0 else Motion.LONG, easing = Motion.EaseUi)

      AnimatedContent(
        targetState = active != null,
        transitionSpec = { (if (reduced) EnterTransition.None togetherWith ExitTransition.None else fadeThrough()) using SizeTransform { _, _ -> sizeSpec } },
        label = "top",
      ) { inSession ->
        if (inSession && active != null) {
          SessionHeader(active, tick)
        } else {
          // A streak reaching a milestone today takes over the line of the day.
          val milestone = current.habits.filter { it.doneToday }.firstNotNullOfOrNull { Milestones.line(it.streak) }
          Hero(current.today, now, "$done OF ${current.habits.size} DONE", milestone ?: Motivation.line(current.today, everythingDone, streak), intro)
        }
      }
      // Still while it's a timer: nothing moves but the arc, once a second.
      val idle = rememberPlanetIdle(waitForEntrance = firstRun, enabled = active == null)
      // A little more air under the planet than above it: it separates the greeting from the cards.
      // Tapped, it offers a session; during one, it is the timer.
      val press = remember { MutableInteractionSource() }
      val pressed by press.collectIsPressedAsState()
      val dip by animateFloatAsState(if (pressed && !reduced) 0.97f else 1f, tween(if (pressed) Motion.PRESS else 160, easing = if (pressed) Motion.EasePress else Motion.EaseUi), label = "dip")
      Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)) {
        Box(
          contentAlignment = Alignment.Center,
          modifier =
            Modifier.graphicsLayer {
                scaleX = dip
                scaleY = dip
              }
              .clickable(interactionSource = press, indication = null, enabled = active == null && study != null, onClickLabel = "Offer a focus session", role = Role.Button) { choosing = !choosing },
        ) {
          Planet(
            disc = 88.dp,
            settle = { intro[2].value },
            drift = { idle.drift.value },
            squash = { idle.squash.value },
            breathe = { idle.breathe.value },
            morph = { morph.value },
            // Over (ran out or ended early): no arc left; the closed ring says done when it counted.
            left = { if (phase == SessionPhase.FINISHED) 0f else active?.left(tick) ?: 1f },
            paused = { phase == SessionPhase.PAUSED },
            closed = { closed },
          )
          if (active != null) SessionNumerals(active, tick, Modifier.graphicsLayer { alpha = ((morph.value - 0.5f) * 2f).coerceIn(0f, 1f); translationY = if (reduced) 0f else (1f - alpha) * 8.dp.toPx() })
        }
        // A fixed slot, so offering the session never pushes the cards around.
        Box(Modifier.height(64.dp), contentAlignment = Alignment.Center) {
          androidx.compose.animation.AnimatedVisibility(
            visible = choosing && active == null && study != null,
            enter = fadeIn(tween(Motion.LIST, easing = Motion.EaseUi)) + slideInVertically(tween(Motion.LIST, easing = Motion.EaseUi)) { it / 4 },
            exit = fadeOut(tween(Motion.FADE_OUT)),
          ) {
            if (study != null) {
              val minutes = suggestedMinutes(study)
              StartSession(minutes, onStart = {
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                choosing = false
                startSession(app, study, minutes)
              })
            }
          }
        }
      }
      AnimatedContent(
        targetState = active != null,
        transitionSpec = {
          if (reduced) EnterTransition.None togetherWith ExitTransition.None
          else (fadeIn(tween(Motion.ENTRANCE, delayMillis = 240, easing = Motion.EaseEntrance)) + slideInVertically(tween(Motion.ENTRANCE, 240, Motion.EaseEntrance)) { rise24 }) togetherWith fadeOut(tween(Motion.FADE_OUT))
        },
        label = "bottom",
      ) { inSession ->
      if (inSession && active != null) {
        SessionCard(app, active, tick, current.habits.firstOrNull { it.habit.id == active.habitId }, viewModel.curriculum, onLog = onLogSession)
      } else Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!notificationsEnabled) {
          WarningCard("Notifications are off", "Without them there are no reminders.", "Turn on", onEnableNotifications)
        } else if (deliveryProblems.isNotEmpty()) {
          WarningCard("Reminders may not arrive", deliveryProblems.joinToString(" "), null, null)
        }
        current.habits.take(MAX_CARDS).forEachIndexed { index, status ->
          val step = intro.getOrNull(HERO_STEPS + index)
          // Keyed by habit, so a card's state (its commit) stays with its habit if the order changes.
          key(status.habit.id) {
            HabitCard(status, now, viewModel.curriculum, pulse = { pulse.value }, appear = { step?.value ?: 1f }, onClick = { onOpen(status.habit.id) })
          }
        }
        val nightly by app.backups.nightly.collectAsStateWithLifecycle()
        // Turned on or off in the system settings, so read again whenever Home comes back.
        var guardOn by remember { mutableStateOf(app.guard.serviceEnabled()) }
        LifecycleResumeEffect(Unit) {
          guardOn = app.guard.serviceEnabled()
          onPauseOrDispose {}
        }
        FooterLine(nightly, guardOn, onBackup, onGuard, Modifier.rise(intro.last()))
      }
      }
    }
  }
}

/**
 * Under the cards: the backup at a glance (a warning when the nightly copy failed or hasn't run for
 * two days) and the scroll guard's state, each opening its screen. Two plain labels, each with a full
 * touch height, rather than links inside one line.
 */
@Composable
private fun FooterLine(nightly: NightlyState, guardOn: Boolean, onBackup: () -> Unit, onGuard: () -> Unit, modifier: Modifier = Modifier) {
  val saved = nightly.lastSaved
  val stale = nightly.uri != null && (saved == null || Duration.between(saved, Instant.now()) > Duration.ofHours(48))
  val warning = nightly.lastError != null || stale
  Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
    when {
      nightly.lastError != null -> WarningCard("Backup failed", nightly.lastError, "Fix", onBackup)
      stale -> WarningCard("Backup is behind", saved?.let { "Last saved ${formatSaved(it)}." } ?: "It hasn't saved yet.", "Fix", onBackup)
    }
    val style = MaterialTheme.typography.labelSmall
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val accent = SpanStyle(color = MaterialTheme.colorScheme.primary)
    Row(verticalAlignment = Alignment.CenterVertically) {
      if (!warning) {
        Text(
          buildAnnotatedString {
            append(if (saved != null) "BACKED UP ${formatSaved(saved).uppercase()} · " else "NO NIGHTLY BACKUP YET · ")
            // The part that is the action, in the accent like every other action.
            withStyle(accent) { append(if (saved != null) "BACKUP" else "SET UP") }
          },
          style = style,
          color = muted,
          modifier = Modifier.clickable(role = Role.Button, onClick = onBackup).padding(vertical = 16.dp),
        )
        Text(" · ", style = style, color = muted)
      }
      Text(
        buildAnnotatedString {
          append("SCROLL GUARD ")
          withStyle(accent) { append(if (guardOn) "ON" else "OFF") }
        },
        style = style,
        color = muted,
        modifier = Modifier.clickable(role = Role.Button, onClick = onGuard).padding(vertical = 16.dp),
      )
    }
  }
}

@Composable
private fun Hero(today: LocalDate, now: LocalTime, progress: String, line: String, intro: List<Animatable<Float, AnimationVector1D>>) {
  val name = stringResource(R.string.owner_name)
  Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
    // Muted, so the name is the one warm thing at the top.
    Text("${today.format(DATE_FORMAT).uppercase()} · $progress", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.rise(intro[0]))
    Spacer(Modifier.height(18.dp))
    val greeting = Motivation.greeting(now, today)
    val display = MaterialTheme.typography.displaySmall.copy(fontSize = 48.sp, lineHeight = 50.sp, letterSpacing = (-1).sp)
    Text("${greeting.lead},", style = display, textAlign = TextAlign.Center, modifier = Modifier.rise(intro[0], 16.dp))
    // The name in the accent italic, revealed left to right while it comes into focus.
    RevealText(
      name + if (greeting.question) "?" else "",
      style = display.copy(fontSize = 56.sp, lineHeight = 60.sp, fontStyle = FontStyle.Italic, fontWeight = FontWeight.Medium),
      color = MaterialTheme.colorScheme.primary,
      progress = { intro[3].value },
    )
    Spacer(Modifier.height(16.dp))
    // The line swaps to the all-done pool the moment the last habit is logged: a fade-through, never
    // two lines on top of each other.
    AnimatedContent(
      targetState = line,
      transitionSpec = { fadeThrough() },
      modifier = Modifier.rise(intro[1]),
      label = "line",
    ) { text ->
      Text(
        text,
        style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, lineHeight = 26.sp, fontStyle = FontStyle.Italic, fontWeight = FontWeight.Medium),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.widthIn(max = 320.dp),
      )
    }
  }
}

/**
 * Text revealed by a soft edge sweeping left to right, while it rises 10dp and goes from a 10dp blur to
 * sharp. All of it happens in the layer at draw time, and the offscreen buffer the mask needs exists only
 * while it plays. The padding leaves room for italic overhangs and the blur, which the layer would
 * otherwise clip.
 */
@Composable
private fun RevealText(text: String, style: TextStyle, color: Color, progress: () -> Float) {
  Text(
    text,
    style = style,
    color = color,
    textAlign = TextAlign.Center,
    modifier =
      // The layer needs room above and below for the blur, but the greeting, the name and the line
      // must stay one tight block: report the text's own height and let the extra room overlap.
      Modifier.layout { measurable, constraints ->
          val placeable = measurable.measure(constraints)
          val room = BLUR_ROOM.roundToPx()
          layout(placeable.width, placeable.height - 2 * room) { placeable.place(0, -room) }
        }
        .graphicsLayer {
          val p = progress()
          compositingStrategy = if (p < 1f) CompositingStrategy.Offscreen else CompositingStrategy.Auto
          // Rise and blur match the site's name reveal: 8 each.
          translationY = (1f - p) * 8.dp.toPx()
          // Whole pixels: a new effect only when the radius actually changes.
          val blur = ((1f - p) * 8.dp.toPx()).roundToInt().toFloat()
          renderEffect = if (blur >= 1f) BlurEffect(blur, blur, TileMode.Decal) else null
        }
        .drawWithContent {
          val p = progress()
          drawContent()
          if (p < 1f) {
            val feather = size.width * 0.35f
            val start = p * (size.width + feather) - feather
            drawRect(Brush.horizontalGradient(listOf(Color.Black, Color.Transparent), startX = start, endX = start + feather), blendMode = BlendMode.DstIn)
          }
        }
        .padding(horizontal = 12.dp, vertical = BLUR_ROOM),
  )
}

/**
 * The shared shell of a habit card: a tonal surface (warm once done, with the change played by the
 * commit), a press that dips and settles back critically damped, and the entrance.
 */
@Composable
private fun HabitSurface(status: HabitStatus, commit: CommitPlayback, appear: () -> Float, onClick: () -> Unit, content: @Composable () -> Unit) {
  val colors = MaterialTheme.colorScheme
  val interaction = remember { MutableInteractionSource() }
  val press = pressScale(interaction)
  val open = colors.surfaceContainerLow
  val warm = colors.primaryContainer
  val shape = RoundedCornerShape(24.dp)
  Column(
    Modifier.fillMaxWidth()
      .graphicsLayer {
        // Entrance and press read at draw time: no recomposition per frame.
        val p = appear()
        alpha = p
        translationY = (1f - p) * 24.dp.toPx()
        scaleX = press.value
        scaleY = press.value
      }
      .clip(shape)
      .drawBehind {
        val progress = if (commit.animating(status.today)) commit.fill.value else 1f
        drawRect(if (status.doneToday) lerp(open, warm, progress) else open)
      }
      .clickable(interaction, indication = null, onClick = onClick)
  ) {
    content()
  }
}

/**
 * One card per habit. A study habit leads with the day's actual job (the topic and its first
 * question); the others with their name and what's next. Both end with this week and the streak.
 */
@Composable
private fun HabitCard(status: HabitStatus, now: LocalTime, curriculum: Curriculum, pulse: () -> Float, appear: () -> Float, onClick: () -> Unit) {
  val colors = MaterialTheme.colorScheme
  val commit = rememberCommit(status.habit.id)
  val pick = status.pick
  val practice = if (pick == null && Practices.appliesTo(status.habit.name, status.habit.linkedPackage)) Practices.forDay(status.today) else null
  HabitSurface(status, commit, appear, onClick) {
    Column(Modifier.padding(20.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        HabitIconImage(status.habit.resolvedIcon, size = 22.dp, tint = colors.primary)
        Spacer(Modifier.width(10.dp))
        val area = pick?.let { curriculum.areaById[it.topic.area]?.name ?: it.topic.area }?.uppercase()
        val state =
          when {
            commit.settled && status.doneToday -> "DONE"
            status.frozenToday -> "FROZEN"
            else -> pick?.let { pickLabel(it.kind) }
          }
        // The name leads only when the title is something else (the study topic, today's practice).
        // Muted throughout; the state (new, a review, continuing, done) is in the accent.
        val parts = listOfNotNull(status.habit.name.uppercase().takeIf { pick != null || practice != null }, state, area)
        Text(
          buildAnnotatedString {
            parts.forEachIndexed { index, part ->
              if (index > 0) append(" · ")
              if (part == state) withStyle(SpanStyle(color = colors.primary)) { append(part) } else append(part)
            }
          },
          style = MaterialTheme.typography.labelMedium,
          color = colors.onSurfaceVariant,
          maxLines = 1,
        )
      }
      Spacer(Modifier.height(14.dp))
      Text(pick?.topic?.title ?: practice?.title ?: status.habit.name, style = MaterialTheme.typography.headlineSmall, maxLines = 3)
      val firstStep = pick?.topic?.hints?.firstOrNull() ?: practice?.steps?.firstOrNull()
      val detail = (if (status.summary.dayOpen && lastCall(status, now) == null) firstStep else null) ?: contextLine(status, now)
      Spacer(Modifier.height(6.dp))
      Text(detail, style = MaterialTheme.typography.bodyMedium, color = if (urgent(status, now)) colors.primary else colors.onSurfaceVariant, maxLines = 2)
      Spacer(Modifier.height(20.dp))
      // The strip's squares stand on the streak's baseline, not on the bottom of the number's box.
      Row {
        // On the tonal card the default grey of a missed day nearly vanishes: one step darker.
        WeekStrip(status.cells, status.today, cellSize = 14.dp, gap = 5.dp, missed = colors.outline, commit = commit, pulse = pulse, modifier = Modifier.alignBy { it.measuredHeight })
        Spacer(Modifier.weight(1f))
        Streak(status, commit, Modifier.alignByBaseline())
      }
    }
  }
}

/** The streak and its unit, rolling to the new value during a commit. Nothing at all at zero: the strip says it. */
@Composable
private fun Streak(status: HabitStatus, commit: CommitPlayback, modifier: Modifier = Modifier) {
  val value = if (commit.rolled) status.streak else commit.commit?.previousStreak ?: status.streak
  if (value == 0) return
  // Number and unit sit on one baseline: aligning their boxes' bottoms left the unit hanging below.
  Row(modifier) {
    RollingNumber(value, NumeralsLarge, MaterialTheme.colorScheme.primary, Modifier.alignByBaseline())
    Spacer(Modifier.width(4.dp))
    // Same caption as the habit page: the unit in the accent.
    Text(if (value == 1) "DAY" else "DAYS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.alignByBaseline())
  }
}

@Composable
private fun WarningCard(title: String, body: String, action: String?, onAction: (() -> Unit)?) {
  OutlinedCard(border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary), shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Text(title, style = MaterialTheme.typography.titleMedium)
      Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
      if (action != null && onAction != null) TextAction(action, onClick = onAction)
    }
  }
}

/** The day's last reminder, once it has gone off; null before that. */
private fun lastCall(status: HabitStatus, now: LocalTime): LocalTime? = parseReminderTimes(status.habit.reminderTimes).lastOrNull()?.takeIf { !now.isBefore(it) }

/** Still open after the last call: the one "hot" state the home has. */
private fun urgent(status: HabitStatus, now: LocalTime): Boolean = status.summary.dayOpen && lastCall(status, now) != null

private fun contextLine(status: HabitStatus, now: LocalTime): String {
  val last = lastCall(status, now)
  return when {
    status.frozenToday -> "Frozen today. The streak is safe."
    status.doneToday -> "Done for today."
    last != null -> "Last call was ${formatTime(last)}." + if (status.streak > 0) " ${dayCount(status.streak)} on the line." else ""
    else -> {
      val next = parseReminderTimes(status.habit.reminderTimes).firstOrNull { it.isAfter(now) }
      if (next != null) "Next reminder at ${formatTime(next)}" else "No more reminders today"
    }
  }
}

/** What has already played in this process: the entrance, and the last-call pulse of the day. */
private object HomeOnce {
  var introPlayed = false
  var pulseOn: LocalDate? = null
}

/**
 * Entrance, top to bottom: date and greeting (16dp rise) at 0 ms, the name revealed over 1 s from +160,
 * the line at +120, the planet settles over 900 ms from +200, then the cards come up from the bottom (24dp) from +420 ms, 90 ms apart;
 * 480 ms with the entrance easing.
 *
 * The whole thing plays when the app starts. Coming back to the app from outside (launcher, widget,
 * Recents) replays the planet and the cards, reset while the app was out of sight so they never blink
 * off in front of you, a little later and slower so the return reads as an arrival. Coming back from a
 * habit's page brings the cards up again, lighter, together with the slide. With the system's
 * animations off, everything is simply there.
 */
@Composable
private fun rememberIntro(today: LocalDate): List<Animatable<Float, AnimationVector1D>> {
  val reduced = rememberReducedMotion()
  val play = remember { !HomeOnce.introPlayed && !reduced }
  // Back from a habit's page (the home is composed again, but the app had already started): cards only.
  // Not when a log was just saved over Home: the commit plays on the cards, which must be there already.
  val fromPage = remember { HomeOnce.introPlayed && !reduced && !Commits.waiting() }
  val steps = remember { List(HERO_STEPS + MAX_CARDS) { index -> Animatable(if (play || (fromPage && index >= HERO_STEPS)) 0f else 1f) } }
  val scope = rememberCoroutineScope()
  var returns by remember { mutableIntStateOf(0) }
  var away by remember { mutableStateOf(false) }
  LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
    if (reduced) return@LifecycleEventEffect
    away = true
    scope.launch { steps.drop(2).forEach { it.snapTo(0f) } }
  }
  LifecycleEventEffect(Lifecycle.Event.ON_START) {
    if (away) {
      away = false
      returns++
    }
  }
  LaunchedEffect(returns, today) {
    HomeOnce.introPlayed = true
    if (reduced) return@LaunchedEffect
    val outside = returns > 0
    steps.forEachIndexed { index, step ->
      if (step.value >= 1f) return@forEachIndexed
      val card = index - HERO_STEPS
      launch {
        delay(
          when {
            index == 0 -> 0L
            index == 1 -> 120L
            index == 2 -> if (outside) 0L else 200L
            index == 3 -> if (outside) 120L else 160L
            outside -> 300L + 110L * card
            play -> 420L + 90L * card
            else -> 90L + 70L * card
          }
        )
        val duration = if (index == 2) 900 else if (index == 3) 900 else if (outside) 560 else if (play) 480 else 420
        step.animateTo(1f, tween(duration, easing = Motion.EaseEntrance))
      }
    }
  }
  return steps
}

/**
 * The time, to the minute, while the home is on screen: "next reminder at", the last-call state and the
 * greeting follow the clock without waiting for something else to recompose.
 */
@Composable
private fun rememberMinute(): LocalTime {
  val now by produceState(LocalTime.now().truncatedTo(ChronoUnit.MINUTES)) {
    while (true) {
      delay(60_000 - System.currentTimeMillis() % 60_000 + 50)
      value = LocalTime.now().truncatedTo(ChronoUnit.MINUTES)
    }
  }
  return now
}

/**
 * Once a day, after the last call, today's open square sends out one ring: after the cards have
 * landed on a cold start, a little sooner otherwise. Never with animations off.
 */
@Composable
private fun rememberLastCallPulse(today: LocalDate, due: Boolean, firstRun: Boolean): Animatable<Float, AnimationVector1D> {
  val reduced = rememberReducedMotion()
  val pulse = remember { Animatable(0f) }
  LaunchedEffect(today, due) {
    if (!due || reduced || HomeOnce.pulseOn == today) return@LaunchedEffect
    HomeOnce.pulseOn = today
    delay(if (firstRun) 1400 else 700)
    pulse.snapTo(0f)
    pulse.animateTo(1f, tween(900, easing = Motion.EaseUi))
  }
  return pulse
}

private class PlanetIdle(val drift: State<Float>, val squash: State<Float>, val breathe: State<Float>)

/**
 * The planet's endless idle loop: the ring swings between -30 and -6 degrees (7 s each way) while it
 * opens and closes as if turning around the planet (5 s), and the disc breathes by 2% (4.5 s). Three
 * different periods, so the motion never repeats in step; a symmetric ease makes every turn-around
 * soft. It starts once the entrance has settled (or at once when coming back to the home). Frames only run while the home is on screen; with the
 * system's animations off the planet stays still.
 */
@Composable
private fun rememberPlanetIdle(waitForEntrance: Boolean, enabled: Boolean = true): PlanetIdle {
  if (rememberReducedMotion() || !enabled) return remember { PlanetIdle(mutableFloatStateOf(0f), mutableFloatStateOf(1f), mutableFloatStateOf(1f)) }
  val loop = rememberInfiniteTransition(label = "planet")
  // After the entrance on a cold start; right away when coming back, so the planet never sits still.
  val start = StartOffset(if (waitForEntrance) 1100 else 0)
  val drift = loop.animateFloat(-12f, 12f, infiniteRepeatable(tween(7_000, easing = Motion.EaseLoop), RepeatMode.Reverse, start), label = "drift")
  val squash = loop.animateFloat(0.8f, 1.2f, infiniteRepeatable(tween(5_000, easing = Motion.EaseLoop), RepeatMode.Reverse, start), label = "squash")
  val breathe = loop.animateFloat(1f, 1.02f, infiniteRepeatable(tween(4_500, easing = Motion.EaseLoop), RepeatMode.Reverse, start), label = "breathe")
  return remember(drift, squash, breathe) { PlanetIdle(drift, squash, breathe) }
}

