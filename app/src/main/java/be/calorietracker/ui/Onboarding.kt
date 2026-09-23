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
import kotlin.math.roundToInt

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
  Column(modifier.fillMaxSize()) {
  Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
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
  }
  Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)) {
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
  }
  if (review != null || manual)
    PlanEditor(
      review,
      profile = runCatching { profile() }.getOrNull(),
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
  profile: Profile? = null,
  onDismiss: () -> Unit,
  onSave: (Plan) -> Unit,
  initial: Boolean = false,
) {
  val eligible = profile?.let { it.age >= 18 && !it.restricted } == true
  if (!eligible) {
    ManualPlanEditor(plan, onDismiss, onSave, initial)
    return
  }
  val p = profile!!
  val maintenance = remember(p) { PlanCalculator.maintenance(p) }
  val initialFraction =
    plan?.intent?.let { (plan.kcal - it.guidedOffsetKcal) / maintenance - 1 }
      ?: when (p.goal) { "lose" -> -.10; "gain" -> .05; else -> 0.0 }
  var step by rememberSaveable { mutableIntStateOf(0) }
  var pacePosition by rememberSaveable {
    mutableFloatStateOf(
      when (p.goal) {
        "lose" -> ((-initialFraction - .05) / .15).toFloat().coerceIn(0f, 1f)
        "gain" -> ((initialFraction - .025) / .075).toFloat().coerceIn(0f, 1f)
        else -> 0f
      }
    )
  }
  var offset by rememberSaveable { mutableFloatStateOf(plan?.intent?.guidedOffsetKcal?.toFloat() ?: 0f) }
  var proteinPerKg by rememberSaveable {
    mutableFloatStateOf((plan?.protein?.div(p.weightKg) ?: 1.4).toFloat().coerceIn(1.2f, 2f))
  }
  var fatFraction by rememberSaveable {
    mutableFloatStateOf((plan?.fat?.times(9)?.div(plan.kcal) ?: .3).toFloat().coerceIn(.25f, .35f))
  }
  var advanced by rememberSaveable { mutableStateOf(false) }
  var directKcal by rememberSaveable { mutableStateOf(plan?.kcal?.fmt() ?: "") }
  var directProtein by rememberSaveable { mutableStateOf(plan?.protein?.fmt(1) ?: "") }
  var directFat by rememberSaveable { mutableStateOf(plan?.fat?.fmt(1) ?: "") }
  var date by rememberSaveable { mutableStateOf(today()) }
  var error by remember { mutableStateOf<String?>(null) }
  val fraction =
    when (p.goal) {
      "lose" -> -.05 - pacePosition * .15
      "gain" -> .025 + pacePosition * .075
      else -> 0.0
    }
  val guided =
    runCatching {
      PlanCalculator.target(
        p,
        fraction,
        offset.roundToInt().toDouble(),
        proteinPerKg.toDouble(),
        fatFraction.toDouble(),
        author = if (initial) "calculator" else "user",
        reason = if (initial) "Guided starting plan" else "Guided plan review",
        effective = date,
      )
    }.getOrNull()
  val preview =
    if (!advanced) guided
    else
      runCatching {
        val kcal = directKcal.toDouble()
        val protein = directProtein.toDouble()
        val fat = directFat.toDouble()
        Plan(
          effective = date,
          kcal = kcal,
          protein = protein,
          fat = fat,
          carbs = (kcal - protein * 4 - fat * 9) / 4,
          author = if (initial) "user" else "user",
          reason = "Advanced guided plan",
          intent = guided?.intent,
        ).also { it.validate() }
      }.getOrNull()
  ActionModal(if (initial) "Build your starting plan" else "Review your plan", onDismiss, action = {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      if (step > 0) OutlinedButton({ step-- }, Modifier.weight(1f)) { Text("Back") }
      Button(onClick = {
        if (step < 3) step++
        else try {
          val result = requireNotNull(preview) { "Check your target values." }
          result.validate()
          onSave(result)
        } catch (e: Exception) { error = e.message ?: "Check your target values." }
      }, modifier = Modifier.weight(1f)) { Text(if (step < 3) "Continue" else if (initial) "Start my diary" else "Save plan") }
    }
  }, fullScreen = true) {
    Text("Step ${step + 1} of 4", style = MaterialTheme.typography.labelLarge)
    LinearProgressIndicator({ (step + 1) / 4f }, Modifier.fillMaxWidth())
    when (step) {
      0 -> {
        Text("The facts behind your estimate", style = MaterialTheme.typography.headlineSmall)
        Panel {
          Text("${p.age} years · ${p.heightCm.fmt(0)} cm · ${p.weightKg.fmt(1)} kg")
          Text("${p.goal.replaceFirstChar { it.uppercase() }} weight · target ${p.targetKg.fmt(1)} kg")
          Text("Activity multiplier ${p.activity.fmt(1)}")
        }
        Text("We use these details only to estimate a starting range. Your real trend will become more useful over time.")
      }
      1 -> {
        Text("Choose a pace that feels doable", style = MaterialTheme.typography.headlineSmall)
        Panel(tint = MaterialTheme.colorScheme.primaryContainer) {
          Text("Estimated maintenance", style = MaterialTheme.typography.labelLarge)
          Text("${maintenance.fmt()} kcal", style = MaterialTheme.typography.displaySmall)
          Text("Likely range ${(.9 * maintenance).fmt()}–${(1.1 * maintenance).fmt()} kcal")
          Text("Mifflin–St Jeor × ${p.activity.fmt(1)}; real needs vary day to day.")
        }
        if (p.goal != "maintain") {
          Slider(pacePosition, { pacePosition = it }, valueRange = 0f..1f, steps = 5)
          val weekly = guided?.intent?.desiredWeeklyKg ?: 0.0
          Text("About ${kotlin.math.abs(weekly).fmt(2)} kg per week", style = MaterialTheme.typography.titleMedium)
          Text(if (pacePosition < .34f) "Slower, with more room for food." else if (pacePosition < .75f) "A steady middle ground." else "Faster, with less room for food.")
        } else Text("Maintenance keeps the target centred on your current estimate.")
      }
      2 -> {
        Text("Make the target feel practical", style = MaterialTheme.typography.headlineSmall)
        Text("${guided?.kcal.fmt()} kcal per day", style = MaterialTheme.typography.displaySmall)
        Text("Fine-tune in 50 kcal steps. This does not change your goal; it changes how assertively you approach it.")
        Slider(offset, { offset = (it / 50).roundToInt() * 50f }, valueRange = -300f..300f, steps = 11)
        Text(
          when {
            offset < 0 -> "${kotlin.math.abs(offset).toDouble().fmt()} kcal less: faster, with less flexibility."
            offset > 0 -> "${offset.toDouble().fmt()} kcal more: slower, with more flexibility."
            else -> "Using the calculated target."
          }
        )
        Text("Protein ${guided?.protein.fmt()} g")
        Slider(proteinPerKg, { proteinPerKg = it }, valueRange = 1.2f..2f, steps = 7)
        Text("Fat ${guided?.fat.fmt()} g")
        Slider(fatFraction, { fatFraction = it }, valueRange = .25f..35f, steps = 9)
        Text("Carbohydrates fill the remaining energy: ${guided?.carbs.fmt()} g")
        Row(verticalAlignment = Alignment.CenterVertically) {
          Switch(advanced, { advanced = it })
          Text("  Advanced exact targets")
        }
        if (advanced) {
          Text("Values outside the guided range carry more uncertainty. Macro energy must still match calories.", color = MaterialTheme.colorScheme.error)
          Field("Daily energy (kcal)", directKcal, { directKcal = it }, true)
          Field("Protein (g)", directProtein, { directProtein = it }, true)
          Field("Fat (g)", directFat, { directFat = it }, true)
        }
      }
      else -> {
        Text("Your plan preview", style = MaterialTheme.typography.headlineSmall)
        Panel(tint = MaterialTheme.colorScheme.secondaryContainer) {
          Text("${preview?.kcal.fmt()} kcal", style = MaterialTheme.typography.displaySmall)
          Text("Protein ${preview?.protein.fmt()} g · Carbs ${preview?.carbs.fmt()} g · Fat ${preview?.fat.fmt()} g")
          Text("Starts ${if (date == today()) "today" else date}")
        }
        Text("This is a starting point. Weekly check-ins will compare your complete diary days with your weight trend before suggesting any change.")
        TextButton(onClick = { advanced = true; step = 2 }) { Text("Use exact targets instead") }
        if (!initial) Field("Start date", date, { date = it })
      }
    }
    ErrorText(error)
  }
}

