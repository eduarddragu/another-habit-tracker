package dev.eduarddragu.anotherhabittracker.ui.components

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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.eduarddragu.anotherhabittracker.R
import dev.eduarddragu.anotherhabittracker.theme.Motion

/** The heading of a section on every screen: mono capitals in the accent. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) =
  Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = modifier)

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
  val arrival = rememberArrival(items.size, delayOf = { startDelay + 90L * it }, durationOf = { 480 })
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
  if (done) OutlinedButton(onClick = onClick, border = cardOutline()) { Text("Log again") } else Button(onClick = onClick) { Text("Log") }
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
 * its line taller than the text beside it. [vertical] padding keeps a usable touch height. [color] is
 * the accent; a way out that shouldn't invite a tap can be muted.
 */
@Composable
fun TextAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, vertical: Dp = 12.dp, color: Color = MaterialTheme.colorScheme.primary) {
  Text(
    text,
    style = MaterialTheme.typography.labelLarge,
    color = if (enabled) color else color.copy(alpha = 0.38f),
    modifier = modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(vertical = vertical),
  )
}
