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

val Ink = Color(0xFF1C292D)
val Petrol = Color(0xFF245B61)
val Citrus = Color(0xFFF0C86E)
val Terracotta = Color(0xFFD97C61)
val Porcelain = Color(0xFFFAF7F0)
private val Light =
  lightColorScheme(
    primary = Petrol,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD4ECE8),
    onPrimaryContainer = Ink,
    secondary = Color(0xFF805A20),
    secondaryContainer = Color(0xFFE7ECE8),
    onSecondaryContainer = Ink,
    tertiary = Color(0xFF8D4434),
    tertiaryContainer = Color(0xFFF9D9CB),
    onTertiaryContainer = Ink,
    background = Porcelain,
    onBackground = Ink,
    surface = Porcelain,
    onSurface = Ink,
    onSurfaceVariant = Color(0xFF526063),
    surfaceContainerLow = Color(0xFFFFFCF7),
    surfaceContainer = Color(0xFFF1EDE5),
    surfaceContainerHigh = Color(0xFFE9E3D9),
    outline = Color(0xFF879496),
  )
private val Dark =
  darkColorScheme(
    primary = Color(0xFF9ED4D3),
    onPrimary = Color(0xFF12373A),
    primaryContainer = Color(0xFF1D464B),
    onPrimaryContainer = Color(0xFFD8F3EF),
    secondary = Color(0xFFD9B779),
    secondaryContainer = Color(0xFF2A3B3D),
    onSecondaryContainer = Color(0xFFE2EFEB),
    tertiary = Color(0xFFF1AD94),
    tertiaryContainer = Color(0xFF673C31),
    onTertiaryContainer = Color(0xFFFFE0D5),
    background = Color(0xFF141E21),
    onBackground = Color(0xFFF2F1EC),
    surface = Color(0xFF141E21),
    onSurface = Color(0xFFF2F1EC),
    onSurfaceVariant = Color(0xFFBEC8C6),
    surfaceContainerLow = Color(0xFF1A272A),
    surfaceContainer = Color(0xFF223236),
    surfaceContainerHigh = Color(0xFF2B4043),
    outline = Color(0xFF95A7A6),
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
      else Dark
    } else if (dynamic && Build.VERSION.SDK_INT >= 31) dynamicLightColorScheme(LocalContext.current)
    else Light
  MaterialTheme(
    colorScheme = colours,
    typography =
      Typography(
        displayLarge =
          androidx.compose.ui.text.TextStyle(
            fontSize = 56.sp,
            lineHeight = 62.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = (-3).sp,
          ),
        headlineLarge =
          androidx.compose.ui.text.TextStyle(
            fontSize = 31.sp,
            lineHeight = 36.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-1).sp,
          ),
        titleLarge =
          androidx.compose.ui.text.TextStyle(
            fontSize = 21.sp,
            lineHeight = 27.sp,
            fontWeight = FontWeight.SemiBold,
          ),
        titleMedium = androidx.compose.ui.text.TextStyle(fontSize = 17.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold),
        bodyLarge = androidx.compose.ui.text.TextStyle(fontSize = 16.sp, lineHeight = 23.sp, fontWeight = FontWeight.Normal),
        bodyMedium = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal),
        labelLarge = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.Medium),
      ),
    shapes = Shapes(
      small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
      medium = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
      large = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
      extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(30.dp),
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
  val mode = prefs[androidx.datastore.preferences.core.stringPreferencesKey("theme")] ?: "Dark"
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
