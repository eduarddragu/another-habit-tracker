package dev.eduarddragu.anotherhabittracker.theme

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith

/**
 * Motion follows eduarddragu.dev: strong ease-outs, nothing that bounces. Interface changes take 200
 * to 420 ms; things arriving take longer (520 ms, 720 ms for a page's main card, about a second for
 * the name on Home) and move short distances (8, 16, 24 or 32dp). Staggers are 60 to 110 ms.
 */
object Motion {
  /** General UI changes, hover-like swaps. */
  val EaseUi = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

  /** Things arriving on screen. */
  val EaseEntrance = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

  /**
   * Full-screen slides: an ease-out with a softer start. The first frames of a screen change are the
   * most expensive ones, so they shouldn't also be the ones that cover a third of the screen.
   */
  val EaseScreen = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)

  /** Leaving: starts slow and speeds up, so what goes away gets out of the way. */
  val EaseExit = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

  /** Symmetric ease for endless loops: soft at both turn-arounds. */
  val EaseLoop = CubicBezierEasing(0.37f, 0f, 0.63f, 1f)

  /** The outgoing half of a fade-through: short, so two states are never on screen together. */
  const val FADE_OUT = 90
  const val SHORT = 200

  /** Items moving to their new place in a list. */
  const val LIST = 250
  const val LONG = 420

  /** Dismissing a sheet: quicker than its entrance. */
  const val EXIT = 220

  /** The commit moment after a log, as offsets from the moment the log sheet is gone. */
  object Commit {
    /** The streak rolls to its new value. */
    const val ROLL = 120

    /** Labels and sublines change. */
    const val SETTLE = 200
  }
}

/** Swap one piece of content for another: out quickly, then in. Never both at once. */
fun fadeThrough(): ContentTransform = fadeIn(tween(Motion.SHORT, delayMillis = Motion.FADE_OUT, easing = Motion.EaseUi)) togetherWith fadeOut(tween(Motion.FADE_OUT))
