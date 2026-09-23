package be.calorietracker

import be.calorietracker.domain.*
import be.calorietracker.services.BackupCipher
import java.time.*
import kotlinx.serialization.json.Json
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
  fun mealTemplateCreatesFreshEntriesWithoutChangingOriginal() {
    val template = MealTemplate(name = "Breakfast", meal = "Breakfast", items = listOf(TemplateItem(food, 200.0, "g")))
    val first = template.entries("2026-09-21", "Breakfast").single()
    val second = template.entries("2026-09-22", "Lunch").single()
    assertNotEquals(first.id, second.id)
    assertEquals(300.0, second.nutrients.kcal!!, 0.0)
    assertEquals("2026-09-21", first.date)
    assertEquals("Lunch", second.meal)
  }

  @Test
  fun oldAppStateDecodesWithWaterAndTemplateDefaults() {
    val state = Json { ignoreUnknownKeys = true }.decodeFromString<AppState>("{\"schema\":1}")
    assertEquals(2000, state.waterGoalMl)
    assertEquals(listOf(250, 500), state.waterQuickAmountsMl)
    assertTrue(state.mealTemplates.isEmpty())
    assertFalse(state.fasting.enabled)
  }

  @Test
  fun fastingWindowHandlesOvernightAndBrusselsDst() {
    val brussels = ZoneId.of("Europe/Brussels")
    val overnight = FastingWindow(true, "20:00", "08:00")
    val evening = ZonedDateTime.of(2026, 9, 23, 22, 0, 0, 0, brussels)
    assertTrue(overnight.status(evening).canEat)
    assertEquals(LocalDate.of(2026, 9, 24), overnight.status(evening).nextTransition.toLocalDate())
    assertFalse(overnight.status(evening.withHour(12)).canEat)

    val spring = FastingWindow(true, "01:00", "03:30")
    val beforeJump = ZonedDateTime.of(2026, 3, 29, 1, 30, 0, 0, brussels)
    assertTrue(spring.status(beforeJump).canEat)
    assertEquals(60, Duration.between(beforeJump, spring.status(beforeJump).nextTransition).toMinutes())
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
  fun guidedTargetsUseConservativeRangesAndMetadata() {
    val loss = Profile(age = 35, heightCm = 170.0, weightKg = 80.0, goal = "lose")
    val maintenance = PlanCalculator.maintenance(loss)
    val gentle = PlanCalculator.target(loss, -.05)
    val assertive = PlanCalculator.target(loss, -.20, offsetKcal = -300.0)
    assertEquals(maintenance * .95, gentle.kcal, .001)
    assertEquals(maintenance * .80 - 300.0, assertive.kcal, .001)
    assertEquals(maintenance * .9, gentle.intent!!.maintenanceLowKcal, .001)
    assertEquals(maintenance * 1.1, gentle.intent!!.maintenanceHighKcal, .001)
    gentle.validate()
    assertive.validate()

    val gain = loss.copy(goal = "gain")
    assertEquals(
      PlanCalculator.maintenance(gain) * 1.025,
      PlanCalculator.target(gain, .025).kcal,
      .001,
    )
    assertEquals(
      PlanCalculator.maintenance(gain) * 1.10,
      PlanCalculator.target(gain, .10).kcal,
      .001,
    )
  }

  @Test
  fun guidedTargetRejectsOutsideRange() {
    val profile = Profile(age = 35, weightKg = 80.0, goal = "lose")
    assertThrows(IllegalArgumentException::class.java) {
      PlanCalculator.target(profile, -.21)
    }
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
  fun weeklyBoundaryIsMondayToSundayAcrossBrusselsDst() {
    val period = WeeklyEngine.previousPeriod(LocalDate.of(2026, 3, 30))
    assertEquals(LocalDate.of(2026, 3, 23), period.start)
    assertEquals(LocalDate.of(2026, 3, 29), period.endInclusive)
    val brussels = ZoneId.of("Europe/Brussels")
    assertEquals(
      167,
      Duration.between(
          period.start.atStartOfDay(brussels),
          period.endInclusive.plusDays(1).atStartOfDay(brussels),
        )
        .toHours(),
    )
  }

  @Test
  fun weeklyPromptRecursAndHonoursSnoozeAndSkip() {
    val date = LocalDate.of(2026, 9, 21)
    val profile = Profile(goal = "lose")
    val plan = PlanCalculator.suggest(profile).copy(effective = "2026-08-01")
    val state = AppState(profile = profile, plans = listOf(plan))
    val period = requireNotNull(WeeklyEngine.due(state, date))
    val snoozed =
      state.copy(
        weeklyCheckIns =
          listOf(
            WeeklyCheckIn(
              periodStart = period.start.toString(),
              periodEnd = period.endInclusive.toString(),
              status = "snoozed",
              completedAt = null,
              snoozedUntil = date.plusDays(1).toString(),
            )
          )
      )
    assertNull(WeeklyEngine.due(snoozed, date))
    assertNotNull(WeeklyEngine.due(snoozed, date.plusDays(1)))
    assertNull(
      WeeklyEngine.due(
        state.copy(
          weeklyCheckIns =
            listOf(
              WeeklyCheckIn(
                periodStart = period.start.toString(),
                periodEnd = period.endInclusive.toString(),
                status = "skipped",
              )
            )
        ),
        date,
      )
    )
    assertNotNull(WeeklyEngine.due(snoozed, date.plusWeeks(1)))
  }

  @Test
  fun weeklyReviewRequiresThreeCompleteWeeks() {
    val state = weeklyState(weeks = 1, weeklyWeightChange = 0.0, desiredWeeklyChange = -.5)
    val checkIn = weeklyCheckIn(1)
    val result = WeeklyEngine.evaluate(state, checkIn)
    assertFalse(result.sufficientEvidence)
    assertNull(result.proposedKcal)
    assertEquals("held", result.status)
  }

  @Test
  fun weeklyRecommendationUsesDeadbandAndCap() {
    val capped =
      WeeklyEngine.evaluate(
        weeklyState(weeks = 2, weeklyWeightChange = 0.0, desiredWeeklyChange = -.5),
        weeklyCheckIn(2),
      )
    assertEquals(1850.0, capped.proposedKcal!!, 0.0)

    val deadband =
      WeeklyEngine.evaluate(
        weeklyState(weeks = 2, weeklyWeightChange = .11, desiredWeeklyChange = 0.0),
        weeklyCheckIn(2),
      )
    assertNull(deadband.proposedKcal)
    assertEquals("held", deadband.status)
  }

  @Test
  fun weeklyReviewHoldsWhenActivityChangesSharply() {
    val base = weeklyState(weeks = 2, weeklyWeightChange = 0.0, desiredWeeklyChange = -.5)
    val health =
      (0L..20L).map { index ->
        val date = LocalDate.of(2026, 8, 30).minusDays(index)
        HealthDay(
          date = date.toString(),
          activeKcal = if (index <= 6) 500.0 else 200.0,
        )
      }
    val result = WeeklyEngine.evaluate(base.copy(health = health), weeklyCheckIn(2))
    assertNull(result.proposedKcal)
    assertTrue(result.reason.contains("Activity changed"))
  }

  @Test
  fun weeklyReviewHoldsAfterARecentPlanChange() {
    val base = weeklyState(weeks = 2, weeklyWeightChange = 0.0, desiredWeeklyChange = -.5)
    val recent =
      base.plans.single().copy(
        id = "recent-plan",
        effective = "2026-08-25",
        created = "2026-08-25T08:00:00Z",
      )
    val checkIn = weeklyCheckIn(2).copy(completedAt = "2026-08-31T08:00:00Z", zoneId = "Europe/Brussels")
    val result = WeeklyEngine.evaluate(base.copy(plans = base.plans + recent), checkIn)
    assertNull(result.proposedKcal)
    assertTrue(result.reason.contains("14 days"))
  }

  @Test
  fun weeklyReviewHoldsWhenWeightNoiseIsTooHigh() {
    val base = weeklyState(weeks = 2, weeklyWeightChange = 0.0, desiredWeeklyChange = -.5)
    val noisy =
      base.copy(
        measurements =
          listOf(0L to 80.0, 7L to 82.0, 14L to 78.0, 20L to 81.5).map { (day, value) ->
            Measurement(
              date = LocalDate.of(2026, 8, 10).plusDays(day).toString(),
              value = value,
            )
          }
      )
    val result = WeeklyEngine.evaluate(noisy, weeklyCheckIn(2))
    assertNull(result.proposedKcal)
    assertTrue(result.reason.contains("varied too much"))
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

  private fun weeklyCheckIn(index: Int): WeeklyCheckIn {
    val start = LocalDate.of(2026, 8, 10).plusWeeks(index.toLong())
    return WeeklyCheckIn(
      id = "week-$index",
      periodStart = start.toString(),
      periodEnd = start.plusDays(6).toString(),
      confirmedDates = (0L..3L).map { start.plusDays(it).toString() },
    )
  }

  private fun weeklyState(
    weeks: Int,
    weeklyWeightChange: Double,
    desiredWeeklyChange: Double,
  ): AppState {
    val profile = Profile(age = 35, weightKg = 80.0, goal = if (desiredWeeklyChange < 0) "lose" else "maintain")
    val plan =
      Plan(
        effective = "2026-07-01",
        created = "2026-07-01T00:00:00Z",
        kcal = 2000.0,
        protein = 100.0,
        fat = 80.0,
        carbs = 220.0,
        intent =
          PlanIntent(
            goal = profile.goal,
            desiredWeeklyKg = desiredWeeklyChange,
            maintenanceKcal = 2400.0,
          ),
      )
    val checkIns = (0 until weeks).map(::weeklyCheckIn)
    val entries =
      (checkIns + weeklyCheckIn(2)).flatMap { checkIn ->
        checkIn.confirmedDates.map { date ->
          Entry(
            date = date,
            food = Food(name = "Complete day", nutrients = Nutrients(kcal = 2000.0)),
            amount = 100.0,
          )
        }
      }
    val measurements =
      listOf(0L, 7L, 14L, 20L).map { day ->
        Measurement(
          date = LocalDate.of(2026, 8, 10).plusDays(day).toString(),
          value = 80.0 + weeklyWeightChange * day / 7.0,
        )
      }
    return AppState(
      profile = profile,
      plans = listOf(plan),
      entries = entries,
      measurements = measurements,
      weeklyCheckIns = checkIns,
    )
  }
}
