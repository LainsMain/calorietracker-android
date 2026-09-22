package be.calorietracker.domain

import java.time.*
import java.util.UUID
import kotlin.math.abs
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
  fun suggest(p: Profile): Plan {
    require(p.age >= 18 && !p.restricted) {
      "Use a personalised professional target for this profile."
    }
    require(
      p.age <= 120 &&
        p.heightCm in 80.0..250.0 &&
        p.weightKg in 20.0..400.0 &&
        p.activity in 1.1..2.5
    )
    val bmr = 10 * p.weightKg + 6.25 * p.heightCm - 5 * p.age + if (p.sex == "male") 5 else -161
    val kcal =
      bmr *
        p.activity *
        when (p.goal) {
          "lose" -> if (p.pace == "slow") 0.95 else if (p.pace == "steady") 0.85 else 0.9
          "gain" -> if (p.pace == "slow") 1.025 else if (p.pace == "steady") 1.075 else 1.05
          else -> 1.0
        }
    val protein = p.weightKg * 1.4
    val fat = kcal * 0.3 / 9
    return Plan(
      kcal = kcal,
      protein = protein,
      fat = fat,
      carbs = (kcal - protein * 4 - fat * 9) / 4,
      author = "calculator",
      reason =
        "Mifflin–St Jeor × ${p.activity}; ${p.goal}; starting estimate, not a measured requirement",
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
data class Message(
  val id: String = newId(),
  val role: String,
  val text: String,
  val timestamp: String = now(),
  val zoneOffset: Int = offset(),
  val photoIds: List<String> = emptyList(),
  val kind: String = "message",
  val requestId: String? = null,
)

@Serializable
data class Summary(
  val id: String = newId(),
  val text: String,
  val messageIds: List<String>,
  val from: String,
  val to: String,
  val created: String = now(),
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
  val summaries: List<Summary> = emptyList(),
  val proposals: List<Proposal> = emptyList(),
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
    require(entries.map { it.id }.distinct().size == entries.size)
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
        val size = m.text.take(18000).length + 128
        (used + size <= budget).also { if (it) used += size }
      }
      .asReversed()
  }

  fun oldestBatch(messages: List<Message>, budget: Int = 70000): List<Message> {
    var used = 0
    return messages.takeWhile { m ->
      val size = m.text.length + 128
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
