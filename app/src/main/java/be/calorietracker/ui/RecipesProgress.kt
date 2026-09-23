package be.calorietracker.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.unit.dp
import be.calorietracker.domain.*
import java.time.LocalDate

@Composable
fun RecipesScreen(vm: TrackerViewModel, s: AppState) {
  var editor by remember { mutableStateOf(false) }
  var selected by remember { mutableStateOf<Recipe?>(null) }
  var portion by remember { mutableStateOf<Recipe?>(null) }
  LazyColumn(
    contentPadding = PaddingValues(20.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    item {
      PageTitle("Your kitchen, simplified", "Made by you") {
        IconButton(
          onClick = {
            selected = null
            editor = true
          }
        ) {
          Icon(Icons.Rounded.Add, "Create recipe")
        }
      }
    }
    if (s.recipes.isEmpty()) item {
      Text("Cook once. Log any portion.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (s.recipes.isEmpty())
      item {
        EmptyState(
          "Keep your favourites close",
          "Create your first recipe from foods you search or scan.",
          "Create recipe",
          {
            selected = null
            editor = true
          },
        )
      }
    items(s.latestRecipes(), key = { it.id }) { r ->
      Panel {
        r.photoId?.let { LocalPhoto(vm, it, Modifier.fillMaxWidth().height(150.dp)) }
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(r.name, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
          IconButton(
            onClick = {
              vm.run {
                vm.store.update {
                  it.copy(
                    recipes =
                      it.recipes.map { v ->
                        if (v.id == r.id) v.copy(favourite = !v.favourite) else v
                      }
                  )
                }
              }
            }
          ) {
            Icon(
              if (r.favourite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
              "Favourite recipe",
            )
          }
        }
        Text("${r.ingredients.size} ingredients · ${r.batchGrams.fmt()} g batch", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
          "${energy(r.portion(100.0).kcal)} / 100 g${if(r.estimatedWeight)" · estimated yield" else ""}"
        )
        Row {
          Button(onClick = { portion = r }) { Text("Log portion") }
          TextButton(
            onClick = {
              selected = r
              editor = true
            }
          ) {
            Text("Edit")
          }
          TextButton(
            onClick = {
              vm.run {
                vm.store.update {
                  it.copy(
                    recipes =
                      it.recipes +
                        r.copy(
                          id = newId(),
                          familyId = newId(),
                          version = 1,
                          name = r.name + " copy",
                        )
                  )
                }
              }
            }
          ) {
            Text("Copy")
          }
        }
      }
    }
  }
  if (editor)
    RecipeEditor(vm, selected, { editor = false }) { r ->
      vm.run { vm.store.update { it.copy(recipes = it.recipes + r) } }
      editor = false
    }
  portion?.let { r ->
    PortionEditor(
      r.asFood(),
      today(),
      s.meals,
      onDismiss = { portion = null },
      onSave = { e ->
        vm.run { vm.store.log(e) }
        portion = null
      },
    )
  }
}

@Composable
fun RecipeEditor(
  vm: TrackerViewModel,
  original: Recipe?,
  onDismiss: () -> Unit,
  onSave: (Recipe) -> Unit,
) {
  var name by remember { mutableStateOf(original?.name ?: "") }
  var grams by remember { mutableStateOf(original?.batchGrams?.toString() ?: "") }
  var servings by remember { mutableStateOf(original?.servings?.toString() ?: "") }
  var estimated by remember { mutableStateOf(original?.estimatedWeight ?: false) }
  var photoId by remember { mutableStateOf(original?.photoId) }
  val ingredients = remember {
    mutableStateListOf<Ingredient>().apply { addAll(original?.ingredients.orEmpty()) }
  }
  var searching by remember { mutableStateOf(false) }
  var ingredient by remember { mutableStateOf<Food?>(null) }
  var amount by remember { mutableStateOf("100") }
  var unit by remember { mutableStateOf("g") }
  var error by remember { mutableStateOf<String?>(null) }
  val photo =
    rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
      if (uri != null) vm.run { photoId = vm.store.importPhoto(uri, "recipe").id }
    }
  LaunchedEffect(ingredients.toList()) {
    if (estimated)
      try {
        grams = ingredients.sumOf { it.massGrams() }.toString()
      } catch (e: Exception) {
        estimated = false
        error = e.message
      }
  }
  Modal(if (original == null) "Create recipe" else "Edit recipe · new version", onDismiss) {
    Field("Recipe name", name, { name = it })
    ingredients.toList().forEachIndexed { i, item ->
      Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
          Text(item.food.name)
          Text("${item.amount.fmt(1)} ${item.unit}", style = MaterialTheme.typography.bodySmall)
        }
        IconButton(onClick = { ingredients.removeAt(i) }) {
          Icon(Icons.Rounded.Close, "Remove ingredient")
        }
      }
    }
    OutlinedButton(onClick = { searching = true }) {
      Icon(Icons.Rounded.Add, null)
      Text("Add ingredient")
    }
    Field(
      "Finished batch weight (g)",
      grams,
      {
        grams = it
        estimated = false
      },
      true,
    )
    Text(
      "Weigh the finished recipe after cooking. Water gained or lost changes the nutrients per gram.",
      style = MaterialTheme.typography.bodySmall,
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
      Checkbox(
        estimated,
        {
          try {
            if (it) grams = ingredients.sumOf { ingredient -> ingredient.massGrams() }.toString()
            estimated = it
            error = null
          } catch (e: Exception) {
            estimated = false
            error = e.message
          }
        },
      )
      Text("Use an estimated ingredient weight", Modifier.weight(1f))
    }
    Field("Number of servings (optional)", servings, { servings = it }, true)
    if (ingredients.isNotEmpty()) {
      val batch = runCatching { Nutrients.total(ingredients.map { it.food.portion(it.amount, it.unit) }) }.getOrNull()
      Panel(tint = MaterialTheme.colorScheme.secondaryContainer) {
        Text("Batch nutrition", style = MaterialTheme.typography.titleMedium)
        Text("${energy(batch?.kcal)} in ${grams.toDoubleOrNull()?.fmt() ?: "—"} g finished weight")
        val portionKcal = batch?.kcal?.let { kcal -> grams.toDoubleOrNull()?.takeIf { it > 0 }?.let { kcal * 100 / it } }
        Text("${energy(portionKcal)} per 100 g")
        servings.toDoubleOrNull()?.takeIf { it > 0 }?.let { count -> Text("${energy(batch?.kcal?.div(count))} per serving") }
      }
    }
    OutlinedButton(onClick = { photo.launch("image/*") }) {
      Text(if (photoId == null) "Add recipe photo" else "Replace photo")
    }
    ErrorText(error)
    Button(
      onClick = {
        try {
          require(name.isNotBlank() && ingredients.isNotEmpty()) {
            "Add a name and at least one ingredient"
          }
          val servingCount = servings.takeIf { it.isNotBlank() }?.toDouble()
          require(servingCount == null || servingCount > 0)
          val r =
            Recipe(
              familyId = original?.familyId ?: newId(),
              version = (original?.version ?: 0) + 1,
              name = name,
              ingredients = ingredients.toList(),
              batchGrams = grams.toDouble(),
              servings = servingCount,
              estimatedWeight = estimated,
              photoId = photoId,
              favourite = original?.favourite ?: false,
            )
          r.portion(100.0)
          onSave(r)
        } catch (e: Exception) {
          error = e.message ?: "Check batch weight and ingredients."
        }
      },
      modifier = Modifier.fillMaxWidth(),
    ) {
      Text("Save recipe")
    }
  }
  if (searching)
    FoodSearch(vm, { searching = false }) {
      ingredient = it
      unit = it.basis
      amount = "100"
      searching = false
    }
  ingredient?.let { food ->
    Modal("Add ${food.name}", { ingredient = null }) {
      Field("Quantity", amount, { amount = it }, true)
      Choice(
        "Unit",
        listOf(food.basis) + if (food.serving != null) listOf("serving") else emptyList(),
        unit,
        { unit = it },
      )
      ErrorText(error)
      Button(
        onClick = {
          try {
            food.portion(amount.toDouble(), unit)
            ingredients += Ingredient(food, amount.toDouble(), unit)
            ingredient = null
          } catch (e: Exception) {
            error = e.message
          }
        }
      ) {
        Text("Add to recipe")
      }
    }
  }
}

@Composable
fun LocalPhoto(vm: TrackerViewModel, id: String, modifier: Modifier = Modifier) {
  var image by remember(id) { mutableStateOf<ImageBitmap?>(null) }
  LaunchedEffect(id) {
    try {
      val b = vm.store.photoBytes(id)
      image = android.graphics.BitmapFactory.decodeByteArray(b, 0, b.size)?.asImageBitmap()
    } catch (_: Exception) {}
  }
  image?.let {
    Image(
      it,
      "Saved photograph",
      modifier,
      contentScale = androidx.compose.ui.layout.ContentScale.Crop,
    )
  }
}

@Composable
fun ProgressScreen(vm: TrackerViewModel, s: AppState) {
  var add by remember { mutableStateOf(false) }
  var compare by remember { mutableStateOf(false) }
  var fullPhoto by remember { mutableStateOf<Photo?>(null) }
  var weightRange by remember { mutableIntStateOf(30) }
  val picker =
    rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
      if (uri != null) vm.run { vm.store.importPhoto(uri) }
    }
  val weights = s.measurements.filter { it.type == "Weight" }.sortedBy { it.date }
  LazyColumn(
    contentPadding = PaddingValues(20.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    item {
      PageTitle("See the bigger picture", "Your progress") {
        IconButton(onClick = { add = true }) { Icon(Icons.Rounded.Add, "Add measurement") }
      }
    }
    item {
      Panel(tint = MaterialTheme.colorScheme.primaryContainer) {
        Text("LATEST WEIGHT", style = MaterialTheme.typography.labelMedium)
        Text(
          "${weights.lastOrNull()?.value.fmt(1)} kg",
          style = MaterialTheme.typography.displaySmall,
        )
        Text("Target ${s.profile?.targetKg.fmt(1)} kg", style = MaterialTheme.typography.bodyMedium)
        val visibleWeights = weights.filter { weightRange == Int.MAX_VALUE || !LocalDate.parse(it.date).isBefore(LocalDate.now().minusDays(weightRange.toLong())) }
        if (visibleWeights.size >= 2) {
          WeightChart(visibleWeights, s.plans.map { it.effective })
          val changes = s.plans.filter { p -> visibleWeights.any { it.date <= p.effective } && visibleWeights.any { it.date >= p.effective } }
          if (changes.isNotEmpty()) Text("Plan changed ${changes.joinToString { it.effective }}", style = MaterialTheme.typography.bodySmall)
          Text("Weekly averages: ${Trends.weeklyWeights(s).joinToString(" → "){it.fmt(1)}} kg", style = MaterialTheme.typography.bodySmall)
        } else {
          Text(if (weights.isEmpty()) "Add a weigh-in to start your trend." else "Add another weigh-in to start your trend.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
    }
    item { Choice("Range", listOf("30 days", "90 days", "All"), when (weightRange) { 30 -> "30 days"; 90 -> "90 days"; else -> "All" }, { weightRange = when (it) { "30 days" -> 30; "90 days" -> 90; else -> Int.MAX_VALUE } }) }
    item { Section("Activity from Health Connect") }
    if (s.health.isEmpty())
      item { EmptyState("No imported activity yet", "Connect or refresh Health Connect in Settings. Runs and other workouts will appear here.") }
    items(s.health.sortedByDescending { it.date }.take(7)) { day ->
      Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(LocalDate.parse(day.date).format(java.time.format.DateTimeFormatter.ofPattern("EEEE, d MMMM")), style = MaterialTheme.typography.titleMedium)
        Text("${day.steps ?: 0} steps · ${(day.distanceMetres?.div(1000)).fmt(1)} km", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        day.workouts.forEach { WorkoutSummary(it) }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .18f))
      }
    }
    item {
      Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Consistency, not perfection", style = MaterialTheme.typography.titleLarge)
        val days = (0L..6L).map { LocalDate.now().minusDays(it).toString() }
        Text("${days.count{d->s.entries.any{it.date==d}}} of the last 7 days have food logged.")
      }
    }
    item { Section("Progress photos", "Add photo") { picker.launch("image/*") } }
    val photos = s.photos.filter { it.kind == "progress" }.sortedBy { it.date }
    if (photos.isEmpty())
      item {
        EmptyState(
          "A private view of your progress",
          "Photos are encrypted on your phone. You choose which ones to share with your coach.",
        )
      }
    else {
      item {
        if (photos.size >= 2)
          OutlinedButton(onClick = { compare = true }) { Text("Compare first & latest") }
      }
      items(photos) { p ->
        Panel {
          LocalPhoto(vm, p.id, Modifier.fillMaxWidth().height(220.dp).clickable { fullPhoto = p })
          Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(p.date)
            TextButton(onClick = { fullPhoto = p }) { Text("View / edit") }
          }
        }
      }
    }
    item { Section("Measurements", "Add") { add = true } }
    items(s.measurements.sortedByDescending { it.date }.take(30)) { m ->
      Column(Modifier.fillMaxWidth()) {
        Row {
          Column(Modifier.weight(1f)) {
            Text(
              "${m.type} · ${m.value.fmt(1)} ${m.unit}",
              style = MaterialTheme.typography.titleMedium,
            )
            Text("${m.date} · ${m.source}", style = MaterialTheme.typography.bodySmall)
          }
          if (m.source == "Manual")
            IconButton(
              onClick = {
                vm.run {
                  vm.store.update {
                    it.copy(measurements = it.measurements.filterNot { v -> v.id == m.id })
                  }
                }
              }
            ) {
              Icon(Icons.Rounded.DeleteOutline, "Delete measurement")
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .18f))
      }
    }
    item { Section("Plan history") }
    items(s.plans.sortedByDescending { it.created }) { p ->
      Column(Modifier.fillMaxWidth()) {
        Text(
          "${p.kcal.fmt()} kcal · from ${p.effective}",
          style = MaterialTheme.typography.titleMedium,
        )
        Text("P ${p.protein.fmt()} · C ${p.carbs.fmt()} · F ${p.fat.fmt()} g")
        p.intent?.let { intent ->
          Text(
            "Guided ${intent.goal} plan · ${kotlin.math.abs(intent.desiredWeeklyKg).fmt(2)} kg/week pace",
            style = MaterialTheme.typography.bodySmall,
          )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .18f))
      }
    }
  }
  if (add)
    MeasurementDialog({ add = false }) { m ->
      vm.run { vm.store.update { it.copy(measurements = it.measurements + m) } }
      add = false
    }
  if (compare)
    Modal("Then & now", { compare = false }) {
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            s.photos.filter { it.kind == "progress" }.minBy { it.date },
            s.photos.filter { it.kind == "progress" }.maxBy { it.date },
          )
          .forEach { p ->
            Column(Modifier.weight(1f)) {
              LocalPhoto(vm, p.id, Modifier.fillMaxWidth().height(280.dp))
              Text(p.date)
            }
          }
      }
      Text("Lighting, pose and clothing affect comparisons. Photos do not measure body fat.")
    }
  fullPhoto?.let { p ->
    var caption by remember(p.id) { mutableStateOf(p.caption) }
    var date by remember(p.id) { mutableStateOf(p.date) }
    Modal("Progress photo", { fullPhoto = null }) {
      LocalPhoto(vm, p.id, Modifier.fillMaxWidth().height(280.dp))
      Field("Date", date, { date = it })
      Field("Caption", caption, { caption = it })
      Button(
        onClick = {
          vm.run {
            LocalDate.parse(date)
            vm.store.update { s ->
              s.copy(
                photos =
                  s.photos.map {
                    if (it.id == p.id) it.copy(date = date, caption = caption) else it
                  }
              )
            }
            fullPhoto = null
          }
        }
      ) {
        Text("Save")
      }
      OutlinedButton(
        onClick = {
          vm.send("Discuss this progress photo from ${p.date}. ${p.caption}", listOf(p.id))
          fullPhoto = null
        }
      ) {
        Text("Share with coach")
      }
      TextButton(
        onClick = {
          vm.run { vm.store.deletePhoto(p.id) }
          fullPhoto = null
        }
      ) {
        Text("Delete photo")
      }
    }
  }
}

