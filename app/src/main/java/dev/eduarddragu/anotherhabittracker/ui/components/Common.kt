package dev.eduarddragu.anotherhabittracker.ui.components

import android.view.accessibility.AccessibilityManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.HorizontalAlignmentLine
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.VerticalAlignmentLine
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.eduarddragu.anotherhabittracker.R
import dev.eduarddragu.anotherhabittracker.theme.Motion
import kotlin.math.max
import kotlin.math.min

/** The heading of a section on every screen: mono capitals in the accent. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) =
  Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = modifier)

/**
 * Every text field: Material's outlined field on the controls' radius (12dp; Material would round it
 * to 4), with a plain text label.
 */
@Composable
fun Field(
  value: String,
  onValueChange: (String) -> Unit,
  label: String,
  modifier: Modifier = Modifier,
  keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
  keyboardActions: KeyboardActions = KeyboardActions.Default,
  singleLine: Boolean = false,
  minLines: Int = 1,
  isError: Boolean = false,
  suffix: (@Composable () -> Unit)? = null,
  supportingText: (@Composable () -> Unit)? = null,
) =
  OutlinedTextField(
    value,
    onValueChange,
    modifier,
    label = { Text(label) },
    suffix = suffix,
    supportingText = supportingText,
    isError = isError,
    keyboardOptions = keyboardOptions,
    keyboardActions = keyboardActions,
    singleLine = singleLine,
    minLines = minLines,
    shape = MaterialTheme.shapes.medium,
  )

/**
 * The card of today's job (a study topic, a meditation practice), same rule as the home cards and the
 * widget: plain while the day is open, warm once it's done. The content color is set explicitly: the
 * plain surface has the same value as surfaceVariant, so Material would pick the muted one.
 */
@Composable
fun DayCard(done: Boolean, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
  val colors = MaterialTheme.colorScheme
  val surface by animateColorAsState(if (done) colors.primaryContainer else colors.surfaceContainerLow, tween(Motion.LONG, easing = Motion.EaseUi), label = "day card")
  Card(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = surface, contentColor = colors.onSurface)) {
    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
  }
}

/** "01 ..." rows (a topic's questions, a practice's steps), arriving one at a time from [startDelay]. */
@Composable
fun NumberedSteps(items: List<String>, startDelay: Long) {
  val arrival = rememberArrival(items.size, delayOf = { startDelay + Motion.STAGGER * it.toLong() }, durationOf = { Motion.ENTRANCE })
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    items.forEachIndexed { index, item ->
      Row(Modifier.rise(arrival[index], 8.dp)) {
        Text("%02d".format(index + 1), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(32.dp).padding(top = 3.dp))
        Text(item, style = MaterialTheme.typography.bodyLarge)
      }
    }
  }
}

/** Log, or once the day is done, "Log again" as the quieter outlined button. */
@Composable
fun LogButton(done: Boolean, onClick: () -> Unit) {
  if (done) OutlinedButton(shape = MaterialTheme.shapes.medium, onClick = onClick, border = cardOutline()) { Text("Log again") } else Button(shape = MaterialTheme.shapes.medium, onClick = onClick) { Text("Log") }
}

/**
 * The outline for outlined buttons: one step stronger than Material's default, which nearly vanishes
 * on a tonal card.
 */
@Composable
fun cardOutline(): BorderStroke = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)

/** A chevron that turns to show whether a section is open. */
@Composable
fun Chevron(open: Boolean, modifier: Modifier = Modifier, size: Dp = 20.dp) {
  val rotation by animateFloatAsState(if (open) 180f else 0f, tween(Motion.SHORT, easing = Motion.EaseUi), label = "chevron")
  Icon(painterResource(R.drawable.ic_chevron_down), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier.size(size).graphicsLayer { rotationZ = rotation })
}

/**
 * A text action at the edge of a row or on its own line: accent text with no button box, so it sits
 * exactly on the gutter (a TextButton centres short labels in a 58dp minimum width) and doesn't make
 * its line taller than the text beside it. [vertical] padding is part of its layout; the touch area
 * grows to 48dp either way without moving anything (see [touchTarget]). [color] is the accent; a way
 * out that shouldn't invite a tap can be muted.
 */
@Composable
fun TextAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, vertical: Dp = 12.dp, color: Color = MaterialTheme.colorScheme.primary) {
  Text(
    text,
    style = MaterialTheme.typography.labelLarge,
    color = if (enabled) color else color.copy(alpha = 0.38f),
    modifier = modifier.touchTarget(Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick)).padding(vertical = vertical),
  )
}

/**
 * Applies [clickable] (a click, a selection) on an area at least [size] each way, while the layout
 * keeps the content's own size: baselines, flush edges and line heights stay where they were, and the
 * extra area overlaps the neighbours. Unlike Compose's own touch slop, the grown area wins over a
 * clickable parent (a tap just beside "Keep going today" doesn't open the row). Intrinsic sizes are the
 * content's, so rows measured by intrinsics don't grow either.
 */
fun Modifier.touchTarget(clickable: Modifier, size: Dp = 48.dp): Modifier = this then TouchArea(size, grow = false) then clickable then TouchArea(size, grow = true)

