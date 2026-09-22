package be.calorietracker.domain

import java.time.*
import java.time.temporal.TemporalAdjusters
import java.util.UUID
import kotlin.math.abs
import kotlin.math.round
import kotlinx.serialization.Serializable

fun newId() = UUID.randomUUID().toString()

fun now() = Instant.now().toString()

fun today() = LocalDate.now().toString()

fun offset() = OffsetDateTime.now().offset.totalSeconds

@Serializable
data class Nutrients(
  val kcal: Double? = null,
  val protein: Double? = null,
  val carbs: Double? = null,
  val fat: Double? = null,
  val saturated: Double? = null,
  val sugars: Double? = null,
  val fibre: Double? = null,
  val salt: Double? = null,
  val extra: Map<String, Double> = emptyMap(),
) {
  fun scale(factor: Double): Nutrients {
    require(factor.isFinite() && factor >= 0)
    return Nutrients(
      kcal?.times(factor),
      protein?.times(factor),
      carbs?.times(factor),
      fat?.times(factor),
      saturated?.times(factor),
      sugars?.times(factor),
      fibre?.times(factor),
      salt?.times(factor),
      extra.mapValues { it.value * factor },
    )
  }

  fun valid() =
    listOfNotNull(kcal, protein, carbs, fat, saturated, sugars, fibre, salt).all {
      it.isFinite() && it >= 0
    } && extra.values.all { it.isFinite() && it >= 0 }

  companion object {
    fun total(items: List<Nutrients>): Nutrients {
      fun sum(f: (Nutrients) -> Double?): Double? =
        if (items.any { f(it) == null }) null else items.sumOf { f(it)!! }
      return Nutrients(
        sum { it.kcal },
        sum { it.protein },
        sum { it.carbs },
        sum { it.fat },
        sum { it.saturated },
        sum { it.sugars },
        sum { it.fibre },
        sum { it.salt },
      )
    }
  }
}

@Serializable
data class Profile(
  val name: String = "",
  val age: Int = 30,
  val heightCm: Double = 175.0,
  val weightKg: Double = 75.0,
  val sex: String = "male",
  val activity: Double = 1.4,
  val goal: String = "maintain",
  val targetKg: Double = 75.0,
  val pace: String = "gentle",
  val preferences: String = "",
  val restricted: Boolean = false,
)

@Serializable
data class PlanIntent(
  val goal: String,
  val desiredWeeklyKg: Double,
  val maintenanceKcal: Double,
  val maintenanceLowKcal: Double = maintenanceKcal * .9,
  val maintenanceHighKcal: Double = maintenanceKcal * 1.1,
  val guidedOffsetKcal: Double = 0.0,
  val calculationVersion: Int = 2,
)

@Serializable
data class Plan(
  val id: String = newId(),
  val effective: String = today(),
  val created: String = now(),
  val kcal: Double,
  val protein: Double,
  val fat: Double,
  val carbs: Double,
  val author: String = "user",
  val reason: String = "Manual adjustment",
  val intent: PlanIntent? = null,
) {
  fun validate() {
    require(listOf(kcal, protein, fat, carbs).all { it.isFinite() && it >= 0 })
    require(kcal > 0)
    require(abs(kcal - (protein * 4 + fat * 9 + carbs * 4)) <= 5) {
      "Macro energy must match calories (within 5 kcal)."
    }
    LocalDate.parse(effective)
  }
}

object PlanCalculator {
  fun maintenance(p: Profile): Double {
    require(
      p.age in 18..120 &&
        !p.restricted &&
        p.heightCm in 80.0..250.0 &&
        p.weightKg in 20.0..400.0 &&
        p.activity in 1.1..2.5
    ) {
      "Use personalised professional targets for this profile."
    }
    val bmr = 10 * p.weightKg + 6.25 * p.heightCm - 5 * p.age + if (p.sex == "male") 5 else -161
    return bmr * p.activity
  }

