package be.calorietracker.services

import android.content.Context
import be.calorietracker.data.*
import be.calorietracker.domain.*
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.Normalizer
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl

interface FoodProvider {
  suspend fun search(query: String): List<Food>

  suspend fun barcode(code: String): Food?
}

@Singleton
class Foods
@Inject
constructor(@ApplicationContext private val context: Context, private val store: Store) :
  FoodProvider {
  private val http = OkHttpClient.Builder().callTimeout(java.time.Duration.ofSeconds(25)).build()
  private val mutex = Mutex()
  private var lastSearch = 0L
  private var catalog: List<Food> = emptyList()

  suspend fun load() {
    if (catalog.isEmpty())
      catalog =
        withContext(Dispatchers.IO) {
          codec.decodeFromString<List<Food>>(
            context.assets.open("cofid.json").bufferedReader().use { it.readText() }
          )
        }
  }

  private fun norm(s: String) =
    Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}"), "")

  fun local(query: String): List<Food> {
    val all = (store.state.value.foods + catalog).distinctBy { it.id }
    if (query.isBlank())
      return all
        .filter { it.favourite || it.lastUsed != null }
        .sortedWith(compareByDescending<Food> { it.favourite }.thenByDescending { it.lastUsed })
        .take(40)
        .ifEmpty {
          catalog
            .filter {
              it.name.startsWith("Bananas") ||
                it.name.startsWith("Eggs, chicken") ||
                it.name.startsWith("Rice, white")
            }
            .take(10)
        }
    val words = norm(query).split(" ").filter { it.isNotBlank() }
    return all
      .filter { food ->
        val text = norm("${food.name} ${food.brand} ${food.aliases} ${food.barcode.orEmpty()}")
        words.all { text.contains(it) }
      }
      .sortedWith(compareByDescending<Food> { it.favourite }.thenBy { it.name.length })
      .take(60)
  }

  override suspend fun search(query: String): List<Food> = mutex.withLock {
    val elapsed = System.currentTimeMillis() - lastSearch
    if (elapsed < 6500) delay(6500 - elapsed)
    lastSearch = System.currentTimeMillis()
    val url =
      "https://world.openfoodfacts.org/cgi/search.pl"
        .toHttpUrl()
        .newBuilder()
        .addQueryParameter("search_terms", query)
        .addQueryParameter("search_simple", "1")
        .addQueryParameter("action", "process")
        .addQueryParameter("json", "1")
        .addQueryParameter("page_size", "30")
        .addQueryParameter(
          "fields",
          "code,product_name,product_name_en,brands,nutriments,quantity,product_quantity_unit,serving_quantity,nutrition_data_per,nutrition_data_prepared_per,countries_tags",
        )
        .build()
    val body = request(url.toString())
    val products = body["products"]?.jsonArray ?: JsonArray(emptyList())
    val ranked =
      products
        .sortedByDescending {
          it.jsonObject["countries_tags"]?.jsonArray?.any { v ->
            v.jsonPrimitive.content == "en:belgium"
          } == true
        }
        .mapNotNull { OpenFoodFactsParser.parse(it.jsonObject) }
    store.cache(ranked)
    ranked
  }

  override suspend fun barcode(code: String): Food? {
    require(code.matches(Regex("[0-9]{8,14}"))) { "Enter an 8–14 digit product barcode." }
    store.state.value.foods
      .firstOrNull { it.barcode == code }
      ?.let {
        return it
      }
    val body = request("https://world.openfoodfacts.org/api/v2/product/$code.json")
    return body["product"]
      ?.jsonObject
      ?.let { OpenFoodFactsParser.parse(it, code) }
      ?.also { store.cache(listOf(it)) }
  }

  private suspend fun request(url: String) =
    withContext(Dispatchers.IO) {
      http
        .newCall(
          Request.Builder()
            .url(url)
            .header(
              "User-Agent",
              "CalorieTracker/1.0 (https://github.com/LainsMain/calorietracker-android)",
            )
            .build()
        )
        .execute()
        .use {
          require(it.isSuccessful) {
            "Food service unavailable (${it.code}). Cached foods still work."
          }
          codec.parseToJsonElement(it.body!!.string()).jsonObject
        }
    }
}

object OpenFoodFactsParser {
  fun parse(p: JsonObject, barcode: String? = null): Food? {
    fun text(k: String) = p[k]?.jsonPrimitive?.contentOrNull.orEmpty()
    val name = text("product_name").ifBlank { text("product_name_en") }
    if (name.isBlank()) return null
    val n = p["nutriments"]?.jsonObject ?: JsonObject(emptyMap())
    val prepared =
      n["energy-kcal_100g"] == null &&
        n["energy_100g"] == null &&
        n.keys.any { it.contains("_prepared_100g") }
    fun num(k: String): Double? {
      val field = if (prepared) k.replace("_100g", "_prepared_100g") else k
      return n[field]?.jsonPrimitive?.doubleOrNull?.takeIf { it.isFinite() && it >= 0 }
    }
    val code = barcode ?: text("code")
    if (code.isBlank()) return null
    val per = text(if (prepared) "nutrition_data_prepared_per" else "nutrition_data_per")
    val declaredVolume =
      text("product_quantity_unit").lowercase() in listOf("ml", "cl", "l") ||
        Regex("(?i)\\d\\s*(ml|cl|l)\\b").containsMatchIn(text("quantity"))
    val basis = if (per.contains("ml") || declaredVolume) "ml" else "g"
    return Food(
      id = "off:$code",
      name = name + if (prepared) " (prepared)" else "",
      brand = text("brands"),
      barcode = code,
      basis = basis,
      nutrients =
        Nutrients(
          num("energy-kcal_100g") ?: num("energy_100g")?.div(4.184),
          num("proteins_100g"),
          num("carbohydrates_100g"),
          num("fat_100g"),
          num("saturated-fat_100g"),
          num("sugars_100g"),
          num("fiber_100g"),
          num("salt_100g"),
        ),
      serving = p["serving_quantity"]?.jsonPrimitive?.doubleOrNull?.takeIf { it > 0 },
      source = "Open Food Facts · ODbL",
      sourceUrl = "https://world.openfoodfacts.org/product/$code",
    )
  }
}
