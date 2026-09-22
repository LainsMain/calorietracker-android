package be.calorietracker.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import be.calorietracker.domain.*

@Composable
fun ProfileEditor(vm: TrackerViewModel, onDismiss: () -> Unit) {
  val original = vm.state.value.profile ?: return
  var name by remember { mutableStateOf(original.name) }
  var age by remember { mutableStateOf(original.age.toString()) }
  var height by remember { mutableStateOf(original.heightCm.toString()) }
  var weight by remember { mutableStateOf(original.weightKg.toString()) }
  var target by remember { mutableStateOf(original.targetKg.toString()) }
  var sex by remember { mutableStateOf(original.sex) }
  var goal by remember { mutableStateOf(original.goal) }
  var preferences by remember { mutableStateOf(original.preferences) }
  var restricted by remember { mutableStateOf(original.restricted) }
  var error by remember { mutableStateOf<String?>(null) }
  Modal("Profile & goals", onDismiss) {
    Field("Name", name, { name = it })
    Field("Age", age, { age = it }, true)
    Field("Height (cm)", height, { height = it }, true)
    Field("Reference weight (kg)", weight, { weight = it }, true)
    Field("Target weight (kg)", target, { target = it }, true)
    Choice("Energy-estimation parameter", listOf("male", "female"), sex, { sex = it })
    Choice("Goal", listOf("lose", "maintain", "gain"), goal, { goal = it })
    Field("Dietary preferences", preferences, { preferences = it })
    Row {
      Checkbox(restricted, { restricted = it })
      Text("Use manual targets for clinical or pregnancy/breastfeeding needs", Modifier.weight(1f))
    }
    Text(
      "Profile changes do not rewrite your calorie targets. Use Edit plan to review and save new targets."
    )
    ErrorText(error)
    Button(
      onClick = {
        try {
          val updated =
            original.copy(
              name = name,
              age = age.toInt(),
              heightCm = height.toDouble(),
              weightKg = weight.toDouble(),
              targetKg = target.toDouble(),
              sex = sex,
              goal = goal,
              preferences = preferences,
              restricted = restricted,
            )
          require(
            updated.age in 1..120 &&
              updated.heightCm in 80.0..250.0 &&
              updated.weightKg in 20.0..400.0 &&
              updated.targetKg in 20.0..400.0
          )
          vm.run { vm.store.update { it.copy(profile = updated) } }
          onDismiss()
        } catch (_: Exception) {
          error = "Check the age, height and weight values."
        }
      }
    ) {
      Text("Save profile")
    }
  }
}