  fun target(
    p: Profile,
    adjustmentFraction: Double,
    offsetKcal: Double = 0.0,
    proteinPerKg: Double = 1.4,
    fatFraction: Double = .3,
    author: String = "calculator",
    reason: String = "Guided plan",
    effective: String = today(),
  ): Plan {
    val maintenance = maintenance(p)
    val allowed =
      when (p.goal) {
        "lose" -> -.20..-.05
        "gain" -> .025..0.10
        else -> 0.0..0.0
      }
    require(adjustmentFraction in allowed) { "Choose a target inside the guided range." }
    require(offsetKcal.isFinite() && offsetKcal in -300.0..300.0)
    val kcal = maintenance * (1 + adjustmentFraction) + offsetKcal
    val protein = p.weightKg * proteinPerKg
    val fat = kcal * fatFraction / 9
    val carbs = (kcal - protein * 4 - fat * 9) / 4
    require(carbs >= 0) { "These macro targets leave no room for carbohydrates." }
    val desiredWeeklyKg = adjustmentFraction * maintenance * 7 / 7700
    return Plan(
      effective = effective,
      kcal = kcal,
      protein = protein,
      fat = fat,
      carbs = carbs,
      author = author,
      reason = reason,
      intent =
        PlanIntent(
          goal = p.goal,
          desiredWeeklyKg = desiredWeeklyKg,
          maintenanceKcal = maintenance,
          guidedOffsetKcal = offsetKcal,
        ),
    )
  }

  fun suggest(p: Profile): Plan {
    val fraction =
      when (p.goal) {
        "lose" -> if (p.pace == "slow") -.05 else if (p.pace == "steady") -.15 else -.10
        "gain" -> if (p.pace == "slow") .025 else if (p.pace == "steady") .075 else .05
        else -> 0.0
      }
    return target(
      p,
      fraction,
      reason =
        "Mifflin–St Jeor × ${p.activity}; ${p.goal}; guided starting estimate with uncertainty",
    )
  }
}

@Serializable
data class Food(
  val id: String = newId(),
  val name: String,
  val brand: String = "",
  val barcode: String? = null,
  val basis: String = "g",
  val nutrients: Nutrients,
  val serving: Double? = null,
  val density: Double? = null,
  val source: String = "Custom",
  val sourceUrl: String = "",
  val aliases: String = "",
  val favourite: Boolean = false,
  val lastUsed: String? = null,
) {
  fun portion(amount: Double, unit: String): Nutrients {
    require(amount.isFinite() && amount > 0)
    val baseAmount =
      when {
        unit == basis -> amount
        unit == "serving" -> amount * requireNotNull(serving) { "Set a serving size first." }
        unit == "g" && basis == "ml" ->
          amount /
            requireNotNull(density) { "Density is required to convert grams to millilitres." }
        unit == "ml" && basis == "g" ->
          amount *
            requireNotNull(density) { "Density is required to convert millilitres to grams." }
        else -> error("Unsupported unit")
      }
    return nutrients.scale(baseAmount / 100)
  }
}

@Serializable data class Ingredient(val food: Food, val amount: Double, val unit: String = "g")

@Serializable
data class Recipe(
  val id: String = newId(),
  val familyId: String = newId(),
  val version: Int = 1,
  val name: String,
  val ingredients: List<Ingredient>,
  val batchGrams: Double,
  val servings: Double? = null,
  val estimatedWeight: Boolean = false,
  val photoId: String? = null,
  val favourite: Boolean = false,
  val created: String = now(),
) {
  fun total() = Nutrients.total(ingredients.map { it.food.portion(it.amount, it.unit) })

  fun portion(grams: Double): Nutrients {
    require(batchGrams.isFinite() && batchGrams > 0 && grams.isFinite() && grams > 0)
    return total().scale(grams / batchGrams)
  }

  fun asFood() =
    Food(
      id = "recipe:$id",
      name = name,
      nutrients = portion(100.0),
      source = "Recipe v$version",
      serving = servings?.let { batchGrams / it },
    )
}

@Serializable
data class Entry(
  val id: String = newId(),
  val date: String = today(),
  val meal: String = "Breakfast",
  val food: Food,
  val amount: Double,
  val unit: String = "g",
  val nutrients: Nutrients = food.portion(amount, unit),
  val note: String = "",
  val created: String = now(),
  val zoneOffset: Int = offset(),
)

