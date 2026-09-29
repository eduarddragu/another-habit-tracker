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
