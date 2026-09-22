package be.calorietracker.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import be.calorietracker.domain.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun WeeklyReviewCard(
  vm: TrackerViewModel,
  s: AppState,
  onStart: (ClosedRange<LocalDate>) -> Unit,
  onEdit: (Plan) -> Unit,
  onCoach: () -> Unit,
) {
  val due = WeeklyEngine.due(s)
  if (due != null) {
    Panel(tint = MaterialTheme.colorScheme.tertiaryContainer) {
      Icon(Icons.Rounded.CalendarMonth, null)
      Text("Your weekly check-in is ready", style = MaterialTheme.typography.headlineSmall)
      Text("Tell us which days were complete. We’ll compare your diary, weight trend, and activity without automatically changing your plan.")
      Text("${due.start.pretty()} – ${due.endInclusive.pretty()}", style = MaterialTheme.typography.labelLarge)
      Button({ onStart(due) }, Modifier.fillMaxWidth()) { Text("Review last week") }
      Row {
        TextButton({ vm.run { vm.store.snoozeWeeklyCheckIn(due, LocalDate.now().plusDays(1)) } }) { Text("Remind me tomorrow") }
        TextButton({ vm.run { vm.store.skipWeeklyCheckIn(due) } }) { Text("Skip this week") }
      }
    }
  }
  val period = WeeklyEngine.previousPeriod()
  val checkIn = s.weeklyCheckIns.lastOrNull { it.periodStart == period.start.toString() && it.status == "completed" }
  val recommendation = checkIn?.recommendationId?.let { id -> s.weeklyRecommendations.firstOrNull { it.id == id } }
  if (recommendation != null) {
    Panel(tint = MaterialTheme.colorScheme.secondaryContainer) {
      Text("Last week, reviewed", style = MaterialTheme.typography.labelLarge)
      Text(
        if (recommendation.proposedKcal != null && recommendation.status == "pending") "A small adjustment is ready"
        else "Keep building the trend",
        style = MaterialTheme.typography.headlineSmall,
      )
      Text(recommendation.reason)
      Text("${recommendation.confirmedDayCount} confirmed days" + (recommendation.averageIntakeKcal?.let { " · ${it.fmt()} kcal average" } ?: ""))
      recommendation.weightSlopeKgPerWeek?.let {
        Text("Weight trend ${if (it > 0) "+" else ""}${it.fmt(2)} kg/week")
      }
      recommendation.activityChangePercent?.let { Text("Activity ${if (it >= 0) "+" else ""}${it.fmt()}% versus recent weeks") }
      if (recommendation.proposedKcal != null && recommendation.status == "pending") {
        Text("${recommendation.currentKcal.fmt()} → ${recommendation.proposedKcal.fmt()} kcal", style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          Button({ vm.run { vm.store.applyWeeklyRecommendation(recommendation.id) } }) { Text("Apply") }
          TextButton({
            val current = requireNotNull(s.plan())
            val kcal = recommendation.proposedKcal
            onEdit(current.copy(id = newId(), kcal = kcal, carbs = (kcal - current.protein * 4 - current.fat * 9) / 4))
          }) { Text("Edit") }
          TextButton({ vm.run {
            vm.store.update { state ->
              state.copy(weeklyRecommendations = state.weeklyRecommendations.map { if (it.id == recommendation.id) it.copy(status = "dismissed") else it })
            }
          } }) { Text("Keep current") }
        }
      }
      TextButton({
        vm.send(
          "Explain my latest weekly check-in in a personal, practical way. Use the saved deterministic recommendation and do not invent or change its numbers.",
          contextKind = "weekly_check_in",
          contextId = checkIn.id,
        )
        onCoach()
      }) {
        Icon(Icons.Rounded.AutoAwesome, null)
        Text("  Explain with my coach")
      }
    }
  }
}