@Serializable
data class Measurement(
  val id: String = newId(),
  val date: String = today(),
  val type: String = "Weight",
  val value: Double,
  val unit: String = "kg",
  val source: String = "Manual",
  val sourceId: String? = null,
)

@Serializable
data class Photo(
  val id: String = newId(),
  val date: String = today(),
  val caption: String = "",
  val kind: String = "progress",
)

@Serializable
data class HealthDay(
  val date: String,
  val steps: Long? = null,
  val activeKcal: Double? = null,
  val totalKcal: Double? = null,
  val distanceMetres: Double? = null,
  val workouts: List<Workout> = emptyList(),
  val origins: List<String> = emptyList(),
  val synced: String = now(),
)

@Serializable
data class WeeklyCheckIn(
  val id: String = newId(),
  val periodStart: String,
  val periodEnd: String,
  val confirmedDates: List<String> = emptyList(),
  val weightMeasurementId: String? = null,
  val weightKg: Double? = null,
  val note: String = "",
  val status: String = "completed",
  val completedAt: String? = now(),
  val zoneId: String = ZoneId.systemDefault().id,
  val snoozedUntil: String? = null,
  val recommendationId: String? = null,
)

@Serializable
data class WeeklyRecommendation(
  val id: String = newId(),
  val checkInId: String,
  val created: String = now(),
  val sufficientEvidence: Boolean,
  val confirmedDayCount: Int,
  val averageIntakeKcal: Double? = null,
  val weightSlopeKgPerWeek: Double? = null,
  val estimatedMaintenanceKcal: Double? = null,
  val activityChangePercent: Double? = null,
  val currentKcal: Double,
  val proposedKcal: Double? = null,
  val reason: String,
  val appliedPlanId: String? = null,
  val status: String = "pending",
)

@Serializable
data class Message(
  val id: String = newId(),
  val role: String,
  val text: String,
  val timestamp: String = now(),
  val zoneOffset: Int = offset(),
  val photoIds: List<String> = emptyList(),
  val kind: String = "message",
  val requestId: String? = null,
  val providerReasoning: String? = null,
  val contextKind: String? = null,
  val contextId: String? = null,
  val conversationId: String = "default",
)

@Serializable
data class Conversation(
  val id: String = newId(),
  val title: String = "New chat",
  val created: String = now(),
)

@Serializable
data class Summary(
  val id: String = newId(),
  val text: String,
  val messageIds: List<String>,
  val from: String,
  val to: String,
  val created: String = now(),
  val conversationId: String = "default",
)

@Serializable
data class Proposal(
  val id: String = newId(),
  val type: String,
  val payload: String,
  val explanation: String,
  val status: String = "pending",
  val created: String = now(),
  val appliedIds: List<String> = emptyList(),
  val requestId: String? = null,
  val conversationId: String = "default",
)

@Serializable
data class Water(val id: String = newId(), val date: String = today(), val ml: Int = 250)

@Serializable
data class AppState(
  val schema: Int = 1,
  val profile: Profile? = null,
  val draft: Profile = Profile(),
  val onboardingStep: Int = 0,
  val plans: List<Plan> = emptyList(),
  val foods: List<Food> = emptyList(),
  val entries: List<Entry> = emptyList(),
  val recipes: List<Recipe> = emptyList(),
  val measurements: List<Measurement> = emptyList(),
  val photos: List<Photo> = emptyList(),
  val health: List<HealthDay> = emptyList(),
  val messages: List<Message> = emptyList(),
  val conversations: List<Conversation> = emptyList(),
  val activeConversationId: String = "default",
  val summaries: List<Summary> = emptyList(),
  val proposals: List<Proposal> = emptyList(),
  val weeklyCheckIns: List<WeeklyCheckIn> = emptyList(),
  val weeklyRecommendations: List<WeeklyRecommendation> = emptyList(),
  val water: List<Water> = emptyList(),
  val meals: List<String> = listOf("Breakfast", "Lunch", "Dinner", "Snacks"),
) {
  fun plan(date: String = today()) =
    plans
      .filter { it.effective <= date }
      .maxWithOrNull(compareBy<Plan> { it.effective }.thenBy { it.created })

  fun totals(date: String = today()) =
    Nutrients.total(entries.filter { it.date == date }.map { it.nutrients })

  fun latestRecipes() =
    recipes.groupBy { it.familyId }.values.map { versions -> versions.maxBy { it.version } }

  fun validate() {
    require(schema == 1) { "Unsupported backup version" }
    plans.forEach { it.validate() }
    entries.forEach {
      LocalDate.parse(it.date)
      require(it.amount.isFinite() && it.amount > 0 && it.nutrients.valid())
      require(it.nutrients == it.food.portion(it.amount, it.unit)) { "Invalid nutrition snapshot" }
    }
    recipes.forEach {
      require(it.ingredients.isNotEmpty() && it.batchGrams > 0 && it.batchGrams.isFinite())
      it.portion(100.0)
    }
    measurements.forEach {
      LocalDate.parse(it.date)
      require(it.value.isFinite() && it.value > 0)
    }
    photos.forEach {
      require(it.id.matches(Regex("[a-zA-Z0-9-]+")))
      LocalDate.parse(it.date)
    }
    weeklyCheckIns.forEach {
      val start = LocalDate.parse(it.periodStart)
      val end = LocalDate.parse(it.periodEnd)
      require(!end.isBefore(start) && it.confirmedDates.all { d -> LocalDate.parse(d) in start..end })
      require(it.weightKg == null || it.weightKg.isFinite() && it.weightKg > 0)
    }
    require(entries.map { it.id }.distinct().size == entries.size)
  }
}

