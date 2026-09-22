package be.calorietracker.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import be.calorietracker.domain.*
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.time.LocalDate
import java.util.concurrent.Executors

@Composable
fun FoodSearch(vm: TrackerViewModel, onDismiss: () -> Unit, onChoose: (Food) -> Unit) {
  var query by rememberSaveable { mutableStateOf("") }
  var scanning by remember { mutableStateOf(false) }
  var custom by remember { mutableStateOf(false) }
  var barcode by remember { mutableStateOf("") }
  var missing by remember { mutableStateOf(false) }
  val results by vm.results.collectAsStateWithLifecycle()
  val searching by vm.searching.collectAsStateWithLifecycle()
  LaunchedEffect(query) { vm.search(query) }
  Modal("Find your food", onDismiss) {
    Field("Search foods, brands, Dutch or French names", query, { query = it })
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      FilledTonalButton(onClick = { scanning = true }) {
        Icon(Icons.Rounded.QrCodeScanner, null)
        Text(" Scan")
      }
      OutlinedButton(onClick = { custom = true }) { Text("Create food") }
    }
    Field("Or enter a barcode", barcode, { barcode = it }, true)
    if (barcode.isNotBlank())
      Button(
        onClick = {
          vm.barcode(barcode) { f ->
            if (f != null) onChoose(f)
            else {
              missing = true
              custom = true
            }
          }
        }
      ) {
        Text("Look up barcode")
      }
    if (searching) LinearProgressIndicator(Modifier.fillMaxWidth())
    Text(
      if (query.isBlank()) "Recent & favourite foods" else "Results · Belgian products preferred",
      style = MaterialTheme.typography.labelLarge,
    )
    results.forEach { food ->
      FoodRow(
        food,
        { onChoose(food) },
        {
          vm.run {
            vm.store.favourite(food)
            vm.search(query)
          }
        },
      )
    }
    if (results.isEmpty() && !searching)
      Text("No match yet. Try a different name, scan a product, or create a food.")
    Text(
      "Open Food Facts (ODbL) · CoFID 2021 (OGL v3). Product coverage and completeness vary.",
      style = MaterialTheme.typography.bodySmall,
    )
  }
  if (scanning)
    Scanner(
      onDismiss = { scanning = false },
      onCode = { code ->
        scanning = false
        barcode = code
        vm.barcode(code) { f ->
          if (f != null) onChoose(f)
          else {
            missing = true
            custom = true
          }
        }
      },
    )
  if (custom)
    CustomFoodEditor(
      initialBarcode = barcode.takeIf { it.isNotBlank() },
      onDismiss = { custom = false },
      onSave = { f ->
        vm.run { vm.store.cache(listOf(f)) }
        custom = false
        onChoose(f)
      },
      vm = vm,
    )
}

@Composable
fun PortionEditor(
  food: Food,
  date: String,
  meals: List<String>,
  entry: Entry? = null,
  onDismiss: () -> Unit,
  onSave: (Entry) -> Unit,
  onDelete: (() -> Unit)? = null,
) {
  var selectedFood by remember { mutableStateOf(food) }
  var editNutrition by remember { mutableStateOf(false) }
  var amount by remember { mutableStateOf(entry?.amount?.toString() ?: "100") }
  var unit by remember { mutableStateOf(entry?.unit ?: selectedFood.basis) }
  var meal by remember { mutableStateOf(entry?.meal ?: meals.first()) }
  var selectedDate by remember { mutableStateOf(date) }
  var note by remember { mutableStateOf(entry?.note ?: "") }
  var error by remember { mutableStateOf<String?>(null) }
  val n = runCatching { selectedFood.portion(amount.toDouble(), unit) }.getOrNull()
  Modal(selectedFood.name, onDismiss) {
    Text(
      "${selectedFood.source} · values per 100 ${selectedFood.basis}",
      style = MaterialTheme.typography.bodySmall,
    )
    Field("Amount", amount, { amount = it }, true)
    Choice(
      "Unit",
      listOf(selectedFood.basis) +
        if (selectedFood.serving != null) listOf("serving") else emptyList(),
      unit,
      { unit = it },
    )
    Choice("Meal", meals, meal, { meal = it })
    Field("Date (YYYY-MM-DD)", selectedDate, { selectedDate = it })
    Field("Notes", note, { note = it })
    TextButton(onClick = { editNutrition = true }) { Text("Edit nutrition / portion definitions") }
    if (n != null) NutrientGrid(n)
    Text("— means unknown, not zero.", style = MaterialTheme.typography.bodySmall)
    ErrorText(error)
    Button(
      onClick = {
        try {
          LocalDate.parse(selectedDate)
          val portion = selectedFood.portion(amount.toDouble(), unit)
          onSave(
            Entry(
              id = entry?.id ?: newId(),
              date = selectedDate,
              meal = meal,
              food = selectedFood,
              amount = amount.toDouble(),
              unit = unit,
              nutrients = portion,
              note = note,
              created = entry?.created ?: now(),
              zoneOffset = entry?.zoneOffset ?: offset(),
            )
          )
        } catch (e: Exception) {
          error = e.message ?: "Check amount and date."
        }
      },
      modifier = Modifier.fillMaxWidth(),
    ) {
      Text(if (entry == null) "Log food" else "Save changes")
    }
    if (entry != null)
      OutlinedButton(
        onClick = { onSave(entry.copy(id = newId(), date = today(), created = now())) },
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text("Copy to today")
      }
    onDelete?.let {
      TextButton(onClick = it) { Text("Delete entry", color = MaterialTheme.colorScheme.error) }
    }
  }
  if (editNutrition)
    CustomFoodEditor(
      initialBarcode = selectedFood.barcode,
      initial = selectedFood,
      onDismiss = { editNutrition = false },
      onSave = {
        selectedFood = it
        unit = it.basis
        editNutrition = false
      },
    )
}

