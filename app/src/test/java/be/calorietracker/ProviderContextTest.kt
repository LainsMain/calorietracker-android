package be.calorietracker

import be.calorietracker.data.codec
import be.calorietracker.domain.*
import be.calorietracker.services.OpenFoodFactsParser
import be.calorietracker.services.DeepSeekStreamAccumulator
import be.calorietracker.ui.markdownBlocks
import be.calorietracker.ui.safeMarkdown
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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

  @Test
  fun versionOneStateDecodesWithNewFieldsDefaulted() {
    val state =
      codec.decodeFromString<AppState>(
        """{"schema":1,"plans":[{"id":"old","effective":"2026-01-01","created":"2026-01-01T00:00:00Z","kcal":2000.0,"protein":100.0,"fat":80.0,"carbs":220.0}]}"""
      )
    assertNull(state.plans.single().intent)
    assertTrue(state.weeklyCheckIns.isEmpty())
    assertTrue(state.weeklyRecommendations.isEmpty())
  }

  @Test
  fun markdownDropsHtmlAndRemoteImagesButKeepsReadableStructure() {
    val safe = safeMarkdown("# Heading\n<script>unsafe()</script>\n![remote](https://example.com/a.png)\n- item\n```\ncode()\n```")
    assertFalse(safe.contains("<script>"))
    assertFalse(safe.contains("https://example.com/a.png"))
    val blocks = markdownBlocks(safe)
    assertEquals("h1", blocks.first().kind)
    assertTrue(blocks.any { it.kind == "bullet" && it.text == "item" })
    assertTrue(blocks.any { it.kind == "code" && it.text == "code()" })
  }

  @Test
  fun malformedMarkdownRemainsVisible() {
    val blocks = markdownBlocks("**unfinished\n```\nstill readable")
    assertTrue(blocks.any { it.text.contains("unfinished") })
    assertTrue(blocks.any { it.kind == "code" && it.text.contains("still readable") })
  }

  @Test
  fun deepSeekStreamKeepsReasoningHiddenAndReassemblesFragmentedTools() {
    val accumulator = DeepSeekStreamAccumulator()
    listOf(
        """{"choices":[{"delta":{"reasoning_content":"private "},"finish_reason":null}]}""",
        """{"choices":[{"delta":{"reasoning_content":"thought","tool_calls":[{"index":0,"id":"call_","function":{"name":"read_","arguments":"{\"fr"}}]},"finish_reason":null}]}""",
        """{"choices":[{"delta":{"tool_calls":[{"index":0,"id":"1","function":{"name":"data","arguments":"om\":\"2026-01-01\"}"}}]},"finish_reason":"tool_calls"}]}""",
      )
      .map { codec.parseToJsonElement(it).jsonObject }
      .forEach { accumulator.accept(it) }
    val response = accumulator.response()
    assertEquals("private thought", response["reasoning_content"]!!.toString().trim('"'))
    assertFalse(response["content"]!!.toString().contains("private"))
    val function =
      response["tool_calls"]!!.jsonArray.single().jsonObject["function"]!!.jsonObject
    assertEquals("read_data", function["name"]!!.toString().trim('"'))
    assertEquals("{\"from\":\"2026-01-01\"}", function["arguments"]!!.jsonPrimitive.content)
  }
}
