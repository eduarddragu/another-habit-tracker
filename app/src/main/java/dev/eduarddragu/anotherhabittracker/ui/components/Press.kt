package dev.eduarddragu.anotherhabittracker.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import dev.eduarddragu.anotherhabittracker.theme.Motion
import kotlinx.coroutines.launch

/**
 * Scale for a pressable surface: dips to 0.97 fast on press, and settles back on release with a
 * critically damped spring (physical, interruptible, no bounce). Read it inside graphicsLayer.
 */
@Composable
fun pressScale(interaction: InteractionSource): Animatable<Float, AnimationVector1D> {
  val scale = remember { Animatable(1f) }
  LaunchedEffect(interaction) {
    interaction.interactions.collect { event ->
      when (event) {
        is PressInteraction.Press -> launch { scale.animateTo(0.97f, tween(100, easing = Motion.EaseUi)) }
        is PressInteraction.Release,
        is PressInteraction.Cancel -> launch { scale.animateTo(1f, spring(dampingRatio = 1f, stiffness = Spring.StiffnessMedium)) }
      }
    }
  }
  return scale
}
