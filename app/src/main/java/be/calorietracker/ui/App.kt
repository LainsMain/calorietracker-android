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
    floatingActionButton = {
      if (ready && state.profile != null && tab in listOf(0, 1))
        ExtendedFloatingActionButton(
          modifier = Modifier.semantics { contentDescription = "Log food" },
          onClick = {
            if (tab == 0) date = today()
            foodSearch = true
          },
          icon = { Icon(Icons.Rounded.Add, null) },
          text = { Text("Log food") },
          containerColor = MaterialTheme.colorScheme.primaryContainer,
        )
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
          IconButton(onClick = { settings = true }) { Icon(Icons.Rounded.Settings, "Settings") }
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
              { vm.run { vm.store.update { it.copy(water = it.water + Water()) } } },
              { tab = 4 },
              { weeklyPeriod = it },
              { draft -> planSeed = draft; planEditor = true },
            )
          1 -> DiaryScreen(vm, state, date, { date = it }, { editing = it })
          2 -> RecipesScreen(vm, state)
          3 -> ProgressScreen(vm, state)
          4 -> CoachScreen(vm, state)
        }
      }
  }
  if (settings) SettingsScreen(vm) { settings = false }
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
  water: () -> Unit,
  coach: () -> Unit,
  startWeekly: (ClosedRange<LocalDate>) -> Unit,
  editWeekly: (Plan) -> Unit,
) {
  val totals = s.totals()
  val plan = s.plan()
  val date = LocalDate.now()
  val activity = s.health.firstOrNull { it.date == today() }
  LazyColumn(
    Modifier.fillMaxSize().testTag("today-list"),
    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 100.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    item {
      PageTitle(
        date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")),
        "A little better,\nevery day.",
      )
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
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        MacroCard("Protein", totals.protein, plan?.protein, Color(0xFFB9D6D0), Modifier.weight(1f))
        MacroCard("Carbs", totals.carbs, plan?.carbs, Color(0xFFE2D5A5), Modifier.weight(1f))
        MacroCard("Fat", totals.fat, plan?.fat, Color(0xFFE7C9B8), Modifier.weight(1f))
      }
    }
    item { Section("Make it a good day") }
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
          Text("${s.water.filter{it.date==today()}.sumOf{it.ml}} ml")
          TextButton(onClick = water) { Text("+ 250 ml") }
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
private fun MacroCard(
  label: String,
  value: Double?,
  target: Double?,
  colour: Color,
  modifier: Modifier,
) {
  Surface(
    modifier,
    shape = RoundedCornerShape(24.dp),
    color = MaterialTheme.colorScheme.surfaceContainer,
  ) {
    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
      Box(Modifier.size(10.dp).background(colour, RoundedCornerShape(10.dp)))
      Text(label, style = MaterialTheme.typography.labelLarge)
      Text("${value.fmt()} g", style = MaterialTheme.typography.titleLarge)
      Text(
        "of ${target.fmt()} g",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
fun DiaryScreen(
  vm: TrackerViewModel,
  s: AppState,
  date: String,
  onDate: (String) -> Unit,
  onEntry: (Entry) -> Unit,
) {
  var calendar by remember { mutableStateOf(false) }
  var copyMeal by remember { mutableStateOf<String?>(null) }
  var full by remember { mutableStateOf(false) }
  LazyColumn(
    modifier = Modifier.testTag("diary-list"),
    contentPadding = PaddingValues(20.dp, 0.dp, 20.dp, 100.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item { PageTitle("Your food story", "Food diary") }
    item {
      Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onDate(LocalDate.parse(date).minusDays(1).toString()) }) {
          Icon(Icons.Rounded.ChevronLeft, "Previous day")
        }
        TextButton(onClick = { calendar = true }, modifier = Modifier.weight(1f)) {
          Text(LocalDate.parse(date).format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy")))
        }
        IconButton(onClick = { onDate(LocalDate.parse(date).plusDays(1).toString()) }) {
          Icon(Icons.Rounded.ChevronRight, "Next day")
        }
      }
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
      item { Section(meal.ifBlank { "Other" }, if (entries.isNotEmpty()) "Copy" else null) { copyMeal = meal } }
      if (entries.isEmpty())
        item {
          Text(
            "Nothing logged yet",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp),
          )
        }
      else
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