@Composable
private fun ManualPlanEditor(
  plan: Plan?,
  onDismiss: () -> Unit,
  onSave: (Plan) -> Unit,
  initial: Boolean,
) {
  var kcal by remember { mutableStateOf(plan?.kcal?.fmt() ?: "") }
  var protein by remember { mutableStateOf(plan?.protein?.fmt(1) ?: "") }
  var fat by remember { mutableStateOf(plan?.fat?.fmt(1) ?: "") }
  var error by remember { mutableStateOf<String?>(null) }
  Modal(if (initial) "Set your targets" else "Review your targets", onDismiss) {
    Text("Use targets agreed with a qualified professional. The app will track them without generating weight-management recommendations.")
    Field("Daily energy (kcal)", kcal, { kcal = it }, true)
    Field("Protein (g)", protein, { protein = it }, true)
    Field("Fat (g)", fat, { fat = it }, true)
    ErrorText(error)
    Button({
      try {
        val energy = kcal.toDouble()
        val p = protein.toDouble()
        val f = fat.toDouble()
        val value = Plan(kcal = energy, protein = p, fat = f, carbs = (energy - p * 4 - f * 9) / 4, reason = "Personalised manual targets")
        value.validate()
        onSave(value)
      } catch (e: Exception) { error = e.message ?: "Check the values." }
    }, Modifier.fillMaxWidth()) { Text(if (initial) "Start my diary" else "Save targets") }
  }
}
