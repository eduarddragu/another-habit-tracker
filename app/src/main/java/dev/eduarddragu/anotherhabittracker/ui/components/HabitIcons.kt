package dev.eduarddragu.anotherhabittracker.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.eduarddragu.anotherhabittracker.R
import dev.eduarddragu.anotherhabittracker.domain.HabitIcon

@get:DrawableRes
val HabitIcon.drawable: Int
  get() =
    when (this) {
      HabitIcon.BOOK -> R.drawable.ic_habit_book
      HabitIcon.LOTUS -> R.drawable.ic_habit_lotus
      HabitIcon.PEN -> R.drawable.ic_habit_pen
      HabitIcon.SPARK -> R.drawable.ic_habit_spark
      HabitIcon.DROP -> R.drawable.ic_habit_drop
      HabitIcon.MOON -> R.drawable.ic_habit_moon
      HabitIcon.DUMBBELL -> R.drawable.ic_habit_dumbbell
      HabitIcon.BOOKMARK -> R.drawable.ic_habit_bookmark
      HabitIcon.HOUSE -> R.drawable.ic_habit_house
    }

/** Decorative: the habit's name is always next to it, so it has no content description. */
@Composable
fun HabitIconImage(icon: HabitIcon, modifier: Modifier = Modifier, size: Dp = 24.dp, tint: Color = LocalContentColor.current) {
  Icon(painterResource(icon.drawable), contentDescription = null, tint = tint, modifier = modifier.size(size))
}