object WeeklyEngine {
  fun previousPeriod(date: LocalDate = LocalDate.now()): ClosedRange<LocalDate> {
    val thisMonday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    return thisMonday.minusDays(7)..thisMonday.minusDays(1)
  }

  fun due(state: AppState, date: LocalDate = LocalDate.now()): ClosedRange<LocalDate>? {
    val period = previousPeriod(date)
    val existing = state.weeklyCheckIns.lastOrNull { it.periodStart == period.start.toString() }
    if (existing?.status in listOf("completed", "skipped")) return null
    if (existing?.snoozedUntil?.let { LocalDate.parse(it).isAfter(date) } == true) return null
    val planStarted = state.plans.any { LocalDate.parse(it.effective) <= period.endInclusive }
    return period.takeIf { state.profile != null && planStarted }
  }

  fun evaluate(state: AppState, checkIn: WeeklyCheckIn): WeeklyRecommendation {
    val reviewDate =
      checkIn.completedAt
        ?.let {
          runCatching {
              Instant.parse(it).atZone(ZoneId.of(checkIn.zoneId)).toLocalDate()
            }
            .getOrNull()
        }
        ?: LocalDate.parse(checkIn.periodEnd).plusDays(1)
    val current = state.plan(reviewDate.toString()) ?: error("No active plan")
    val completed =
      (state.weeklyCheckIns.filter { it.status == "completed" } + checkIn)
        .distinctBy { it.id }
        .sortedByDescending { it.periodEnd }
        .take(4)
        .sortedBy { it.periodStart }
    val usableWeeks = completed.filter { it.confirmedDates.size >= 4 }
    val confirmed = usableWeeks.flatMap { it.confirmedDates }.distinct().sorted()
    val intake =
      confirmed.mapNotNull { date ->
        state.entries.filter { it.date == date }.takeIf { it.isNotEmpty() }?.let { entries ->
          Nutrients.total(entries.map { it.nutrients }).kcal
        }
      }
    val start = usableWeeks.firstOrNull()?.periodStart
    val end = usableWeeks.lastOrNull()?.periodEnd
    val weights =
      if (start == null || end == null) emptyList()
      else
        state.measurements
          .filter { it.type == "Weight" && it.date in start..end }
          .groupBy { it.date }
          .map { (date, values) -> LocalDate.parse(date) to values.last().value }
          .sortedBy { it.first }
    val coveredWeightWeeks =
      usableWeeks.count { week ->
        weights.any { it.first.toString() in week.periodStart..week.periodEnd }
      }
    val slope = robustWeeklySlope(weights)
    val averageIntake = intake.takeIf { it.isNotEmpty() }?.average()
    val activityChange = activityChange(state, checkIn)
    val recentPlan =
      state.plans.any {
        val effective = LocalDate.parse(it.effective)
        effective > reviewDate.minusDays(14) && effective <= reviewDate
      }
    val baseEvidence =
      usableWeeks.size >= 3 &&
        usableWeeks.takeLast(3).all { it.confirmedDates.size >= 4 } &&
        coveredWeightWeeks >= 3 &&
        weights.size >= 4 &&
        intake.size >= 12 &&
        averageIntake != null &&
        slope != null
    val holdReason =
      when {
        !baseEvidence ->
          "Keep your current target while we build a stable three-week picture from complete days and weigh-ins."
        recentPlan -> "Keep this target for at least 14 days so the last change has time to show in the trend."
        weightNoise(weights, slope) > .5 ->
          "Weight readings varied too much around the trend this time, so the app is holding your target until the signal is clearer."
        activityChange != null && abs(activityChange) > 40 ->
          "Activity changed a lot this week, so the scale trend is not yet a clean signal for changing food targets."
        else -> null
      }
    if (holdReason != null)
      return WeeklyRecommendation(
        checkInId = checkIn.id,
        sufficientEvidence = baseEvidence,
        confirmedDayCount = confirmed.size,
        averageIntakeKcal = averageIntake,
        weightSlopeKgPerWeek = slope,
        activityChangePercent = activityChange,
        currentKcal = current.kcal,
        reason = holdReason,
        status = "held",
      )
    val desired =
      current.intent?.desiredWeeklyKg
        ?: state.profile?.let { p -> (current.kcal - PlanCalculator.maintenance(p)) * 7 / 7700 }
        ?: 0.0
    if (abs(slope!! - desired) <= maxOf(.1, abs(desired) * .25))
      return WeeklyRecommendation(
        checkInId = checkIn.id,
        sufficientEvidence = true,
        confirmedDayCount = confirmed.size,
        averageIntakeKcal = averageIntake,
        weightSlopeKgPerWeek = slope,
        estimatedMaintenanceKcal = averageIntake!! - slope * 7700 / 7,
        activityChangePercent = activityChange,
        currentKcal = current.kcal,
        reason = "Your measured trend is close to the pace you selected. Keep the current target.",
        status = "held",
      )
    val maintenance = averageIntake!! - slope * 7700 / 7
    val observedTarget = maintenance + desired * 7700 / 7
    val blended = current.kcal + (observedTarget - current.kcal) * .5
    var delta = (blended - current.kcal).coerceIn(-150.0, 150.0)
    delta = round(delta / 25) * 25
    if (abs(delta) < 75) delta = 0.0
    val proposed = (current.kcal + delta).takeIf { delta != 0.0 }
    return WeeklyRecommendation(
      checkInId = checkIn.id,
      sufficientEvidence = true,
      confirmedDayCount = confirmed.size,
      averageIntakeKcal = averageIntake,
      weightSlopeKgPerWeek = slope,
      estimatedMaintenanceKcal = maintenance,
      activityChangePercent = activityChange,
      currentKcal = current.kcal,
      proposedKcal = proposed,
      reason =
        if (proposed == null) "The evidence suggests only a very small difference, so keep the current target."
        else "Your confirmed intake and multi-week weight trend support a conservative ${abs(delta).toInt()} kcal adjustment.",
      status = if (proposed == null) "held" else "pending",
    )
  }

