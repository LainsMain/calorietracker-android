package be.calorietracker

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import be.calorietracker.domain.*
import be.calorietracker.data.codec
import be.calorietracker.services.WorkerServices
import dagger.hilt.android.EntryPointAccessors
import java.time.LocalDate
import java.time.Instant
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import org.junit.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UiInstrumentedTest {
  @get:Rule val compose = createAndroidComposeRule<MainActivity>()

  @After
  fun clear() = runBlocking {
    val store =
      EntryPointAccessors.fromApplication(compose.activity.application, WorkerServices::class.java)
        .store()
    store.load()
    store.clear()
  }

  @Test
  fun dashboardAndNavigationAreUsable() {
    val store =
      EntryPointAccessors.fromApplication(compose.activity.application, WorkerServices::class.java)
        .store()
    runBlocking {
      store.load()
      val profile = Profile(name = "Alex", goal = "lose", weightKg = 78.0, targetKg = 72.0)
      val oats =
        Food(
          name = "Overnight oats with berries",
          nutrients = Nutrients(130.0, 7.0, 18.0, 3.3, 0.5, 5.0, 3.0, 0.1),
        )
      val lunch =
        Food(
          name = "Chicken & roasted vegetable bowl",
          nutrients = Nutrients(155.0, 13.0, 15.0, 4.8, 0.7, 3.0, 2.0, 0.3),
        )
      store.update {
        AppState(
          profile = profile,
          plans = listOf(PlanCalculator.suggest(profile)),
          foods = listOf(oats.copy(lastUsed = now()), lunch),
          entries =
            listOf(
              Entry(food = oats, amount = 250.0, meal = "breakfast"),
              Entry(food = lunch, amount = 350.0, meal = "Lunch"),
            ),
          measurements =
            (0L..20).map {
              Measurement(
                date = LocalDate.now().minusDays(it).toString(),
                value = 76.8 + it * 0.045,
              )
            },
          health =
            listOf(
              HealthDay(
                today(),
                steps = 6428,
                activeKcal = 286.0,
                distanceMetres = 5400.0,
                workouts =
                  listOf(
                    Workout(
                      id = "run",
                      title = "Morning run",
                      type = 56,
                      start = Instant.now().minusSeconds(2400).toString(),
                      end = Instant.now().minusSeconds(600).toString(),
                      source = "com.google.android.apps.fitness",
                      typeName = "Run",
                      sourceLabel = "Google Fit",
                      distanceMetres = 5100.0,
                      activeKcal = 274.0,
                    )
                  ),
              )
            ),
          messages =
            listOf(
              Message(role = "user", text = "How is my week looking?", requestId = "coach-request"),
              Message(
                role = "assistant",
                text = "## Weekly focus\n- Keep logging complete days\n- Your **morning run** adds useful context.",
                requestId = "coach-request",
              ),
              Message(role = "assistant", text = "I prepared one reviewed change.", requestId = "coach-request"),
            ),
          proposals =
            listOf(
              Proposal(
                id = "ui-plan-proposal",
                type = "plan",
                payload =
                  codec.encodeToString(
                    Plan(
                      kcal = 1950.0,
                      protein = 100.0,
                      fat = 70.0,
                      carbs = 230.0,
                      reason = "A reviewed UI test change",
                    )
                  ),
                explanation = "A reviewed UI test change",
                requestId = "coach-request",
              )
            ),
          water = listOf(Water(ml = 1250)),
        )
      }
    }
    compose.waitUntil(15000) {
      compose.onAllNodesWithText("YOUR DAILY ENERGY").fetchSemanticsNodes().isNotEmpty()
    }
    snapshot("today")
    compose.onNodeWithTag("today-list").performScrollToNode(hasText("Morning run"))
    compose.onNodeWithText("Morning run").assertIsDisplayed()
    compose.onNodeWithText("Google Fit").performScrollTo().assertIsDisplayed()
    compose.onNodeWithText("Diary").performClick()
    compose.onNodeWithText("Food diary").assertIsDisplayed()
    snapshot("diary")
    compose.onNodeWithTag("diary-list").performScrollToNode(hasText("Overnight oats with berries"))
    compose.onNodeWithText("Overnight oats with berries").assertIsDisplayed()
    compose.onNodeWithText("Recipes").performClick()
    compose.onNodeWithText("Made by you").assertIsDisplayed()
    compose.onNodeWithText("Progress").performClick()
    compose.onNodeWithText("Your progress").assertIsDisplayed()
    snapshot("progress")
    compose.onNodeWithText("Coach").performClick()
    compose.onNodeWithText("Your coach").assertIsDisplayed()
    compose.waitUntil(5000) {
      compose.onAllNodesWithText("I prepared one reviewed change.").fetchSemanticsNodes().isNotEmpty()
    }
    compose.onNodeWithText("I prepared one reviewed change.").assertIsDisplayed()
    compose.onNodeWithTag("coach-list").performScrollToNode(hasText("Ready for your review"))
    compose.onAllNodesWithText("Ready for your review").assertCountEquals(1)
    compose.onNodeWithText("Apply").performScrollTo().performClick()
    compose.waitUntil(5000) {
      store.state.value.proposals.single { it.id == "ui-plan-proposal" }.status == "applied"
    }
    compose.onNodeWithText("Undo").performScrollTo().performClick()
    compose.waitUntil(5000) {
      store.state.value.proposals.single { it.id == "ui-plan-proposal" }.status == "undone"
    }
    compose.onNodeWithContentDescription("Take photo").assertExists()
    compose.onNodeWithContentDescription("New chat").performClick()
    compose.waitUntil(5000) { store.state.value.activeConversationId != "default" }
    compose.onNodeWithText("Let's find what works for you").performScrollTo().assertIsDisplayed()
    compose.onNodeWithContentDescription("Chat history").performClick()
    compose.onNodeWithText("How is my week looking?").performClick()
    compose.waitUntil(5000) { store.state.value.activeConversationId == "default" }
    compose.onNodeWithText("I prepared one reviewed change.").assertIsDisplayed()
    snapshot("coach")
    compose.onNodeWithText("Today").performClick()
    compose.waitUntil(5000) {
      compose.onAllNodesWithContentDescription("Log food").fetchSemanticsNodes().isNotEmpty()
    }
    compose.onNodeWithContentDescription("Log food").performClick()
    compose.onNodeWithText("Find your food").assertIsDisplayed()
    compose.onNodeWithText("Create food").performClick()
    compose.onNodeWithText("Food name").performTextInput("Test custom food")
    compose.onNodeWithText("Energy (kcal)").performTextInput("120")
    compose.onNodeWithText("Save food").performScrollTo().performClick()
    compose.onNodeWithText("Log food").performClick()
    compose.waitUntil(5000) { store.state.value.entries.any { it.food.name == "Test custom food" } }
    compose.onNodeWithText("Diary").performClick()
    compose.onAllNodesWithText("Save meal").onFirst().performClick()
    compose.onNodeWithText("Template name").performTextClearance()
    compose.onNodeWithText("Template name").performTextInput("Usual breakfast")
    compose.onNodeWithText("Save meal template").performClick()
    compose.waitUntil(5000) { store.state.value.mealTemplates.any { it.name == "Usual breakfast" } }
    compose.onNodeWithText("Usual breakfast").performClick()
    compose.onNodeWithText("Log meal").performClick()
    compose.waitUntil(5000) { store.state.value.entries.count { it.food.name == "Test custom food" } == 2 }
  }

  @Test
  fun guidedPlanAndWeeklyCheckInOpenAsStepByStepFlows() {
    val store =
      EntryPointAccessors.fromApplication(compose.activity.application, WorkerServices::class.java)
        .store()
    runBlocking {
      store.load()
      val profile = Profile(name = "Sam", goal = "lose", weightKg = 80.0, targetKg = 74.0)
      store.update {
        AppState(
          profile = profile,
          plans =
            listOf(
              PlanCalculator.suggest(profile).copy(
                effective = LocalDate.now().minusDays(60).toString(),
                created = Instant.now().minusSeconds(60L * 86400).toString(),
              )
            ),
          measurements = listOf(Measurement(value = 80.0)),
        )
      }
    }
    compose.waitUntil(15000) {
      compose.onAllNodesWithContentDescription("Review plan").fetchSemanticsNodes().isNotEmpty()
    }
    compose.onNodeWithContentDescription("Review plan").performClick()
    compose.onNodeWithText("The facts behind your estimate").assertIsDisplayed()
    compose.onNodeWithText("Step 1 of 4").assertIsDisplayed()
    compose.onNodeWithContentDescription("Close").performClick()
    compose.waitUntil(5000) {
      compose.onAllNodesWithText("Your weekly check-in is ready").fetchSemanticsNodes().isNotEmpty()
    }
    compose.onNodeWithText("Review last week").performClick()
    compose.onNodeWithText("Which days were fully tracked?").assertIsDisplayed()
    compose.onNodeWithText("Step 1 of 3").assertIsDisplayed()
  }

  private fun snapshot(name: String) {
    compose.waitForIdle()
    val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
    val values =
      android.content.ContentValues().apply {
        put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, "calorietracker-$name.png")
        put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png")
        put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CalorieTrackerQA")
      }
    val uri =
      compose.activity.contentResolver.insert(
        android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        values,
      )!!
    compose.activity.contentResolver.openOutputStream(uri)!!.use {
      bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
    }
  }
}