@Composable
fun WeeklyCheckInDialog(
  vm: TrackerViewModel,
  s: AppState,
  period: ClosedRange<LocalDate>,
  onDismiss: () -> Unit,
) {
  var step by rememberSaveable { mutableIntStateOf(0) }
  val selected = remember { mutableStateListOf<String>() }
  val latestWeight =
    s.measurements
      .filter {
        it.type == "Weight" &&
          it.date in LocalDate.now().minusDays(14).toString()..LocalDate.now().toString()
      }
      .maxByOrNull { it.date }
  var weight by rememberSaveable { mutableStateOf(latestWeight?.value?.fmt(1) ?: "") }
  var useWeight by rememberSaveable { mutableStateOf(latestWeight != null) }
  var note by rememberSaveable { mutableStateOf("") }
  var error by remember { mutableStateOf<String?>(null) }
  val days = (0L..6L).map { period.start.plusDays(it) }
  Modal("Weekly check-in", onDismiss) {
    Text("Step ${step + 1} of 3", style = MaterialTheme.typography.labelLarge)
    LinearProgressIndicator({ (step + 1) / 3f }, Modifier.fillMaxWidth())
    when (step) {
      0 -> {
        Text("Which days were fully tracked?", style = MaterialTheme.typography.headlineSmall)
        Text("Select a day only if food and drinks were logged closely enough to represent the whole day. There is no penalty for leaving a day out.")
        days.forEach { date ->
          val id = date.toString()
          Surface(
            onClick = { if (id in selected) selected.remove(id) else selected.add(id) },
            color = if (id in selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
            shape = MaterialTheme.shapes.large,
          ) {
            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
              Checkbox(id in selected, { if (it) selected.add(id) else selected.remove(id) })
              Column {
                Text(date.format(DateTimeFormatter.ofPattern("EEEE")), style = MaterialTheme.typography.titleMedium)
                val kcal = s.totals(id).kcal
                Text(kcal?.let { "${it.fmt()} kcal logged" } ?: "No complete energy total", style = MaterialTheme.typography.bodySmall)
              }
            }
          }
        }
      }
      1 -> {
        Text("Add a useful weight point", style = MaterialTheme.typography.headlineSmall)
        if (latestWeight != null) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(useWeight, { useWeight = it })
            Text("Use ${latestWeight.value.fmt(1)} kg from ${latestWeight.date} · ${latestWeight.source}")
          }
        }
        if (!useWeight) Field("Current weight (kg, optional)", weight, { weight = it }, true)
        Text("One reading can be noisy. Recommendations use the multi-week direction, not a single change.")
      }
      else -> {
        Text("Anything unusual last week?", style = MaterialTheme.typography.headlineSmall)
        Field("Optional note", note, { note = it })
        Text("Travel, illness, celebrations, poor sleep, or a very different activity week can help explain the trend.")
        Panel {
          Text("${selected.size} complete days selected", style = MaterialTheme.typography.titleMedium)
          Text("The app may recommend keeping your current target while it collects enough stable evidence.")
        }
      }
    }
    ErrorText(error)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      if (step > 0) OutlinedButton({ step-- }, Modifier.weight(1f)) { Text("Back") }
      Button({
        if (step < 2) step++ else {
          try {
            val chosenWeight = if (useWeight) latestWeight?.value else weight.toDoubleOrNull()
            val checkIn =
              WeeklyCheckIn(
                id = "weekly:${period.start}",
                periodStart = period.start.toString(),
                periodEnd = period.endInclusive.toString(),
                confirmedDates = selected.distinct().sorted(),
                weightMeasurementId = if (useWeight) latestWeight?.id else null,
                weightKg = chosenWeight,
                note = note.trim(),
              )
            vm.run { vm.store.saveWeeklyCheckIn(checkIn) }
            onDismiss()
          } catch (e: Exception) { error = e.message ?: "Check the weight value." }
        }
      }, Modifier.weight(1f)) { Text(if (step < 2) "Continue" else "Finish review") }
    }
  }
}

private fun LocalDate.pretty() = format(DateTimeFormatter.ofPattern("d MMM"))