  private fun robustWeeklySlope(weights: List<Pair<LocalDate, Double>>): Double? {
    if (weights.size < 4) return null
    val slopes =
      weights.flatMapIndexed { i, a ->
        weights.drop(i + 1).mapNotNull { b ->
          val days = Duration.between(a.first.atStartOfDay(), b.first.atStartOfDay()).toDays()
          if (days == 0L) null else (b.second - a.second) / days * 7
        }
      }.sorted()
    return slopes.takeIf { it.isNotEmpty() }?.let { it[it.size / 2] }
  }

  private fun weightNoise(weights: List<Pair<LocalDate, Double>>, weeklySlope: Double): Double {
    if (weights.size < 4) return Double.POSITIVE_INFINITY
    val origin = weights.first().first
    val residuals =
      weights
        .map { (date, value) ->
          value - Duration.between(origin.atStartOfDay(), date.atStartOfDay()).toDays() * weeklySlope / 7
        }
        .sorted()
    val median = residuals[residuals.size / 2]
    return residuals.map { abs(it - median) }.sorted().let { it[it.size / 2] }
  }

  private fun activityChange(state: AppState, checkIn: WeeklyCheckIn): Double? {
    fun average(from: LocalDate, to: LocalDate): Double? =
      state.health
        .filter { LocalDate.parse(it.date) in from..to }
        .mapNotNull { it.activeKcal }
        .takeIf { it.isNotEmpty() }
        ?.average()
    val end = LocalDate.parse(checkIn.periodEnd)
    val current = average(end.minusDays(6), end) ?: return null
    val previous = average(end.minusDays(20), end.minusDays(7)) ?: return null
    return if (previous <= 0) null else (current - previous) / previous * 100
  }
}

