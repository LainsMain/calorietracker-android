package be.calorietracker.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.unit.dp
import be.calorietracker.domain.*

@Composable
fun Onboarding(vm: TrackerViewModel, s: AppState, modifier: Modifier) {
  var step by rememberSaveable { mutableIntStateOf(s.onboardingStep.coerceIn(0, 2)) }
  var name by rememberSaveable { mutableStateOf(s.draft.name) }
  var age by rememberSaveable { mutableStateOf(s.draft.age.toString()) }
  var height by rememberSaveable { mutableStateOf(s.draft.heightCm.toString()) }
  var weight by rememberSaveable { mutableStateOf(s.draft.weightKg.toString()) }
  var sex by rememberSaveable { mutableStateOf(s.draft.sex) }
  var activity by rememberSaveable { mutableStateOf(s.draft.activity) }
  var goal by rememberSaveable { mutableStateOf(s.draft.goal) }
  var pace by rememberSaveable { mutableStateOf(s.draft.pace) }
  var target by rememberSaveable { mutableStateOf(s.draft.targetKg.toString()) }
  var preferences by rememberSaveable { mutableStateOf(s.draft.preferences) }
  var restricted by rememberSaveable { mutableStateOf(s.draft.restricted) }
  var error by remember { mutableStateOf<String?>(null) }
  var review by remember { mutableStateOf<Plan?>(null) }
  var manual by remember { mutableStateOf(false) }
  fun profile() =
    Profile(
      name,
      age.toInt(),
      height.toDouble(),
      weight.toDouble(),
      sex,
      activity,
      goal,
      target.toDouble(),
      pace,
      preferences,
      restricted,
    )
  LaunchedEffect(
    name,
    age,
    height,
    weight,
    sex,
    activity,
    goal,
    target,
    pace,
    preferences,
    restricted,
    step,
  ) {
    kotlinx.coroutines.delay(400)
    runCatching { profile() }
      .getOrNull()
      ?.let { p -> vm.store.update { it.copy(draft = p, onboardingStep = step) } }
  }
  Column(
    modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
    verticalArrangement = Arrangement.spacedBy(18.dp),
  ) {
    Spacer(Modifier.height(20.dp))
    Icon(Icons.Rounded.Spa, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
    PageTitle(
      "A fresh start · ${step+1} of 3",
      when (step) {
        0 -> "Make room\nfor feeling good."
        1 -> "A plan that\nfits your life."
        else -> "Your starting\npoint, your way."
      },
    )
    LinearProgressIndicator(progress = { (step + 1) / 3f }, modifier = Modifier.fillMaxWidth())
    when (step) {
      0 -> {
        Text(
          "A thoughtful food diary, useful insights, and a coach who grows with you. Your records live on this device."
        )
        Field("What should we call you?", name, { name = it })
        Field("Age", age, { age = it }, true)
        Field("Height (cm)", height, { height = it }, true)
        Field("Current weight (kg)", weight, { weight = it }, true)
        Choice("Energy-estimation parameter", listOf("male", "female"), sex, { sex = it })
        Text(
          "Used only for the Mifflin–St Jeor starting estimate. You can set targets manually instead.",
          style = MaterialTheme.typography.bodySmall,
        )
      }
      1 -> {
        Choice("Your goal", listOf("lose", "maintain", "gain"), goal, { goal = it })
        Field("Target weight (kg)", target, { target = it }, true)
        Choice(
          "Typical activity",
          listOf("Mostly seated", "Lightly active", "Active", "Very active"),
          when (activity) {
            1.2 -> "Mostly seated"
            1.4 -> "Lightly active"
            1.6 -> "Active"
            else -> "Very active"
          },
          {
            activity =
              when (it) {
                "Mostly seated" -> 1.2
                "Lightly active" -> 1.4
                "Active" -> 1.6
                else -> 1.8
              }
          },
        )
        Choice("Preferred pace", listOf("slow", "gentle", "steady"), pace, { pace = it })
        Panel {
          Text("A measured starting pace", style = MaterialTheme.typography.titleMedium)
          Text(
            "Gentle starts 10% below maintenance for loss, 5% above for gain. Slow uses 5% / 2.5%; steady uses 15% / 7.5%. Exercise is not added again. Review and adjust your targets whenever you want."
          )
        }
        Field("Dietary preferences / foods to avoid", preferences, { preferences = it })
      }
      2 -> {
        Text(
          "This app provides general tracking, not medical care. For pregnancy, breastfeeding or conditions affecting nutrition, use targets agreed with your clinician."
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
          Checkbox(restricted, { restricted = it })
          Text(
            "Use manual targets for clinical or pregnancy/breastfeeding needs",
            Modifier.weight(1f),
          )
        }
        Panel {
          Text("Local by design", style = MaterialTheme.typography.titleMedium)
          Text(
            "No account or cloud backup. You can export an encrypted backup. AI is optional: selected context and photos go to DeepSeek only when you use the coach."
          )
        }
        Text("Defaults: English · grams · kilograms · kcal · Belgian food preference")
      }
    }
    ErrorText(error)
    Button(
      onClick = {
        try {
          val p = profile()
          require(
            p.age in 1..120 &&
              p.heightCm in 80.0..250.0 &&
              p.weightKg in 20.0..400.0 &&
              p.targetKg in 20.0..400.0
          ) {
            "Please check age, height and weight."
          }
          vm.run { vm.store.update { it.copy(draft = p) } }
          error = null
          if (step < 2) step++
          else if (p.age < 18 || p.restricted) manual = true else review = PlanCalculator.suggest(p)
        } catch (e: Exception) {
          error = e.message ?: "Check the numbers."
        }
      },
      modifier = Modifier.fillMaxWidth(),
    ) {
      Text(if (step < 2) "Continue" else "Review my targets")
    }
    if (step == 2)
      TextButton(
        onClick = {
          try {
            profile()
            manual = true
          } catch (_: Exception) {
            error = "Check your profile values."
          }
        }
      ) {
        Text("Set targets manually instead")
      }
    if (step > 0) TextButton(onClick = { step-- }) { Text("Back") }
  }
  if (review != null || manual)
    PlanEditor(
      review,
      onDismiss = {
        review = null
        manual = false
      },
      onSave = {
        vm.saveProfile(profile(), it)
        review = null
        manual = false
      },
      initial = true,
    )
}

@Composable
fun PlanEditor(
  plan: Plan?,
  onDismiss: () -> Unit,
  onSave: (Plan) -> Unit,
  initial: Boolean = false,
) {
  var kcal by remember {
    mutableStateOf(plan?.kcal?.let { "%.0f".format(java.util.Locale.ROOT, it) } ?: "")
  }
  var protein by remember {
    mutableStateOf(plan?.protein?.let { "%.1f".format(java.util.Locale.ROOT, it) } ?: "")
  }
  var fat by remember {
    mutableStateOf(plan?.fat?.let { "%.1f".format(java.util.Locale.ROOT, it) } ?: "")
  }
  var carbs by remember {
    mutableStateOf(plan?.carbs?.let { "%.1f".format(java.util.Locale.ROOT, it) } ?: "")
  }
  var reason by remember {
    mutableStateOf(if (initial) plan?.reason ?: "Manual starting targets" else "Manual adjustment")
  }
  var date by remember { mutableStateOf(today()) }
  var error by remember { mutableStateOf<String?>(null) }
  Modal(if (initial) "Your starting plan" else "Adjust your plan", onDismiss) {
    Text(
      "An estimate to learn from, not a fixed prescription. Every change is saved in your plan history."
    )
    Field("Daily energy (kcal)", kcal, { kcal = it }, true)
    Field("Protein (g)", protein, { protein = it }, true)
    Field("Fat (g)", fat, { fat = it }, true)
    Field("Carbohydrates (g)", carbs, { carbs = it }, true)
    TextButton(
      onClick = {
        try {
          carbs = ((kcal.toDouble() - protein.toDouble() * 4 - fat.toDouble() * 9) / 4).toString()
        } catch (_: Exception) {
          error = "Enter calories, protein and fat first."
        }
      }
    ) {
      Text("Calculate carbs from remaining energy")
    }
    Field("Effective date (YYYY-MM-DD)", date, { date = it })
    Field("Reason", reason, { reason = it })
    ErrorText(error)
    Button(
      onClick = {
        try {
          val p =
            Plan(
              effective = date,
              kcal = kcal.toDouble(),
              protein = protein.toDouble(),
              fat = fat.toDouble(),
              carbs = carbs.toDouble(),
              reason = reason,
            )
          p.validate()
          onSave(p)
        } catch (e: Exception) {
          error = e.message ?: "Check all target values."
        }
      },
      modifier = Modifier.fillMaxWidth(),
    ) {
      Text(if (initial) "Start my diary" else "Save new plan")
    }
  }
}
