package dev.eduarddragu.anotherhabittracker.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.eduarddragu.anotherhabittracker.theme.NumeralsSmall

/** A number (DM Sans) with a small mono caption, e.g. "12/30" over "LAST 30 DAYS". */
@Composable
fun StatBlock(value: String, caption: String, modifier: Modifier = Modifier) {
  Column(modifier) {
    // Wraps rather than cutting at a large font size: "1h 45m" in a third of the row may need two lines.
    Text(value, style = NumeralsSmall)
    Text(caption.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
  }
}

/** A session's length in a line of text: "45 min" under an hour, then as the stats write it, "1h 5m". */
fun formatMinutes(minutes: Int): String = if (minutes >= 60) formatDuration(minutes) else "$minutes min"

/** A duration as a stat reads it, with its units on both parts: "1h 45m", "2h", "45m". */
fun formatDuration(minutes: Int): String {
  val hours = minutes / 60
  val rest = minutes % 60
  return when {
    hours == 0 -> "${rest}m"
    rest == 0 -> "${hours}h"
    else -> "${hours}h ${rest}m"
  }
}
