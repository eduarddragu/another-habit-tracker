package dev.eduarddragu.anotherhabittracker.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.eduarddragu.anotherhabittracker.theme.Motion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * A screen's content arriving in order while the screen slides in: [count] steps, by default from
 * +140 ms, 60 ms apart, 520 ms each with the entrance easing; [delayOf] and [durationOf] override that
 * per step. Plays the first time the screen is shown only (the flag
 * is saved with the screen's state, so coming back to it from the log form or settings replays
 * nothing). With the system's animations off, everything is simply there.
 */
@Composable
fun rememberArrival(
  count: Int,
  delayOf: (Int) -> Long = { 140L + 60L * it },
  durationOf: (Int) -> Int = { 520 },
): List<Animatable<Float, AnimationVector1D>> {
  val reduced = rememberReducedMotion()
  var arrived by rememberSaveable { mutableStateOf(false) }
  val play = remember { !arrived && !reduced }
  val steps = remember(count) { List(count) { Animatable(if (play) 0f else 1f) } }
  LaunchedEffect(Unit) {
    arrived = true
    if (!play) return@LaunchedEffect
    steps.forEachIndexed { index, step ->
      launch {
        delay(delayOf(index))
        step.animateTo(1f, tween(durationOf(index), easing = Motion.EaseEntrance))
      }
    }
  }
  return steps
}

/** Fade and rise by [distance] as [step] goes from 0 to 1, read at draw time. */
fun Modifier.rise(step: Animatable<Float, *>?, distance: Dp = 8.dp): Modifier =
  if (step == null) this
  else
    graphicsLayer {
      val p = step.value
      alpha = p
      translationY = (1f - p) * distance.toPx()
    }
