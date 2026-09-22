package be.calorietracker.data

import android.content.Context
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.*
import be.calorietracker.domain.*
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

val codec = Json {
  ignoreUnknownKeys = true
  encodeDefaults = true
}
private val Context.preferences by preferencesDataStore("preferences")

@Entity(tableName = "vault")
data class VaultRow(@PrimaryKey val id: Int = 1, val content: ByteArray)

@Dao
interface VaultDao {
  @Query("SELECT * FROM vault WHERE id=1") suspend fun read(): VaultRow?

  @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun write(row: VaultRow)
}

@Database(entities = [VaultRow::class], version = 1, exportSchema = true)
abstract class TrackerDb : RoomDatabase() {
  abstract fun vault(): VaultDao
}

@Singleton
class Crypto @Inject constructor() {
  private val key: SecretKey by lazy {
    val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    (store.getKey("tracker-vault-v1", null) as? SecretKey)
      ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        .apply {
          init(
            KeyGenParameterSpec.Builder(
                "tracker-vault-v1",
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
              )
              .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
              .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
              .build()
          )
        }
        .generateKey()
  }

  fun encrypt(data: ByteArray): ByteArray {
    val c = Cipher.getInstance("AES/GCM/NoPadding")
    c.init(Cipher.ENCRYPT_MODE, key)
    return c.iv + c.doFinal(data)
  }

  fun decrypt(data: ByteArray): ByteArray {
    require(data.size >= 28)
    val c = Cipher.getInstance("AES/GCM/NoPadding")
    c.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, data.copyOfRange(0, 12)))
    return c.doFinal(data.copyOfRange(12, data.size))
  }
}

@Singleton
class Preferences
@Inject
constructor(@ApplicationContext private val context: Context, private val crypto: Crypto) {
  val flow = context.preferences.data

  suspend fun set(name: String, value: String) =
    context.preferences.edit { it[stringPreferencesKey(name)] = value }

  suspend fun get(name: String, default: String = "") =
    flow.first()[stringPreferencesKey(name)] ?: default

  suspend fun apiKey() =
    withContext(Dispatchers.IO) {
      val f = File(context.filesDir, "api-key")
      if (f.exists()) crypto.decrypt(f.readBytes()).decodeToString() else ""
    }

  suspend fun saveKey(value: String) =
    withContext(Dispatchers.IO) {
      val f = File(context.filesDir, "api-key")
      if (value.isBlank()) f.delete() else f.writeBytes(crypto.encrypt(value.trim().toByteArray()))
    }

  suspend fun clear() {
    context.preferences.edit { it.clear() }
    saveKey("")
  }
}

