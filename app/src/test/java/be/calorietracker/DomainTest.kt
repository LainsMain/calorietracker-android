package be.calorietracker

import be.calorietracker.domain.*
import be.calorietracker.services.BackupCipher
import java.time.*
import org.junit.Assert.*
import org.junit.Test

class DomainTest {
  private val food = Food(name = "Ingredient", nutrients = Nutrients(150.0, 10.0, 20.0, 3.0))

  @Test
  fun recipeUsesFinishedWeight() {
    val recipe =
      Recipe(name = "Batch", ingredients = listOf(Ingredient(food, 1000.0)), batchGrams = 1000.0)
    assertEquals(300.0, recipe.portion(200.0).kcal!!, 0.00001)
  }

  @Test
  fun evaporationChangesPortionDensity() {
    val recipe =
      Recipe(name = "Batch", ingredients = listOf(Ingredient(food, 1000.0)), batchGrams = 500.0)
    assertEquals(600.0, recipe.portion(200.0).kcal!!, 0.00001)
  }

  @Test
  fun editingRecipeDoesNotChangeOldLog() {
    val recipe =
      Recipe(name = "Batch", ingredients = listOf(Ingredient(food, 1000.0)), batchGrams = 1000.0)
    val entry = Entry(food = recipe.asFood(), amount = 200.0)
    val newer = recipe.copy(id = newId(), version = 2, batchGrams = 500.0)
    assertEquals(300.0, entry.nutrients.kcal!!, 0.0)
    assertEquals(600.0, newer.portion(200.0).kcal!!, 0.0)
  }

  @Test
  fun changedProviderDoesNotChangeSnapshot() {
    val entry = Entry(food = food, amount = 100.0)
    val changed = food.copy(nutrients = Nutrients(kcal = 900.0))
    assertEquals(150.0, entry.nutrients.kcal!!, 0.0)
    assertEquals(900.0, changed.nutrients.kcal!!, 0.0)
  }

  @Test
  fun missingNutrientsStayUnknown() {
    assertNull(Nutrients.total(listOf(Nutrients(kcal = 10.0), Nutrients(kcal = 20.0))).protein)
    assertNull(Nutrients.total(listOf(Nutrients(kcal = 10.0), Nutrients())).kcal)
    assertEquals(0.0, Nutrients.total(emptyList()).kcal!!, 0.0)
  }

  @Test(expected = IllegalArgumentException::class)
  fun volumeDoesNotAssumeDensity() {
    food.portion(100.0, "ml")
  }

  @Test
  fun volumeUsesExplicitDensity() {
    assertEquals(300.0, food.copy(density = 2.0).portion(100.0, "ml").kcal!!, 0.0)
  }

  @Test
  fun servingsUseDeclaredWeight() {
    assertEquals(90.0, food.copy(serving = 30.0).portion(2.0, "serving").kcal!!, 0.0)
  }

  @Test(expected = IllegalArgumentException::class)
  fun rejectsZeroRecipeWeight() {
    Recipe(name = "Bad", ingredients = listOf(Ingredient(food, 100.0)), batchGrams = 0.0)
      .portion(100.0)
  }

  @Test(expected = IllegalArgumentException::class)
  fun rejectsNaNPortion() {
    food.portion(Double.NaN, "g")
  }

  @Test(expected = IllegalArgumentException::class)
  fun rejectsNegativePortion() {
    food.portion(-1.0, "g")
  }

  @Test
  fun historicalPlanUsesEffectiveDate() {
    val old =
      Plan(effective = "2026-01-01", kcal = 2000.0, protein = 100.0, fat = 80.0, carbs = 220.0)
    val new = old.copy(id = newId(), effective = "2026-02-01", kcal = 1800.0, carbs = 170.0)
    val s = AppState(plans = listOf(old, new))
    assertEquals(old, s.plan("2026-01-15"))
    assertEquals(new, s.plan("2026-02-01"))
  }

  @Test(expected = IllegalArgumentException::class)
  fun macroEnergyMustReconcile() {
    Plan(kcal = 2000.0, protein = 1.0, fat = 1.0, carbs = 1.0).validate()
  }

  @Test
  fun estimateMatchesFormulaAndNoExerciseDoubleCount() {
    val profile =
      Profile(age = 30, heightCm = 175.0, weightKg = 75.0, activity = 1.4, goal = "lose")
    val p = PlanCalculator.suggest(profile)
    assertEquals((750 + 1093.75 - 150 + 5) * 1.4 * 0.9, p.kcal, 0.0001)
    p.validate()
  }

  @Test(expected = IllegalArgumentException::class)
  fun noAutomatedPlanForMinors() {
    PlanCalculator.suggest(Profile(age = 16))
  }

  @Test(expected = IllegalArgumentException::class)
  fun noAutomatedPlanForRestrictedProfile() {
    PlanCalculator.suggest(Profile(restricted = true))
  }

  @Test
  fun diaryDateSurvivesTimezoneChange() {
    val entry =
      Entry(
        date = "2026-03-29",
        food = food,
        amount = 100.0,
        created = "2026-03-28T23:30:00Z",
        zoneOffset = 3600,
      )
    assertEquals("2026-03-29", entry.date)
    val brussels = ZoneId.of("Europe/Brussels")
    val day = LocalDate.parse(entry.date)
    assertEquals(
      23,
      Duration.between(day.atStartOfDay(brussels), day.plusDays(1).atStartOfDay(brussels))
        .toHours(),
    )
  }

  @Test
  fun trendRequiresEnoughWeighIns() {
    val state = AppState(measurements = listOf(Measurement(value = 75.0)))
    assertTrue(Trends.weeklyWeights(state).all { it == null })
  }

  @Test
  fun backupRoundTripAndRandomisedEncryption() {
    val bytes = "private diary".toByteArray()
    val password = "correct horse battery".toCharArray()
    val a = BackupCipher.encrypt(bytes, password)
    val b = BackupCipher.encrypt(bytes, password)
    assertFalse(a.contentEquals(b))
    assertArrayEquals(bytes, BackupCipher.decrypt(a, password))
  }

  @Test
  fun backupRejectsTampering() {
    val encrypted = BackupCipher.encrypt("records".toByteArray(), "longpassword".toCharArray())
    encrypted[encrypted.lastIndex] = (encrypted.last().toInt() xor 1).toByte()
    assertThrows(Exception::class.java) {
      BackupCipher.decrypt(encrypted, "longpassword".toCharArray())
    }
  }

  @Test
  fun backupRejectsWrongPassword() {
    val encrypted = BackupCipher.encrypt("records".toByteArray(), "longpassword".toCharArray())
    assertThrows(Exception::class.java) {
      BackupCipher.decrypt(encrypted, "incorrect-password".toCharArray())
    }
  }

  @Test
  fun estimatedRecipeMassIncludesServings() {
    assertEquals(60.0, Ingredient(food.copy(serving = 30.0), 2.0, "serving").massGrams(), 0.0)
  }

  @Test(expected = IllegalArgumentException::class)
  fun estimatedRecipeMassRequiresLiquidDensity() {
    Ingredient(food.copy(basis = "ml"), 100.0, "ml").massGrams()
  }
}
