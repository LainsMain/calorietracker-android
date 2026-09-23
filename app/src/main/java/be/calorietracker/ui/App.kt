package be.calorietracker.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import be.calorietracker.data.*
import be.calorietracker.domain.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackerApp(vm: TrackerViewModel, quickLog: Boolean = false) {
  val state by vm.state.collectAsStateWithLifecycle()
  val ready by vm.ready.collectAsStateWithLifecycle()
  val error by vm.error.collectAsStateWithLifecycle()
  var tab by rememberSaveable { mutableIntStateOf(0) }
  var settings by remember { mutableStateOf(false) }
  var settingsPage by remember { mutableStateOf("Home") }
  var planEditor by remember { mutableStateOf(false) }
  var planSeed by remember { mutableStateOf<Plan?>(null) }
  var weeklyPeriod by remember { mutableStateOf<ClosedRange<LocalDate>?>(null) }
  var foodSearch by remember { mutableStateOf(quickLog) }
  var date by rememberSaveable { mutableStateOf(today()) }
  var chosen by remember { mutableStateOf<Food?>(null) }
  var editing by remember { mutableStateOf<Entry?>(null) }
  val snackbar = remember { SnackbarHostState() }
  LaunchedEffect(error) {
    error?.let {
      snackbar.showSnackbar(it)
      vm.error.value = null
    }
  }
  Scaffold(
    snackbarHost = { SnackbarHost(snackbar) },
    bottomBar = {
      if (ready && state.profile != null)
        NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
          listOf(
              "Today" to Icons.Rounded.Home,
              "Diary" to Icons.Rounded.MenuBook,
              "Recipes" to Icons.Rounded.RestaurantMenu,
              "Progress" to Icons.Rounded.Insights,
              "Coach" to Icons.Rounded.AutoAwesome,
            )
            .forEachIndexed { i, (name, icon) ->
              NavigationBarItem(
                selected = tab == i,
                onClick = { tab = i },
                icon = { Icon(icon, name) },
                label = { Text(name) },
              )
            }
        }
    },
  ) { padding ->
    if (!ready)
      Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
      }
    else if (state.profile == null) Onboarding(vm, state, Modifier.padding(padding))
    else
      Column(Modifier.fillMaxSize().padding(padding)) {
        Row(
          Modifier.fillMaxWidth().padding(horizontal = 20.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(Icons.Rounded.Spa, null, tint = MaterialTheme.colorScheme.primary)
          Text(
            "  CalorieTracker",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f),
          )
          IconButton(onClick = { planSeed = state.plan(); planEditor = true }) { Icon(Icons.Rounded.Tune, "Review plan") }
          IconButton(onClick = { settingsPage = "Home"; settings = true }) { Icon(Icons.Rounded.Settings, "Settings") }
        }
        when (tab) {
          0 ->
            TodayScreen(
              vm,
              state,
              {
                foodSearch = true
                date = today()
              },
              { planSeed = state.plan(); planEditor = true },
              { ml -> vm.run { vm.store.update { it.copy(water = it.water + Water(ml = ml)) } } },
              { tab = 4 },
              { weeklyPeriod = it },
              { draft -> planSeed = draft; planEditor = true },
              { settingsPage = "Fasting"; settings = true },
            )
          1 -> DiaryScreen(vm, state, date, { date = it }, { editing = it }, { foodSearch = true })
          2 -> RecipesScreen(vm, state)
          3 -> ProgressScreen(vm, state)
          4 -> CoachScreen(vm, state)
        }
      }
  }
  if (settings) SettingsScreen(vm, initialPage = settingsPage) { settings = false }
  if (planEditor)
    PlanEditor(
      planSeed ?: state.plan(),
      profile = state.profile,
      onDismiss = { planEditor = false },
      onSave = { p ->
        vm.run { vm.store.update { it.copy(plans = it.plans + p) } }
        planEditor = false
      },
    )
  weeklyPeriod?.let { period ->
    WeeklyCheckInDialog(vm, state, period) { weeklyPeriod = null }
  }
  if (foodSearch)
    FoodSearch(
      vm,
      onDismiss = { foodSearch = false },
      onChoose = {
        chosen = it
        foodSearch = false
      },
    )
  chosen?.let { food ->
    PortionEditor(
      food,
      date,
      state.meals,
      onDismiss = { chosen = null },
      onSave = { e ->
        vm.run {
          vm.store.cache(listOf(food))
          vm.store.log(e)
        }
        chosen = null
      },
    )
  }
  editing?.let { entry ->
    PortionEditor(
      entry.food,
      entry.date,
      state.meals,
      entry,
      onDismiss = { editing = null },
      onSave = { e ->
        vm.run {
          vm.store.update { s -> s.copy(entries = s.entries.filterNot { it.id == e.id } + e) }
        }
        editing = null
      },
      onDelete = {
        vm.run {
          vm.store.update { s -> s.copy(entries = s.entries.filterNot { it.id == entry.id }) }
          val result = snackbar.showSnackbar("Entry deleted", "Undo")
          if (result == SnackbarResult.ActionPerformed)
            vm.store.update { it.copy(entries = it.entries + entry) }
        }
        editing = null
      },
    )
  }
  val release by vm.release.collectAsStateWithLifecycle()
  release?.let { UpdateDialog(vm, it) { vm.release.value = null } }
}