object Trends {
  fun weeklyWeights(state: AppState, end: LocalDate = LocalDate.now()): List<Double?> =
    (2 downTo 0).map { week ->
      val stop = end.minusDays((week * 7).toLong())
      val start = stop.minusDays(6)
      val samples =
        state.measurements
          .filter { it.type == "Weight" && LocalDate.parse(it.date) in start..stop }
          .groupBy { it.date }
          .values
          .map { day -> day.last().value }
      if (samples.size >= 3) samples.average() else null
    }

  fun plateauEvidence(state: AppState): String {
    val weeks = weeklyWeights(state)
    val days = (0..20).map { LocalDate.now().minusDays(it.toLong()).toString() }
    val logged = days.count { d -> state.entries.any { it.date == d } }
    return "Weekly weight averages (oldest first): $weeks; days with any food logged: $logged/21. A logged day does not guarantee complete intake. Require at least 3 weights/week and discuss completeness, adherence, activity, fluid shifts and recent plan changes before proposing an adjustment."
  }
}

/**
 * Bounded context without deleting original messages. Limits are conservative character budgets.
 */
object ChatContext {
  fun recent(messages: List<Message>, budget: Int = 48000): List<Message> {
    var used = 0
    return messages
      .asReversed()
      .take(50)
      .takeWhile { m ->
        val size = m.text.take(18000).length + m.providerReasoning.orEmpty().take(50000).length + 128
        (used + size <= budget).also { if (it) used += size }
      }
      .asReversed()
  }

  fun oldestBatch(messages: List<Message>, budget: Int = 70000): List<Message> {
    var used = 0
    return messages.takeWhile { m ->
      val size = m.text.length + m.providerReasoning.orEmpty().length + 128
      (used + size <= budget).also { if (it) used += size }
    }
  }
}

@Serializable
data class Workout(
  val id: String,
  val title: String,
  val type: Int,
  val start: String,
  val end: String,
  val source: String,
  val typeName: String = "Workout",
  val sourceLabel: String = "Health Connect",
  val distanceMetres: Double? = null,
  val activeKcal: Double? = null,
)

/**
 * Preserve repeated workouts; collapse only overlapping equivalent sessions mirrored by sources.
 */
fun deduplicateWorkouts(workouts: List<Workout>): List<Workout> {
  val kept = mutableListOf<Workout>()
  workouts
    .distinctBy { it.id }
    .sortedWith(compareBy<Workout> { it.start }.thenBy { it.source })
    .forEach { candidate ->
      val a = Instant.parse(candidate.start)
      val b = Instant.parse(candidate.end)
      if (
        kept.none { existing ->
          existing.type == candidate.type &&
            existing.source != candidate.source &&
            kotlin.math.abs(Duration.between(Instant.parse(existing.start), a).seconds) < 60 &&
            kotlin.math.abs(Duration.between(Instant.parse(existing.end), b).seconds) < 60
        }
      )
        kept += candidate
    }
  return kept
}

fun Ingredient.massGrams(): Double =
  when (unit) {
    "g" -> amount
    "ml" ->
      amount *
        requireNotNull(food.density) {
          "Weigh the finished batch: ${food.name} has no known density."
        }
    "serving" ->
      amount *
        requireNotNull(food.serving) *
        if (food.basis == "ml")
          requireNotNull(food.density) {
            "Weigh the finished batch: ${food.name} has no known density."
          }
        else 1.0
    else -> error("Unsupported ingredient unit")
  }