@Composable
fun CustomFoodEditor(
  initialBarcode: String? = null,
  onDismiss: () -> Unit,
  onSave: (Food) -> Unit,
  vm: TrackerViewModel? = null,
  initial: Food? = null,
) {
  var name by remember { mutableStateOf(initial?.name ?: "") }
  var brand by remember { mutableStateOf(initial?.brand ?: "") }
  var basis by remember { mutableStateOf(initial?.basis ?: "g") }
  var serving by remember { mutableStateOf(initial?.serving?.toString() ?: "") }
  var density by remember { mutableStateOf(initial?.density?.toString() ?: "") }
  var error by remember { mutableStateOf<String?>(null) }
  val values = remember {
    mutableStateListOf(
      *(initial?.nutrients?.let {
          listOf(it.kcal, it.protein, it.carbs, it.fat, it.saturated, it.sugars, it.fibre, it.salt)
        } ?: List(8) { null })
        .map { it?.toString() ?: "" }
        .toTypedArray()
    )
  }
  val photo =
    rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
      if (uri != null && vm != null)
        vm.run {
          val p = vm.store.importPhoto(uri, "label")
          vm.send(
            "Read this nutrition label. Prepare an editable custom food entry, using label values per 100g or 100ml. Ask about portion amount if unknown. Do not invent missing nutrients.",
            listOf(p.id),
          )
          onDismiss()
        }
    }
  Modal("Create a food", onDismiss) {
    if (initialBarcode != null) Text("Barcode $initialBarcode")
    Field("Food name", name, { name = it })
    Field("Brand (optional)", brand, { brand = it })
    Choice("Nutrition values per 100", listOf("g", "ml"), basis, { basis = it })
    listOf(
        "Energy (kcal)",
        "Protein (g)",
        "Carbohydrates (g)",
        "Fat (g)",
        "Saturated fat (g)",
        "Sugars (g)",
        "Fibre (g)",
        "Salt (g)",
      )
      .forEachIndexed { i, label -> Field(label, values[i], { values[i] = it }, true) }
    Text("Leave unavailable values blank.")
    Field("Serving size ($basis, optional)", serving, { serving = it }, true)
    Field("Density (g/ml, optional)", density, { density = it }, true)
    if (vm != null)
      OutlinedButton(onClick = { photo.launch("image/*") }) { Text("Read a label with the coach") }
    ErrorText(error)
    Button(
      onClick = {
        try {
          require(name.isNotBlank()) { "Enter a food name" }
          val v = values.map { if (it.isBlank()) null else it.toDouble() }
          val n = Nutrients(v[0], v[1], v[2], v[3], v[4], v[5], v[6], v[7])
          require(n.valid())
          val size = serving.takeIf { it.isNotBlank() }?.toDouble()
          val d = density.takeIf { it.isNotBlank() }?.toDouble()
          require(size == null || size > 0)
          require(d == null || d > 0)
          onSave(
            Food(
              id = initial?.takeIf { it.source == "Custom" }?.id ?: newId(),
              name = name,
              brand = brand,
              barcode = initialBarcode,
              basis = basis,
              nutrients = n,
              serving = size,
              density = d,
            )
          )
        } catch (e: Exception) {
          error = e.message ?: "Check the nutrition values."
        }
      },
      modifier = Modifier.fillMaxWidth(),
    ) {
      Text("Save food")
    }
  }
}

@androidx.annotation.OptIn(ExperimentalGetImage::class)
@Composable
fun Scanner(onDismiss: () -> Unit, onCode: (String) -> Unit) {
  val context = LocalContext.current
  val lifecycle = LocalLifecycleOwner.current
  var allowed by remember {
    mutableStateOf(
      ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED
    )
  }
  val permission =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed = it }
  var found by remember { mutableStateOf(false) }
  LaunchedEffect(Unit) { if (!allowed) permission.launch(Manifest.permission.CAMERA) }
  Modal("Scan a product", onDismiss) {
    Text("Point the camera at an EAN or UPC barcode.")
    if (allowed) {
      val preview = remember { PreviewView(context) }
      val executor = remember { Executors.newSingleThreadExecutor() }
      val scanner = remember { BarcodeScanning.getClient() }
      DisposableEffect(Unit) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener(
          {
            val provider = future.get()
            val p =
              Preview.Builder().build().also { it.setSurfaceProvider(preview.surfaceProvider) }
            val analysis =
              ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(executor) { proxy ->
              val image = proxy.image
              if (image != null && !found)
                scanner
                  .process(InputImage.fromMediaImage(image, proxy.imageInfo.rotationDegrees))
                  .addOnSuccessListener { codes ->
                    codes
                      .firstOrNull { it.rawValue?.matches(Regex("[0-9]{8,14}")) == true }
                      ?.rawValue
                      ?.let {
                        if (!found) {
                          found = true
                          onCode(it)
                        }
                      }
                  }
                  .addOnCompleteListener { proxy.close() }
              else proxy.close()
            }
            try {
              provider.bindToLifecycle(lifecycle, CameraSelector.DEFAULT_BACK_CAMERA, p, analysis)
            } catch (_: Exception) {}
          },
          ContextCompat.getMainExecutor(context),
        )
        onDispose {
          if (future.isDone) future.get().unbindAll()
          scanner.close()
          executor.shutdown()
        }
      }
      AndroidView(factory = { preview }, modifier = Modifier.fillMaxWidth().height(360.dp))
    } else {
      Text("Camera access is needed to scan. You can also type the barcode in food search.")
      Button(onClick = { permission.launch(Manifest.permission.CAMERA) }) { Text("Allow camera") }
    }
  }
}
