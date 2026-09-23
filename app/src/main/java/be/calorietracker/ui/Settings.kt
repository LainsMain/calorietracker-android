package be.calorietracker.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import be.calorietracker.BuildConfig
import be.calorietracker.domain.*
import be.calorietracker.services.Release
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun SettingsScreen(vm: TrackerViewModel, initialPage: String = "Home", onDismiss: () -> Unit) {
  val context = LocalContext.current
  var page by remember(initialPage) { mutableStateOf(initialPage) }
  var theme by remember { mutableStateOf("System") }
  var dynamicColour by remember { mutableStateOf(false) }
  var energyUnit by remember { mutableStateOf("kcal") }
  var profileEditor by remember { mutableStateOf(false) }
  var key by remember { mutableStateOf("") }
  var hasKey by remember { mutableStateOf(false) }
  var biometric by remember { mutableStateOf(false) }
  var reminders by remember { mutableStateOf(false) }
  var password by remember { mutableStateOf("") }
  var includePhotos by remember { mutableStateOf(true) }
  var status by remember { mutableStateOf("") }
  var healthGranted by remember { mutableStateOf(emptySet<String>()) }
  var healthSync by remember { mutableStateOf("") }
  var healthSources by remember { mutableStateOf("") }
  var healthError by remember { mutableStateOf("") }
  var healthDiagnostics by remember { mutableStateOf(false) }
  var delete by remember { mutableStateOf(false) }
  var restoreUri by remember { mutableStateOf<Uri?>(null) }
  var meals by remember { mutableStateOf(vm.state.value.meals.joinToString(", ")) }
  var waterGoal by remember { mutableStateOf(vm.state.value.waterGoalMl.toString()) }
  var waterAmounts by remember { mutableStateOf(vm.state.value.waterQuickAmountsMl.joinToString(", ")) }
  var fastingEnabled by remember { mutableStateOf(vm.state.value.fasting.enabled) }
  var fastingStart by remember { mutableStateOf(vm.state.value.fasting.starts) }
  var fastingEnd by remember { mutableStateOf(vm.state.value.fasting.ends) }
  LaunchedEffect(Unit) {
    theme = vm.prefs.get("theme", "System")
    dynamicColour = vm.prefs.get("dynamicColour") == "true"
    energyUnit = vm.prefs.get("energyUnit", "kcal")
    hasKey = vm.prefs.apiKey().isNotBlank()
    biometric = vm.prefs.get("biometric") == "true"
    reminders = vm.prefs.get("reminders") == "true"
    healthGranted = vm.health.granted()
    healthSync = vm.prefs.get("healthSync")
    healthSources = vm.prefs.get("healthSources")
    healthError = vm.prefs.get("healthError")
  }
  val export =
    rememberLauncherForActivityResult(
      ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
      if (uri != null)
        vm.run {
          vm.backups.export(uri, password, includePhotos)
          status = "Encrypted backup exported."
        }
    }
  val restore =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      restoreUri = uri
    }
  val permissions =
    rememberLauncherForActivityResult(
      PermissionController.createRequestPermissionResultContract()
    ) { granted ->
      vm.run {
        vm.prefs.set("healthEnabled", (granted.isNotEmpty()).toString())
        try {
          vm.health.sync()
          status = "Health Connect refreshed. Only granted data types are imported."
        } finally {
          healthGranted = vm.health.granted()
          healthSync = vm.prefs.get("healthSync")
          healthSources = vm.prefs.get("healthSources")
          healthError = vm.prefs.get("healthError")
        }
      }
    }
  val notifications =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      if (!granted) status = "Notifications are disabled in Android settings."
    }
  Modal(if (page == "Home") "Settings" else page, { if (page == "Home") onDismiss() else page = "Home" }) {
    if (page == "Home") {
    Text("LOCAL BY DESIGN", style = MaterialTheme.typography.labelMedium)
    Text(
      "Your diary, photos and chat are encrypted on this device. There is no account or automatic cloud backup."
    )
    listOf(
      "Coach & privacy" to "AI key and private chat",
      "Health Connect" to "Activity, permissions and sync",
      "Appearance" to "Theme, units and app lock",
      "Tracking" to "Profile, meals, water and reminders",
      "Fasting" to "Eating hours and daily status",
      "Backup & updates" to "Keep your data safe and current",
      "Sources & licenses" to "Food data, source code and deletion",
    ).forEach { (title, detail) ->
      Surface(onClick = { page = title }, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.fillMaxWidth().padding(18.dp)) {
          Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodySmall)
          }
          Text("›", style = MaterialTheme.typography.titleLarge)
        }
      }
    }
    }
    if (page == "Fasting") {
      Text("Choose when you usually eat. This is a reminder, not a lock: you can log meals whenever you need to.")
      Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text("Show my eating window", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        Switch(fastingEnabled, { fastingEnabled = it })
      }
      Choice("Quick schedules", listOf("12:12", "14:10", "16:8"),
        when (fastingStart to fastingEnd) {
          "08:00" to "20:00" -> "12:12"
          "10:00" to "20:00" -> "14:10"
          "12:00" to "20:00" -> "16:8"
          else -> "Custom"
        },
        { selected ->
          fastingStart = when (selected) { "12:12" -> "08:00"; "14:10" -> "10:00"; else -> "12:00" }
          fastingEnd = "20:00"
        }
      )
      Field("Eating starts (HH:mm)", fastingStart, { fastingStart = it })
      Field("Eating ends (HH:mm)", fastingEnd, { fastingEnd = it })
      Text("Times follow your device’s local timezone, including when you travel. Overnight windows are supported.", style = MaterialTheme.typography.bodySmall)
      Button(onClick = {
        try {
          val choice = FastingWindow(fastingEnabled, fastingStart.trim(), fastingEnd.trim())
          choice.validate()
          vm.run { vm.store.update { it.copy(fasting = choice) } }
          status = if (choice.enabled) "Eating window saved." else "Fasting display turned off."
        } catch (_: Exception) { status = "Enter different valid 24-hour times, such as 12:00 and 20:00." }
      }, modifier = Modifier.fillMaxWidth()) { Text("Save eating window") }
    }
    if (page == "Coach & privacy") {
    Section("DeepSeek coach")
    Text(
      "Using the coach sends your selected messages, relevant health and diary records, and attached images to DeepSeek. API usage is billed to your own key. Local deletion cannot retract previous submissions."
    )
    OutlinedTextField(
      key,
      { key = it },
      label = { Text(if (hasKey) "Replace API key (saved securely)" else "DeepSeek API key") },
      visualTransformation = PasswordVisualTransformation(),
      singleLine = true,
      modifier = Modifier.fillMaxWidth(),
    )
    Text("Model: deepseek-flash · thinking mode", style = MaterialTheme.typography.bodySmall)
    Button(
      onClick = {
        vm.run {
          if (key.isNotBlank()) {
            vm.prefs.saveKey(key)
            key = ""
            hasKey = true
          }
          vm.prefs.set("model", "deepseek-flash")
          status = "Coach settings saved."
        }
      }
    ) {
      Text("Save AI settings")
    }
    if (hasKey)
      TextButton(
        onClick = {
          vm.run {
            vm.prefs.saveKey("")
            hasKey = false
            status = "API key removed."
          }
        }
      ) {
        Text("Remove API key")
      }
    }
    if (page == "Health Connect") {
    Section("Health & activity")
    Text(
      "Runs, workouts, steps, distance, energy, and weight can be read from Health Connect. Activity helps explain your weekly trend; it never raises today's food budget."
    )
    val stale = remember(healthSync) {
      healthSync.isNotBlank() && runCatching { java.time.Duration.between(Instant.parse(healthSync), Instant.now()).toHours() > 24 }.getOrDefault(false)
    }
    Panel(tint = if (healthGranted.isNotEmpty()) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer) {
      Text(
        when {
          !vm.health.available() -> "Health Connect unavailable"
          healthError.contains("revoked", true) -> "Permissions revoked"
          healthError.isNotBlank() -> "Refresh failed"
          healthGranted.isEmpty() -> "Not connected"
          stale -> "Connected · data may be stale"
          else -> "Connected"
        },
        style = MaterialTheme.typography.titleMedium,
      )
      if (healthGranted.isNotEmpty()) {
        Text("${healthGranted.intersect(vm.health.permissions).size} data permissions granted")
        Text(if (healthSync.isBlank()) "Connected with no data yet" else "Last refreshed ${localDateTime(healthSync)}")
        Text(if (healthSources.isBlank()) "No workout apps have shared data yet" else "Workout apps: $healthSources")
      }
      if (healthError.isNotBlank()) Text("Last refresh failed. Your existing activity is unchanged.", color = MaterialTheme.colorScheme.error)
    }
    Button(
      onClick = {
        if (vm.health.available()) permissions.launch(vm.health.permissions)
        else
          context.startActivity(
            Intent(
              Intent.ACTION_VIEW,
              Uri.parse("market://details?id=com.google.android.apps.healthdata"),
            )
          )
      }
    ) {
      Text("Connect / manage permissions")
    }
    if (vm.health.optionalPermissions().isNotEmpty())
      TextButton(onClick = { permissions.launch(vm.health.optionalPermissions()) }) {
        Text("Allow background sync / older history")
      }
    OutlinedButton(
      onClick = {
        vm.run {
          try {
            vm.health.sync()
            status = "Activity refreshed."
          } finally {
            healthGranted = vm.health.granted()
            healthSync = vm.prefs.get("healthSync")
            healthSources = vm.prefs.get("healthSources")
            healthError = vm.prefs.get("healthError")
          }
        }
      }
    ) {
      Text("Refresh activity")
    }
    TextButton(
      onClick = {
        vm.run {
          vm.prefs.set("healthEnabled", "false")
          vm.store.update {
            it.copy(
              health = emptyList(),
              measurements = it.measurements.filter { m -> m.sourceId == null },
            )
          }
          status =
            "Local imported activity removed. Manage permission access in Android Health Connect settings."
        }
      }
    ) {
      Text("Disconnect & remove imports")
    }
    TextButton(onClick = { healthDiagnostics = !healthDiagnostics }) { Text(if (healthDiagnostics) "Hide technical details" else "Technical details") }
    if (healthDiagnostics) {
      val state by vm.state.collectAsStateWithLifecycle()
      Text("Readable window: ${if ("android.permission.health.READ_HEALTH_DATA_HISTORY" in healthGranted) "up to 90 days" else "30 days"}", style = MaterialTheme.typography.bodySmall)
      Text("Record origins: ${state.health.flatMap { it.origins }.distinct().joinToString().ifBlank { "none" }}", style = MaterialTheme.typography.bodySmall)
      if (healthError.isNotBlank()) Text("Last error: $healthError", style = MaterialTheme.typography.bodySmall)
    }
    }
    if (page == "Appearance") {
    Section("Make it yours")
    Choice(
      "Appearance",
      listOf("System", "Light", "Dark"),
      theme,
      {
        theme = it
        vm.run { vm.prefs.set("theme", it) }
      },
    )
    Row {
      Text("Use wallpaper colours", Modifier.weight(1f))
      Switch(
        dynamicColour,
        {
          dynamicColour = it
          vm.run { vm.prefs.set("dynamicColour", it.toString()) }
        },
      )
    }
    Choice(
      "Display energy in",
      listOf("kcal", "kJ"),
      energyUnit,
      {
        energyUnit = it
        vm.run { vm.prefs.set("energyUnit", it) }
      },
    )
    Row {
      Text("Require device unlock on app launch", Modifier.weight(1f))
      Switch(
        biometric,
        { enabled ->
          val manager = androidx.biometric.BiometricManager.from(context)
          if (
            !enabled ||
              manager.canAuthenticate(
                androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG or
                  androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
              ) == androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS
          ) {
            biometric = enabled
            vm.run { vm.prefs.set("biometric", enabled.toString()) }
          } else status = "Set up a device PIN or biometrics first."
        },
      )
    }
    }
    if (page == "Tracking") {
    Section("Your tracking")
    OutlinedButton(onClick = { profileEditor = true }) { Text("Edit profile & goals") }
    Field("Meal names, comma-separated", meals, { meals = it })
    TextButton(
      onClick = {
        vm.run {
          val names = meals.split(",").map { it.trim() }.filter { it.isNotBlank() }.distinct()
          require(names.isNotEmpty())
          vm.store.update { it.copy(meals = (names + it.entries.map { e -> e.meal }).distinct()) }
          status = "Meal names saved."
        }
      }
    ) {
      Text("Save meals")
    }
    Section("Hydration")
    Field("Daily water goal (ml)", waterGoal, { waterGoal = it }, true)
    Field("Quick-add amounts (ml, comma-separated)", waterAmounts, { waterAmounts = it })
    TextButton(onClick = {
      val goal = waterGoal.toIntOrNull()
      val amounts = waterAmounts.split(",").mapNotNull { it.trim().toIntOrNull() }.distinct()
      if (goal == null || goal !in 250..10000 || amounts.isEmpty() || amounts.any { it !in 25..2000 })
        status = "Use a 250–10,000 ml goal and quick amounts from 25–2,000 ml."
      else vm.run {
        vm.store.update { it.copy(waterGoalMl = goal, waterQuickAmountsMl = amounts) }
        status = "Water preferences saved."
      }
    }) { Text("Save water preferences") }
    Row {
      Text("Gentle tracking reminders", Modifier.weight(1f))
      Switch(
        reminders,
        {
          reminders = it
          vm.run { vm.prefs.set("reminders", it.toString()) }
          if (it && Build.VERSION.SDK_INT >= 33)
            notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        },
      )
    }
    }
    if (page == "Backup & updates") {
    Section("Encrypted backup")
    Text(
      "Export before changing phones or uninstalling. Restore replaces this device's diary. API keys and imported health records are excluded from restoration."
    )
    OutlinedTextField(
      password,
      { password = it },
      label = { Text("Backup password (10+ characters)") },
      visualTransformation = PasswordVisualTransformation(),
      modifier = Modifier.fillMaxWidth(),
    )
    Row {
      Text("Include photos", Modifier.weight(1f))
      Switch(includePhotos, { includePhotos = it })
    }
    Row {
      Button(
        onClick = { export.launch("CalorieTracker-${today()}.ctbackup") },
        enabled = password.length >= 10,
      ) {
        Text("Export")
      }
      TextButton(onClick = { restore.launch(arrayOf("*/*")) }, enabled = password.length >= 10) {
        Text("Restore")
      }
    }
    Section("App updates")
    Text("Version ${BuildConfig.VERSION_NAME} · signed GitHub releases")
    Button(
      onClick = {
        vm.run {
          val release = vm.updates.check(true)
          vm.release.value = release
          status =
            if (release == null) "You're on the latest available version." else "Update found."
        }
      }
    ) {
      Text("Check for updates")
    }
    }
    if (page == "Sources & licenses") {
    Section("Sources & licences")
    Text(
      "Open Food Facts: ODbL database, attribution and share-alike. CoFID 2021: Crown copyright, Open Government Licence v3.0. Food values can be incomplete or approximate. Trace values remain unknown."
    )
    TextButton(
      onClick = {
        context.startActivity(
          Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://github.com/LainsMain/calorietracker-android"),
          )
        )
      }
    ) {
      Text("Source code, privacy & documentation")
    }
    if (status.isNotBlank()) Text(status, color = MaterialTheme.colorScheme.primary)
    TextButton(onClick = { delete = true }) {
      Text("Delete all local data", color = MaterialTheme.colorScheme.error)
    }
    }
    if (status.isNotBlank() && page != "Sources & licenses") Text(status, color = MaterialTheme.colorScheme.primary)
  }
  if (profileEditor) ProfileEditor(vm) { profileEditor = false }
  if (delete)
    AlertDialog(
      onDismissRequest = { delete = false },
      title = { Text("Delete everything on this device?") },
      text = {
        Text(
          "This removes your diary, plans, recipes, chat, photos, imported health data and API key. Export a backup first if you want to keep them."
        )
      },
      confirmButton = {
        TextButton(
          onClick = {
            vm.run {
              vm.cancelCoach()
              vm.store.clear()
              vm.prefs.clear()
            }
            delete = false
            onDismiss()
          }
        ) {
          Text("Delete everything")
        }
      },
      dismissButton = { TextButton(onClick = { delete = false }) { Text("Cancel") } },
    )
  restoreUri?.let { uri ->
    AlertDialog(
      onDismissRequest = { restoreUri = null },
      title = { Text("Replace this diary with the backup?") },
      text = {
        Text(
          "The backup is validated before replacement. Device-specific health sync will restart. Your API key is not restored."
        )
      },
      confirmButton = {
        TextButton(
          onClick = {
            vm.run {
              vm.backups.restore(uri, password)
              status = "Backup restored."
            }
            restoreUri = null
          }
        ) {
          Text("Restore")
        }
      },
      dismissButton = { TextButton(onClick = { restoreUri = null }) { Text("Cancel") } },
    )
  }
}