// Where the content sits inside the grown area: the inner half reports it, the outer half reads it
// (alignment lines travel with each measurement, so nothing is shared between passes).
private val ContentLeft = VerticalAlignmentLine(::min)
private val ContentRight = VerticalAlignmentLine(::max)
private val ContentTop = HorizontalAlignmentLine(::min)
private val ContentBottom = HorizontalAlignmentLine(::max)

private data class TouchArea(val size: Dp, val grow: Boolean) : ModifierNodeElement<TouchAreaNode>() {
  override fun create() = TouchAreaNode(size, grow)

  override fun update(node: TouchAreaNode) {
    node.size = size
    node.grow = grow
  }
}

/**
 * [grow]: the inner half, which centres the content in at least [size] each way. Otherwise the outer
 * half, which lays out only the content's bounds and lets the rest overlap.
 */
private class TouchAreaNode(var size: Dp, var grow: Boolean) : Modifier.Node(), LayoutModifierNode {
  override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
    val target = size.roundToPx()
    // Room for the inner half to grow past the outer half's constraints.
    val slack = 2 * target
    if (grow) {
      // The outer half's own constraints again: the content measures as if nothing were around it.
      val own =
        Constraints(
          minWidth = constraints.minWidth,
          maxWidth = if (constraints.hasBoundedWidth) constraints.maxWidth - slack else Constraints.Infinity,
          minHeight = constraints.minHeight,
          maxHeight = if (constraints.hasBoundedHeight) constraints.maxHeight - slack else Constraints.Infinity,
        )
      val content = measurable.measure(own)
      val width = maxOf(content.width, target)
      val height = maxOf(content.height, target)
      val x = (width - content.width) / 2
      val y = (height - content.height) / 2
      val lines = mapOf(ContentLeft to x, ContentRight to x + content.width, ContentTop to y, ContentBottom to y + content.height)
      return layout(width, height, lines) { content.place(x, y) }
    }
    val loose =
      constraints.copy(
        maxWidth = if (constraints.hasBoundedWidth) constraints.maxWidth + slack else Constraints.Infinity,
        maxHeight = if (constraints.hasBoundedHeight) constraints.maxHeight + slack else Constraints.Infinity,
      )
    val grown = measurable.measure(loose)
    val left = grown[ContentLeft]
    val top = grown[ContentTop]
    if (left == AlignmentLine.Unspecified || top == AlignmentLine.Unspecified) return layout(grown.width, grown.height) { grown.place(0, 0) }
    return layout(grown[ContentRight] - left, grown[ContentBottom] - top) { grown.place(-left, -top) }
  }

  override fun IntrinsicMeasureScope.minIntrinsicWidth(measurable: IntrinsicMeasurable, height: Int) = measurable.minIntrinsicWidth(height)

  override fun IntrinsicMeasureScope.maxIntrinsicWidth(measurable: IntrinsicMeasurable, height: Int) = measurable.maxIntrinsicWidth(height)

  override fun IntrinsicMeasureScope.minIntrinsicHeight(measurable: IntrinsicMeasurable, width: Int) = measurable.minIntrinsicHeight(width)

  override fun IntrinsicMeasureScope.maxIntrinsicHeight(measurable: IntrinsicMeasurable, width: Int) = measurable.maxIntrinsicHeight(width)
}

/**
 * A card's mono label: muted parts joined by " · ", with only [state] (done, new, a review, now) in
 * the accent. The parts are given in place, so a habit named like a state word stays muted.
 */
@Composable
fun CardLabel(modifier: Modifier = Modifier, lead: List<String> = emptyList(), state: String? = null, trail: List<String> = emptyList(), maxLines: Int = Int.MAX_VALUE) {
  val colors = MaterialTheme.colorScheme
  Text(
    buildAnnotatedString {
      var first = true
      fun separate() {
        if (!first) append(" · ")
        first = false
      }
      lead.forEach {
        separate()
        append(it)
      }
      if (state != null) {
        separate()
        withStyle(SpanStyle(color = colors.primary)) { append(state) }
      }
      trail.forEach {
        separate()
        append(it)
      }
    },
    style = MaterialTheme.typography.labelMedium,
    color = colors.onSurfaceVariant,
    maxLines = maxLines,
    overflow = TextOverflow.Ellipsis,
    modifier = modifier,
  )
}

/**
 * Whether a screen reader is exploring by touch (TalkBack): what would hide itself after a few
 * seconds has to stay until it's used. Follows the setting while the screen is up.
 */
@Composable
fun rememberTouchExploration(): Boolean {
  val context = LocalContext.current
  val manager = remember { context.getSystemService(AccessibilityManager::class.java) }
  var enabled by remember { mutableStateOf(manager?.isTouchExplorationEnabled == true) }
  DisposableEffect(manager) {
    val listener = AccessibilityManager.TouchExplorationStateChangeListener { enabled = it }
    manager?.addTouchExplorationStateChangeListener(listener)
    onDispose { manager?.removeTouchExplorationStateChangeListener(listener) }
  }
  return enabled
}