@Composable
fun TodayScreen(
  vm: TrackerViewModel,
  s: AppState,
  log: () -> Unit,
  edit: () -> Unit,
  water: (Int) -> Unit,
  coach: () -> Unit,
  startWeekly: (ClosedRange<LocalDate>) -> Unit,
  editWeekly: (Plan) -> Unit,
  editFasting: () -> Unit,
) {
  val totals = s.totals()
  val plan = s.plan()
  val date = LocalDate.now()
  val activity = s.health.firstOrNull { it.date == today() }
  LazyColumn(
    Modifier.fillMaxSize().testTag("today-list"),
    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    item {
      PageTitle(
        date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")),
        "Today",
      )
    }
    if (s.fasting.enabled) item { FastingCard(s.fasting, editFasting) }
    item {
      Button(onClick = log, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Log food" }) {
        Icon(Icons.Rounded.Add, null)
        Text("  Log food")
      }
    }
    item { WeeklyReviewCard(vm, s, startWeekly, editWeekly, coach) }
    item {
      Panel(tint = MaterialTheme.colorScheme.primaryContainer) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          Text("YOUR DAILY ENERGY", style = MaterialTheme.typography.labelMedium)
          Icon(Icons.Rounded.Bolt, null)
        }
        Row(verticalAlignment = Alignment.Bottom) {
          Text(energyValue(totals.kcal), style = MaterialTheme.typography.displayLarge)
          Text(
            " ${LocalEnergyUnit.current}",
            Modifier.padding(bottom = 9.dp),
            style = MaterialTheme.typography.titleMedium,
          )
        }
        LinearProgressIndicator(
          progress = { ((totals.kcal ?: 0.0) / (plan?.kcal ?: 1.0)).toFloat().coerceIn(0f, 1f) },
          modifier = Modifier.fillMaxWidth().height(10.dp),
          color = MaterialTheme.colorScheme.primary,
          trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .12f),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          Text("${energy(plan?.kcal)} target")
          Text(
            "${energyValue(plan?.kcal?.let { target -> totals.kcal?.let { target-it } })} left",
            fontWeight = FontWeight.SemiBold,
          )
        }
        if (totals.kcal == null)
          Text("Some logged foods have unknown energy.", style = MaterialTheme.typography.bodySmall)
      }
    }
    item {
      Panel {
        MacroLine("Protein", totals.protein, plan?.protein, MaterialTheme.colorScheme.primary)
        MacroLine("Carbs", totals.carbs, plan?.carbs, Color(0xFFC97842))
        MacroLine("Fat", totals.fat, plan?.fat, Color(0xFF9A9B23))
      }
    }
    item { Section("Activity") }
    item {
      Panel(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Rounded.DirectionsRun, null, tint = MaterialTheme.colorScheme.primary)
          Text("  Today’s activity", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
          Text(activity?.let { "Synced" } ?: "No data", style = MaterialTheme.typography.labelMedium)
        }
        if (activity == null)
          Text("Connect or refresh Health Connect in Settings to see steps and workouts here.")
        else {
          Text("${activity.steps ?: 0} steps · ${(activity.distanceMetres?.div(1000)).fmt(1)} km · ${energy(activity.activeKcal)} active")
          activity.workouts.forEach { workout -> WorkoutSummary(workout) }
          Text("Activity helps interpret your weekly trend; it is not added to today’s food budget.", style = MaterialTheme.typography.bodySmall)
        }
      }
    }
    item {
      Panel(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Rounded.WaterDrop, null, tint = MaterialTheme.colorScheme.primary)
          Text("  Water", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
          Text("${s.water.filter{it.date==today()}.sumOf{it.ml}} / ${s.waterGoalMl} ml")
        }
        LinearProgressIndicator(progress = { (s.water.filter { it.date == today() }.sumOf { it.ml }.toFloat() / s.waterGoalMl).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          s.waterQuickAmountsMl.forEach { amount ->
            FilledTonalButton(onClick = { water(amount) }) { Text("+ $amount ml") }
          }
        }
      }
    }
    item { Section("On the menu", "Add", log) }
    if (s.entries.none { it.date == today() })
      item {
        EmptyState(
          "Your next meal starts here",
          "Search a food, scan a barcode, or choose a recipe. Recent foods will be one tap away.",
          "Find a food",
          log,
        )
      }
    else {
      val visibleMeals =
        s.meals +
          s.entries
            .filter {
              it.date == today() && s.meals.none { meal -> meal.equals(it.meal, true) }
            }
            .map { it.meal }
            .distinct()
      items(visibleMeals) { meal ->
        val configured = meal in s.meals
        val entries =
          s.entries.filter {
            it.date == today() &&
              if (configured) it.meal.equals(meal, true) else it.meal == meal
          }
        if (entries.isNotEmpty())
          Panel {
            Row(Modifier.fillMaxWidth()) {
              Text(meal.ifBlank { "Other" }, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
              Text(energy(Nutrients.total(entries.map { it.nutrients }).kcal))
            }
            Text(
              entries.joinToString { it.food.name },
              maxLines = 2,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
      }
    }
    item {
      Panel(tint = MaterialTheme.colorScheme.secondaryContainer) {
        Icon(Icons.Rounded.AutoAwesome, null)
        Text("A coach who knows your story", style = MaterialTheme.typography.titleLarge)
        Text("Reflect on your meals, understand your trends, and shape a plan that fits.")
        TextButton(onClick = coach) {
          Text("Check in with your coach")
          Icon(Icons.Rounded.ArrowForward, null)
        }
      }
    }
    item { TextButton(onClick = edit) { Text("Review my plan") } }
  }
}

@Composable
fun WorkoutSummary(workout: Workout) {
  val start = java.time.Instant.parse(workout.start).atZone(java.time.ZoneId.systemDefault())
  val minutes = java.time.Duration.between(java.time.Instant.parse(workout.start), java.time.Instant.parse(workout.end)).toMinutes()
  Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.large) {
    Column(Modifier.fillMaxWidth().padding(12.dp)) {
      Text(workout.title.ifBlank { workout.typeName }, style = MaterialTheme.typography.titleMedium)
      Text("${start.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))} · $minutes min" + (workout.distanceMetres?.let { " · ${(it / 1000).fmt(1)} km" } ?: "") + (workout.activeKcal?.let { " · ${it.fmt()} kcal" } ?: ""))
      Text(workout.sourceLabel, style = MaterialTheme.typography.labelSmall)
    }
  }
}

