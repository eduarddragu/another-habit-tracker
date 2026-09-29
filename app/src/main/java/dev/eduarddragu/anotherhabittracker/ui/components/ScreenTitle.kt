package dev.eduarddragu.anotherhabittracker.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.eduarddragu.anotherhabittracker.domain.HabitIcon

/** The one title pattern of every screen: a mono accent label (with the habit's icon, if any), then the title in the display serif. */
@Composable
fun ScreenTitle(label: String, title: String, modifier: Modifier = Modifier, icon: HabitIcon? = null) {
  Column(modifier) {
    // Same height with or without an icon, so titles line up from screen to screen.
    Row(Modifier.heightIn(min = 18.dp), verticalAlignment = Alignment.CenterVertically) {
      if (icon != null) {
        HabitIconImage(icon, size = 18.dp, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
      }
      Text(label.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
    }
    Spacer(Modifier.height(8.dp))
    Text(title, style = MaterialTheme.typography.displaySmall)
  }
}
