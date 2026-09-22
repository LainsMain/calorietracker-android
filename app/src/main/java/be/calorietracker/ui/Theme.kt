package be.calorietracker.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*

val Forest = Color(0xFF244C3C)
val Lime = Color(0xFFD5E9A4)
private val Light =
  lightColorScheme(
    primary = Forest,
    onPrimary = Color.White,
    primaryContainer = Lime,
    onPrimaryContainer = Color(0xFF193020),
    secondary = Color(0xFF676A4A),
    secondaryContainer = Color(0xFFE8EACD),
    tertiary = Color(0xFF89614A),
    tertiaryContainer = Color(0xFFFFDBC6),
    background = Color(0xFFF8F9F3),
    surface = Color(0xFFF8F9F3),
    surfaceContainer = Color(0xFFEEF1E7),
    surfaceContainerHigh = Color(0xFFE7EBDD),
  )

@Composable
fun TrackerTheme(
  mode: String = "System",
  dynamic: Boolean = false,
  content: @Composable () -> Unit,
) {
  val dark =
    when (mode) {
      "Light" -> false
      "Dark" -> true
      else -> isSystemInDarkTheme()
    }
  val colours =
    if (dark) {
      if (dynamic && Build.VERSION.SDK_INT >= 31) dynamicDarkColorScheme(LocalContext.current)
      else darkColorScheme(primary = Lime, primaryContainer = Forest)
    } else if (dynamic && Build.VERSION.SDK_INT >= 31) dynamicLightColorScheme(LocalContext.current)
    else Light
  MaterialTheme(
    colorScheme = colours,
    typography =
      Typography(
        displayLarge =
          androidx.compose.ui.text.TextStyle(
            fontSize = 64.sp,
            lineHeight = 68.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = (-3).sp,
          ),
        headlineLarge =
          androidx.compose.ui.text.TextStyle(
            fontSize = 34.sp,
            lineHeight = 40.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-1).sp,
          ),
        titleLarge =
          androidx.compose.ui.text.TextStyle(
            fontSize = 22.sp,
            lineHeight = 28.sp,
            fontWeight = FontWeight.Medium,
          ),
      ),
    content = content,
  )
}

@Composable
fun LockScreen(unlock: () -> Unit) {
  Surface(Modifier.fillMaxSize()) {
    Column(
      Modifier.padding(32.dp),
      verticalArrangement = Arrangement.Center,
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Text("Your space.\nYour progress.", style = MaterialTheme.typography.headlineLarge)
      Spacer(Modifier.height(24.dp))
      Button(onClick = unlock) { Text("Unlock diary") }
    }
  }
}

val LocalEnergyUnit = androidx.compose.runtime.staticCompositionLocalOf { "kcal" }

@Composable
fun energyValue(kcal: Double?): String =
  (kcal?.times(if (LocalEnergyUnit.current == "kJ") 4.184 else 1.0)).fmt()

@Composable fun energy(kcal: Double?): String = "${energyValue(kcal)} ${LocalEnergyUnit.current}"

@Composable
fun TrackerRoot(vm: TrackerViewModel, quickLog: Boolean = false) {
  val prefs by
    vm.prefs.flow.collectAsState(initial = androidx.datastore.preferences.core.emptyPreferences())
  val mode = prefs[androidx.datastore.preferences.core.stringPreferencesKey("theme")] ?: "System"
  val dynamic =
    prefs[androidx.datastore.preferences.core.stringPreferencesKey("dynamicColour")] == "true"
  TrackerTheme(mode, dynamic) {
    androidx.compose.runtime.CompositionLocalProvider(
      LocalEnergyUnit provides
        (prefs[androidx.datastore.preferences.core.stringPreferencesKey("energyUnit")] ?: "kcal")
    ) {
      TrackerApp(vm, quickLog)
    }
  }
}