@Composable
fun WeightChart(weights: List<Measurement>, planDates: List<String> = emptyList()) {
  val color = MaterialTheme.colorScheme.primary
  val markerColor = MaterialTheme.colorScheme.tertiary
  Canvas(Modifier.fillMaxWidth().height(110.dp)) {
    if (weights.size >= 2) {
      val low = weights.minOf { it.value } - 0.5
      val high = weights.maxOf { it.value } + 0.5
      val first = LocalDate.parse(weights.first().date).toEpochDay()
      val span = (LocalDate.parse(weights.last().date).toEpochDay() - first).coerceAtLeast(1)
      planDates.forEach { date ->
        val day = runCatching { LocalDate.parse(date).toEpochDay() }.getOrNull()
        if (day != null && day in first..(first + span)) {
          val x = ((day - first).toFloat() / span) * size.width
          drawLine(markerColor, Offset(x, 0f), Offset(x, size.height), 2f, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
        }
      }
      val points = weights.map { w ->
        Offset(
          ((LocalDate.parse(w.date).toEpochDay() - first).toFloat() / span) * size.width,
          size.height - ((w.value - low) / (high - low) * size.height).toFloat(),
        )
      }
      points.zipWithNext().forEach { (a, b) -> drawLine(color, a, b, 5f) }
      points.forEach { drawCircle(color, 5f, it) }
    }
  }
}

@Composable
fun MeasurementDialog(onDismiss: () -> Unit, onSave: (Measurement) -> Unit) {
  var type by remember { mutableStateOf("Weight") }
  var value by remember { mutableStateOf("") }
  var date by remember { mutableStateOf(today()) }
  var error by remember { mutableStateOf<String?>(null) }
  Modal("Add measurement", onDismiss) {
    Choice("Measurement", listOf("Weight", "Waist", "Hip", "Chest"), type, { type = it })
    Field(
      if (type == "Weight") "Weight (kg)" else "Circumference (cm)",
      value,
      { value = it },
      true,
    )
    Field("Date", date, { date = it })
    ErrorText(error)
    Button(
      onClick = {
        try {
          LocalDate.parse(date)
          val v = value.toDouble()
          require(v.isFinite() && v > 0)
          onSave(
            Measurement(
              date = date,
              type = type,
              value = v,
              unit = if (type == "Weight") "kg" else "cm",
            )
          )
        } catch (_: Exception) {
          error = "Enter a valid date and positive measurement."
        }
      }
    ) {
      Text("Save measurement")
    }
  }
}