@Singleton
class Store
@Inject
constructor(@ApplicationContext private val context: Context, private val crypto: Crypto) {
  private val db = Room.databaseBuilder(context, TrackerDb::class.java, "tracker.db").build()
  private val mutex = Mutex()
  private val _state = MutableStateFlow(AppState())
  val state = _state.asStateFlow()
  private var loaded = false

  suspend fun load() = mutex.withLock {
    if (!loaded) {
      withContext(Dispatchers.IO) {
        db.vault().read()?.let {
          _state.value =
            codec.decodeFromString<AppState>(crypto.decrypt(it.content).decodeToString())
        }
      }
      loaded = true
    }
  }

  suspend fun update(change: (AppState) -> AppState) = mutex.withLock {
    check(loaded)
    val next = change(_state.value)
    withContext(Dispatchers.IO) {
      db.vault().write(VaultRow(content = crypto.encrypt(codec.encodeToString(next).toByteArray())))
    }
    _state.value = next
  }

  suspend fun replace(state: AppState) {
    state.validate()
    update {
      state.copy(
        health = emptyList(),
        measurements = state.measurements.filter { it.source == "Manual" },
      )
    }
  }

  suspend fun cache(foods: List<Food>) = update { s ->
    s.copy(
      foods =
        (s.foods + foods)
          .groupBy { it.id }
          .map { (_, versions) ->
            val latest = versions.last()
            latest.copy(
              favourite = versions.any { it.favourite },
              lastUsed = versions.mapNotNull { it.lastUsed }.maxOrNull(),
            )
          }
    )
  }

  suspend fun favourite(food: Food) = update { s ->
    s.copy(foods = s.foods.filterNot { it.id == food.id } + food.copy(favourite = !food.favourite))
  }

  suspend fun log(entry: Entry) = update {
    it.copy(
      entries = it.entries + entry,
      foods = it.foods.filterNot { f -> f.id == entry.food.id } + entry.food.copy(lastUsed = now()),
    )
  }

  suspend fun deleteMessage(id: String) {
    val deleted = mutableListOf<String>()
    update { s ->
      val target = s.messages.firstOrNull { it.id == id }
      val remaining = s.messages.filterNot { it.id == id }
      deleted +=
        target?.photoIds.orEmpty().filter { photo ->
          remaining.none { photo in it.photoIds } && s.recipes.none { it.photoId == photo }
        }
      s.copy(
        messages = remaining,
        summaries = emptyList(),
        photos = s.photos.filterNot { it.id in deleted },
      )
    }
    deleted.forEach { photoFile(it).delete() }
  }

  suspend fun deletePhoto(id: String) {
    update { s ->
      s.copy(
        photos = s.photos.filterNot { it.id == id },
        messages = s.messages.map { it.copy(photoIds = it.photoIds - id) },
        summaries = emptyList(),
        recipes = s.recipes.map { if (it.photoId == id) it.copy(photoId = null) else it },
      )
    }
    photoFile(id).delete()
  }

  fun photoFile(id: String): File {
    require(id.matches(Regex("[a-zA-Z0-9-]+")))
    return File(File(context.filesDir, "photos").apply { mkdirs() }, id)
  }

  suspend fun photoBytes(id: String) =
    withContext(Dispatchers.IO) { crypto.decrypt(photoFile(id).readBytes()) }

  suspend fun putPhotoBytes(id: String, bytes: ByteArray) =
    withContext(Dispatchers.IO) { photoFile(id).writeBytes(crypto.encrypt(bytes)) }

  suspend fun importPhoto(uri: Uri, kind: String = "progress"): Photo =
    withContext(Dispatchers.IO) {
      val raw =
        context.contentResolver.openInputStream(uri)!!.use { readBounded(it, 25 * 1024 * 1024) }
      require(raw.size <= 25 * 1024 * 1024) { "Choose a photo under 25 MB." }
      val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
      android.graphics.BitmapFactory.decodeByteArray(raw, 0, raw.size, bounds)
      require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Unsupported image" }
      var sample = 1
      while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2400) sample *= 2
      val bitmap =
        android.graphics.BitmapFactory.decodeByteArray(
          raw,
          0,
          raw.size,
          android.graphics.BitmapFactory.Options().apply { inSampleSize = sample },
        ) ?: error("Unsupported image")
      val exif = androidx.exifinterface.media.ExifInterface(raw.inputStream())
      val transform =
        android.graphics.Matrix().apply {
          if (exif.isFlipped) postScale(-1f, 1f)
          postRotate(exif.rotationDegrees.toFloat())
        }
      val oriented =
        android.graphics.Bitmap.createBitmap(
          bitmap,
          0,
          0,
          bitmap.width,
          bitmap.height,
          transform,
          true,
        )
      val ratio = minOf(1.0, 1600.0 / maxOf(oriented.width, oriented.height))
      val scaled =
        android.graphics.Bitmap.createScaledBitmap(
          oriented,
          (oriented.width * ratio).toInt(),
          (oriented.height * ratio).toInt(),
          true,
        )
      val bytes =
        java.io
          .ByteArrayOutputStream()
          .also { scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, it) }
          .toByteArray()
      val p = Photo(kind = kind)
      putPhotoBytes(p.id, bytes)
      update { it.copy(photos = it.photos + p) }
      p
    }

  suspend fun clear() {
    update { AppState() }
    File(context.filesDir, "photos").deleteRecursively()
  }

  suspend fun applyProposal(id: String) = update { s ->
    val p = s.proposals.first { it.id == id }
    if (p.status != "pending") return@update s
    var next = s
    val ids =
      when (p.type) {
        "plan" -> {
          val v = codec.decodeFromString<Plan>(p.payload).copy(author = "coach", created = now())
          v.validate()
          next = s.copy(plans = s.plans + v)
          listOf(v.id)
        }
        "entry" -> {
          val v = codec.decodeFromString<Entry>(p.payload)
          require(v.nutrients == v.food.portion(v.amount, v.unit))
          next = s.copy(entries = s.entries + v)
          listOf(v.id)
        }
        "recipe" -> {
          val v = codec.decodeFromString<Recipe>(p.payload)
          require(v.ingredients.isNotEmpty())
          v.portion(100.0)
          next = s.copy(recipes = s.recipes + v)
          listOf(v.id)
        }
        else -> error("Unsupported action")
      }
    next.copy(
      proposals =
        next.proposals.map {
          if (it.id == id) it.copy(status = "applied", appliedIds = ids) else it
        },
      messages =
        next.messages +
          Message(role = "system", text = "User approved ${p.type} proposal $id", kind = "audit"),
    )
  }

  suspend fun undoProposal(id: String) = update { s ->
    val p = s.proposals.first { it.id == id }
    if (p.status != "applied") s
    else
      s.copy(
        plans = s.plans.filterNot { it.id in p.appliedIds },
        entries = s.entries.filterNot { it.id in p.appliedIds },
        recipes = s.recipes.filterNot { it.id in p.appliedIds },
        proposals = s.proposals.map { if (it.id == id) it.copy(status = "undone") else it },
        messages =
          s.messages + Message(role = "system", text = "User undid proposal $id", kind = "audit"),
      )
  }
}

fun readBounded(input: java.io.InputStream, limit: Int): ByteArray {
  val output = java.io.ByteArrayOutputStream()
  val buffer = ByteArray(8192)
  var total = 0
  while (true) {
    val count = input.read(buffer)
    if (count < 0) break
    total += count
    require(total <= limit) { "File is too large." }
    output.write(buffer, 0, count)
  }
  return output.toByteArray()
}
