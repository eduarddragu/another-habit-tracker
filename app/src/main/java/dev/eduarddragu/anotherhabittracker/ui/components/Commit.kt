package dev.eduarddragu.anotherhabittracker.ui.components

import android.os.SystemClock
import android.provider.Settings
import androidx.annotation.MainThread
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import dev.eduarddragu.anotherhabittracker.theme.Motion
import java.time.LocalDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** A session or freeze just written. The screen the user lands on afterwards plays it once. */
data class Commit(val habitId: Long, val day: LocalDate, val previousStreak: Int, val frozen: Boolean = false, val at: Long = SystemClock.elapsedRealtime())

/** Hand-off between the log form, which writes, and the screen underneath it, which celebrates. */
object Commits {
  private var pending: Commit? = null

  @MainThread
  fun post(commit: Commit) {
    pending = commit
  }

  /** The pending commit for [habitId], if it's fresh: one nobody played within a few seconds is stale. */
  @MainThread
  fun take(habitId: Long): Commit? =
    pending?.takeIf { it.habitId == habitId }?.also { pending = null }?.takeIf { SystemClock.elapsedRealtime() - it.at < FRESH_MS }

  /** True while a commit waits to be played: the screen underneath shouldn't also replay its entrance. */
  @MainThread fun waiting(): Boolean = pending?.let { SystemClock.elapsedRealtime() - it.at < FRESH_MS } == true

  private const val FRESH_MS = 15_000L
}

/**
 * The state of one commit's choreography. Without a commit everything starts settled, so a screen
 * opened normally shows its final state on the first frame.
 */
@Stable
class CommitPlayback(val commit: Commit?) {
  /** Today's square: 0 = still open, 1 = filled. */
  val fill = Animatable(if (commit == null) 1f else 0f)

  /** The ring that leaves the square as it fills: 0 = at the square, 1 = grown and gone. */
  val ring = Animatable(1f)

  /** The streak shows its new value. */
  var rolled by mutableStateOf(commit == null)

  /** Labels and sublines show their new text. */
  var settled by mutableStateOf(commit == null)

  fun animating(day: LocalDate): Boolean = commit?.day == day
}

/** True when the system's animations are off: every delay and stagger must be skipped too. */
@Composable
fun rememberReducedMotion(): Boolean {
  val context = LocalContext.current
  return remember { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
}

/**
 * Plays a pending commit for [habitId] once the screen has finished arriving: the square fills
 * (with the haptic, so touch and picture land together), then the streak rolls, then the labels
 * change. Timings live in [Motion.Commit].
 */
@Composable
fun rememberCommit(habitId: Long): CommitPlayback {
  val playback = remember(habitId) { CommitPlayback(Commits.take(habitId)) }
  val reduced = rememberReducedMotion()
  val haptics = LocalHapticFeedback.current
  val transition = LocalNavAnimatedContentScope.current.transition
  LaunchedEffect(playback) {
    if (playback.commit == null) return@LaunchedEffect
    if (reduced) {
      haptics.performHapticFeedback(HapticFeedbackType.Confirm)
      playback.fill.snapTo(1f)
      playback.rolled = true
      playback.settled = true
      return@LaunchedEffect
    }
    // Wait for the log sheet to be gone, so the fill is actually seen.
    snapshotFlow { transition.currentState == EnterExitState.Visible && !transition.isRunning }.first { it }
    playback.ring.snapTo(0f)
    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
    launch { playback.fill.animateTo(1f, tween(360, easing = Motion.EaseEntrance)) }
    launch { playback.ring.animateTo(1f, tween(480, easing = Motion.EaseUi)) }
    delay(Motion.Commit.ROLL.toLong())
    playback.rolled = true
    delay((Motion.Commit.SETTLE - Motion.Commit.ROLL).toLong())
    playback.settled = true
  }
  return playback
}