@Composable
fun UpdateDialog(vm: TrackerViewModel, release: Release, onDismiss: () -> Unit) {
  val progress by vm.updates.progress.collectAsStateWithLifecycle()
  Modal("A fresh update · ${release.versionName}", onDismiss) {
    Text(release.notes)
    Text("${(release.bytes/1024.0/1024.0).fmt(1)} MB")
    when (progress.status) {
      "downloading" -> {
        LinearProgressIndicator(
          progress = { (progress.received.toDouble() / progress.total.coerceAtLeast(1)).toFloat() },
          modifier = Modifier.fillMaxWidth(),
        )
        Text(
          "${(100.0*progress.received/progress.total.coerceAtLeast(1)).fmt()}% · ${(progress.received/1024.0/1024.0).fmt(1)} / ${(progress.total/1024.0/1024.0).fmt(1)} MB"
        )
        TextButton(onClick = { vm.updates.cancel() }) { Text("Cancel download") }
      }
      "ready" -> {
        Text(
          "Download verified. Android will ask you to confirm installation. If you enable installation permission, return here and tap Install again."
        )
        Button(onClick = { vm.run { vm.updates.install() } }) { Text("Install update") }
      }
      else -> {
        ErrorText(progress.error)
        Button(onClick = { vm.run { vm.updates.download(release) } }) {
          Text(if (progress.status == "error") "Retry download" else "Download update")
        }
        TextButton(onClick = onDismiss) { Text("Later") }
      }
    }
  }
}

private fun localDateTime(value: String): String =
  runCatching {
      DateTimeFormatter.ofPattern("d MMM, HH:mm")
        .format(Instant.parse(value).atZone(ZoneId.systemDefault()))
    }
    .getOrDefault("recently")
