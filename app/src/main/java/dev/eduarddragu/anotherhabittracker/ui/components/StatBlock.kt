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
    Text(value, style = NumeralsSmall, maxLines = 1)
    Text(caption.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
  }
}

fun formatMinutes(minutes: Int): String = if (minutes >= 60) "${minutes / 60}h ${"%02d".format(minutes % 60)}" else "$minutes min"

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
