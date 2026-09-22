package be.calorietracker

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import be.calorietracker.domain.*
import be.calorietracker.services.WorkerServices
import dagger.hilt.android.EntryPointAccessors
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
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
              Entry(food = oats, amount = 250.0),
              Entry(food = lunch, amount = 350.0, meal = "Lunch"),
            ),
          measurements =
            (0L..20).map {
              Measurement(
                date = LocalDate.now().minusDays(it).toString(),
                value = 76.8 + it * 0.045,
              )
            },
          health = listOf(HealthDay(today(), steps = 6428, activeKcal = 286.0)),
          water = listOf(Water(ml = 1250)),
        )
      }
    }
    compose.waitUntil(15000) {
      compose.onAllNodesWithText("YOUR DAILY ENERGY").fetchSemanticsNodes().isNotEmpty()
    }
    snapshot("today")
    compose.onNodeWithText("Diary").performClick()
    compose.onNodeWithText("Food diary").assertIsDisplayed()
    snapshot("diary")
    compose.onNodeWithText("Recipes").performClick()
    compose.onNodeWithText("Made by you").assertIsDisplayed()
    compose.onNodeWithText("Progress").performClick()
    compose.onNodeWithText("Your progress").assertIsDisplayed()
    snapshot("progress")
    compose.onNodeWithText("Coach").performClick()
    compose.onNodeWithText("Your coach").assertIsDisplayed()
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
    compose.onNodeWithText("Log food").performScrollTo().performClick()
    compose.waitUntil(5000) { store.state.value.entries.any { it.food.name == "Test custom food" } }
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