@Composable
private fun MacroLine(
  label: String,
  value: Double?,
  target: Double?,
  colour: Color,
) {
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Row(Modifier.fillMaxWidth()) {
      Text(label, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
      Text("${value.fmt()} / ${target.fmt()} g", style = MaterialTheme.typography.labelLarge)
    }
    LinearProgressIndicator(progress = { ((value ?: 0.0) / (target ?: 1.0)).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth(), color = colour, trackColor = MaterialTheme.colorScheme.surfaceContainerHigh)
  }
}

@Composable
fun DiaryScreen(
  vm: TrackerViewModel,
  s: AppState,
  date: String,
  onDate: (String) -> Unit,
  onEntry: (Entry) -> Unit,
  onLog: () -> Unit,
) {
  var calendar by remember { mutableStateOf(false) }
  var copyMeal by remember { mutableStateOf<String?>(null) }
  var templateMeal by remember { mutableStateOf<String?>(null) }
  var templateName by remember { mutableStateOf("") }
  var templateToLog by remember { mutableStateOf<MealTemplate?>(null) }
  var openedEmpty by remember { mutableStateOf<Set<String>>(emptySet()) }
  var full by remember { mutableStateOf(false) }
  LazyColumn(
    modifier = Modifier.testTag("diary-list"),
    contentPadding = PaddingValues(20.dp, 0.dp, 20.dp, 24.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item { PageTitle("Your food story", "Food diary") }
    item {
      Button(onClick = onLog, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Log food" }) {
        Icon(Icons.Rounded.Add, null)
        Text("  Log food for this day")
      }
    }
    item {
      Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        (-3L..3L).forEach { offset ->
          val day = LocalDate.parse(date).plusDays(offset)
          FilterChip(
            selected = offset == 0L,
            onClick = { onDate(day.toString()) },
            label = { Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Text(day.format(DateTimeFormatter.ofPattern("EEE")))
              Text(day.dayOfMonth.toString())
            } },
          )
        }
      }
      TextButton(onClick = { calendar = true }) { Text("Choose date · ${LocalDate.parse(date).format(DateTimeFormatter.ofPattern("d MMM yyyy"))}") }
    }
    item {
      Panel {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          Text(energy(s.totals(date).kcal), style = MaterialTheme.typography.headlineMedium)
          TextButton(onClick = { full = !full }) { Text(if (full) "Less" else "All nutrients") }
        }
        Text("Daily target ${energy(s.plan(date)?.kcal)}")
        if (full) NutrientGrid(s.totals(date))
      }
    }
    if (s.mealTemplates.isNotEmpty()) item {
      Section("Saved meals")
      Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        s.mealTemplates.forEach { template ->
          FilterChip(selected = false, onClick = { templateToLog = template }, label = { Text(template.name) })
        }
      }
    }
    val visibleMeals =
      s.meals +
        s.entries
          .filter { it.date == date && s.meals.none { meal -> meal.equals(it.meal, true) } }
          .map { it.meal }
          .distinct()
    visibleMeals.forEach { meal ->
      val configured = meal in s.meals
      val entries =
        s.entries.filter {
          it.date == date && if (configured) it.meal.equals(meal, true) else it.meal == meal
        }
      item {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
          Text(meal.ifBlank { "Other" }, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
          if (entries.isNotEmpty()) {
            TextButton(onClick = { templateName = meal; templateMeal = meal }) { Text("Save meal") }
            TextButton(onClick = { copyMeal = meal }) { Text("Copy") }
          } else TextButton(onClick = { openedEmpty = if (meal in openedEmpty) openedEmpty - meal else openedEmpty + meal }) {
            Text(if (meal in openedEmpty) "Hide" else "Show")
          }
        }
      }
      if (entries.isEmpty()) {
        if (meal in openedEmpty) item {
          Text(
            "Nothing logged yet",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp),
          )
        }
      } else
        items(entries, key = { it.id }) { e ->
          Surface(
            onClick = { onEntry(e) },
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
          ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
              Column(Modifier.weight(1f)) {
                Text(e.food.name, style = MaterialTheme.typography.titleMedium)
                Text(
                  "${e.amount.fmt(1)} ${e.unit} · ${e.note.ifBlank{e.food.source}}",
                  style = MaterialTheme.typography.bodySmall,
                )
              }
              Text(energy(e.nutrients.kcal))
            }
          }
        }
    }
  }
  templateMeal?.let { meal ->
    Modal("Save this meal", { templateMeal = null }) {
      Text("${s.entries.count { it.date == date && it.meal.equals(meal, true) }} foods will be saved with their logged portions.")
      Field("Template name", templateName, { templateName = it })
      Button(onClick = {
        vm.run { vm.store.saveMealTemplate(templateName.trim(), date, meal) }
        templateMeal = null
      }, enabled = templateName.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Save meal template") }
    }
  }
  templateToLog?.let { template ->
    var chosenMeal by remember(template.id) { mutableStateOf(template.meal) }
    Modal("Log ${template.name}", { templateToLog = null }) {
      Text("Review what will be added to ${LocalDate.parse(date).format(DateTimeFormatter.ofPattern("d MMM"))}.")
      Choice("Meal", s.meals, chosenMeal, { chosenMeal = it })
      template.items.forEach { item ->
        Text("${item.food.name} · ${item.amount.fmt(1)} ${item.unit} · ${energy(item.food.portion(item.amount, item.unit).kcal)}")
      }
      val total = Nutrients.total(template.items.map { it.food.portion(it.amount, it.unit) })
      Text("Total ${energy(total.kcal)}", style = MaterialTheme.typography.titleMedium)
      Button(onClick = {
        vm.run { vm.store.logMealTemplate(template.id, date, chosenMeal) }
        templateToLog = null
      }, modifier = Modifier.fillMaxWidth()) { Text("Log meal") }
      TextButton(onClick = {
        vm.run { vm.store.update { it.copy(mealTemplates = it.mealTemplates.filterNot { t -> t.id == template.id }) } }
        templateToLog = null
      }) { Text("Delete template", color = MaterialTheme.colorScheme.error) }
    }
  }
  copyMeal?.let { meal ->
    DateDialog(today(), { copyMeal = null }) { target ->
      vm.run {
        vm.store.update { st ->
          st.copy(
            entries =
              st.entries +
                st.entries
                  .filter { it.date == date && it.meal == meal }
                  .map {
                    it.copy(id = newId(), date = target, created = now(), zoneOffset = offset())
                  }
          )
        }
      }
      copyMeal = null
    }
  }
  if (calendar)
    DateDialog(date, { calendar = false }) {
      onDate(it)
      calendar = false
    }
}

@Composable
fun DateDialog(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
  var value by remember { mutableStateOf(initial) }
  var error by remember { mutableStateOf<String?>(null) }
  Modal("Choose date", onDismiss) {
    Field("Date (YYYY-MM-DD)", value, { value = it })
    ErrorText(error)
    Button(
      onClick = {
        try {
          LocalDate.parse(value)
          onSave(value)
        } catch (_: Exception) {
          error = "Enter a valid date such as 2026-09-17."
        }
      },
      modifier = Modifier.fillMaxWidth(),
    ) {
      Text("Use date")
    }
  }
}
