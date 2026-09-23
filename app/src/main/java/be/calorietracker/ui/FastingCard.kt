package be.calorietracker.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import be.calorietracker.domain.FastingWindow
import java.time.Duration
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

@Composable
fun FastingCard(window: FastingWindow, onEdit: () -> Unit) {
  var clock by remember { mutableStateOf(ZonedDateTime.now()) }
  LaunchedEffect(window) {
    while (true) {
      clock = ZonedDateTime.now()
      delay(30_000)
    }
  }
  val status = window.status(clock)
  val remaining = Duration.between(clock, status.nextTransition).toMinutes().coerceAtLeast(0)
  val time = DateTimeFormatter.ofPattern("HH:mm")
  Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
      Icon(Icons.Rounded.Schedule, null, tint = MaterialTheme.colorScheme.primary)
      Spacer(Modifier.width(10.dp))
      Text(if (status.canEat) "Eating window" else "Fasting now", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
      TextButton(onClick = onEdit) { Text("Edit") }
    }
    Text("${window.starts}–${window.ends} · ${if (status.canEat) "closes" else "opens"} in ${remaining / 60} h ${remaining % 60} min", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}
