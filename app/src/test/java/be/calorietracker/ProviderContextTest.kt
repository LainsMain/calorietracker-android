package be.calorietracker

import be.calorietracker.data.codec
import be.calorietracker.domain.*
import be.calorietracker.services.OpenFoodFactsParser
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test

class ProviderContextTest {
  @Test
  fun missingFoodNutrientsRemainUnknown() {
    val p =
      codec
        .parseToJsonElement("""{"code":"5400000000000","product_name":"Test","nutriments":{}}""")
        .jsonObject
    assertNull(OpenFoodFactsParser.parse(p)!!.nutrients.kcal)
  }

  @Test
  fun preparedProductIsExplicitlyLabelled() {
    val p =
      codec
        .parseToJsonElement(
          """{"code":"5400000000000","product_name":"Soup","nutriments":{"energy-kcal_prepared_100g":50,"proteins_prepared_100g":2}}"""
        )
        .jsonObject
    val f = OpenFoodFactsParser.parse(p)!!
    assertTrue(f.name.contains("prepared"))
    assertEquals(50.0, f.nutrients.kcal!!, 0.0)
    assertEquals(2.0, f.nutrients.protein!!, 0.0)
  }

  @Test
  fun liquidNutritionKeepsVolumeBasis() {
    val p =
      codec
        .parseToJsonElement(
          """{"code":"5400000000000","product_name":"Milk","product_quantity_unit":"ml","nutriments":{"energy-kcal_100g":42}}"""
        )
        .jsonObject
    assertEquals("ml", OpenFoodFactsParser.parse(p)!!.basis)
  }

  @Test
  fun contextIsBoundedAndChronological() {
    val messages =
      (0..99).map { Message(id = it.toString(), role = "user", text = "x".repeat(5000)) }
    val result = ChatContext.recent(messages)
    assertTrue(result.sumOf { it.text.length + 128 } <= 48000)
    assertEquals("99", result.last().id)
    assertTrue(result.zipWithNext().all { (a, b) -> a.id.toInt() < b.id.toInt() })
  }

  @Test
  fun summaryOnlyCoversIncludedMessages() {
    val messages =
      (0..99).map { Message(id = it.toString(), role = "user", text = "x".repeat(5000)) }
    val batch = ChatContext.oldestBatch(messages)
    assertTrue(batch.sumOf { it.text.length + 128 } <= 70000)
    assertEquals("0", batch.first().id)
    assertEquals("12", batch.last().id)
  }

  @Test
  fun mirroredWorkoutsDoNotDuplicateButSeparateSessionsRemain() {
    val one = Workout("a", "Walk", 1, "2026-09-20T10:00:00Z", "2026-09-20T11:00:00Z", "source.a")
    val mirror = one.copy(id = "b", source = "source.b")
    val separate = one.copy(id = "c", start = "2026-09-20T12:00:00Z", end = "2026-09-20T13:00:00Z")
    assertEquals(2, deduplicateWorkouts(listOf(one, mirror, separate, one)).size)
  }
}
