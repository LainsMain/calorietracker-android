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
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import be.calorietracker.BuildConfig
import be.calorietracker.domain.*
import be.calorietracker.services.Release

@Composable
fun SettingsScreen(vm: TrackerViewModel, onDismiss: () -> Unit) {
  val context = LocalContext.current
  var theme by remember { mutableStateOf("System") }
  var dynamicColour by remember { mutableStateOf(false) }
  var energyUnit by remember { mutableStateOf("kcal") }
  var profileEditor by remember { mutableStateOf(false) }
  var key by remember { mutableStateOf("") }
  var hasKey by remember { mutableStateOf(false) }
  var model by remember { mutableStateOf("deepseek-flash") }
  var biometric by remember { mutableStateOf(false) }
  var reminders by remember { mutableStateOf(false) }
  var password by remember { mutableStateOf("") }
  var includePhotos by remember { mutableStateOf(true) }
  var status by remember { mutableStateOf("") }
  var delete by remember { mutableStateOf(false) }
  var restoreUri by remember { mutableStateOf<Uri?>(null) }
  var meals by remember { mutableStateOf(vm.state.value.meals.joinToString(", ")) }
  LaunchedEffect(Unit) {
    theme = vm.prefs.get("theme", "System")
    dynamicColour = vm.prefs.get("dynamicColour") == "true"
    energyUnit = vm.prefs.get("energyUnit", "kcal")
    hasKey = vm.prefs.apiKey().isNotBlank()
    model = vm.prefs.get("model", "deepseek-flash")
    biometric = vm.prefs.get("biometric") == "true"
    reminders = vm.prefs.get("reminders") == "true"
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
        vm.health.sync()
        status = "Health Connect refreshed. Only granted data types are imported."
      }
    }
  val notifications =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      if (!granted) status = "Notifications are disabled in Android settings."
    }
  Modal("Settings & privacy", onDismiss) {
    Text("LOCAL BY DESIGN", style = MaterialTheme.typography.labelMedium)
    Text(
      "Your diary, photos and chat are encrypted on this device. There is no account or automatic cloud backup."
    )
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
    Field("Model", model, { model = it })
    Button(
      onClick = {
        vm.run {
          require(model.isNotBlank())
          if (key.isNotBlank()) {
            vm.prefs.saveKey(key)
            key = ""
            hasKey = true
          }
          vm.prefs.set("model", model)
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
    Section("Health & activity")
    Text(
      "Read steps, workouts, active/total energy, distance and weight from Health Connect. We reconcile the last 30 days (90 with history access); imported exercise does not raise your food allowance."
    )
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
          vm.health.sync()
          status = "Activity refreshed."
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
              measurements = it.measurements.filter { m -> m.source == "Manual" },
            )
          }
          status =
            "Local imported activity removed. Manage permission access in Android Health Connect settings."
        }
      }
    ) {
      Text("Disconnect & remove imports")
    }
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
