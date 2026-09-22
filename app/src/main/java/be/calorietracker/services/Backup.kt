package be.calorietracker.services

import android.content.Context
import android.net.Uri
import be.calorietracker.data.*
import be.calorietracker.domain.*
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

@Serializable
data class BackupEnvelope(val format: Int = 1, val state: AppState, val photos: Map<String, String>)

object BackupCipher {
  private val magic = "CTBK0001".toByteArray()

  private fun key(password: CharArray, salt: ByteArray): SecretKeySpec {
    val spec = PBEKeySpec(password, salt, 600000, 256)
    return try {
      SecretKeySpec(
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded,
        "AES",
      )
    } finally {
      spec.clearPassword()
    }
  }

  fun encrypt(data: ByteArray, password: CharArray): ByteArray {
    require(password.size >= 10) { "Use at least 10 characters." }
    val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
    val c = Cipher.getInstance("AES/GCM/NoPadding")
    c.init(Cipher.ENCRYPT_MODE, key(password, salt))
    c.updateAAD(magic)
    return magic + salt + c.iv + c.doFinal(data)
  }

  fun decrypt(data: ByteArray, password: CharArray): ByteArray {
    require(data.size > 52 && data.copyOfRange(0, 8).contentEquals(magic)) {
      "Not a supported backup"
    }
    val c = Cipher.getInstance("AES/GCM/NoPadding")
    c.init(
      Cipher.DECRYPT_MODE,
      key(password, data.copyOfRange(8, 24)),
      GCMParameterSpec(128, data.copyOfRange(24, 36)),
    )
    c.updateAAD(magic)
    return c.doFinal(data.copyOfRange(36, data.size))
  }
}

class Backups
@Inject
constructor(
  @ApplicationContext private val context: Context,
  private val store: Store,
  private val prefs: Preferences,
) {
  suspend fun export(uri: Uri, password: String, includePhotos: Boolean) =
    withContext(Dispatchers.IO) {
      val state = store.state.value
      val photos =
        if (includePhotos)
          state.photos.associate {
            it.id to java.util.Base64.getEncoder().encodeToString(store.photoBytes(it.id))
          }
        else emptyMap()
      val safe =
        if (includePhotos) state
        else
          state.copy(
            photos = emptyList(),
            messages = state.messages.map { it.copy(photoIds = emptyList()) },
            recipes = state.recipes.map { it.copy(photoId = null) },
          )
      val encrypted =
        BackupCipher.encrypt(
          codec.encodeToString(BackupEnvelope(state = safe, photos = photos)).toByteArray(),
          password.toCharArray(),
        )
      context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(encrypted) }
    }

  suspend fun restore(uri: Uri, password: String) =
    withContext(Dispatchers.IO) {
      val bytes =
        context.contentResolver.openInputStream(uri)!!.use {
          be.calorietracker.data.readBounded(it, 128 * 1024 * 1024)
        }
      require(bytes.size < 128 * 1024 * 1024) { "Backup exceeds 128 MB limit." }
      val envelope =
        codec.decodeFromString<BackupEnvelope>(
          BackupCipher.decrypt(bytes, password.toCharArray()).decodeToString()
        )
      require(envelope.format == 1)
      envelope.state.validate()
      require(envelope.state.photos.all { envelope.photos.containsKey(it.id) }) {
        "Missing photo in backup"
      }
      // Stage photos with new IDs; an interrupted import cannot damage the currently committed
      // state.
      val ids = envelope.photos.keys.associateWith { newId() }
      val staged = mutableListOf<String>()
      try {
        envelope.photos.forEach { (id, base64) ->
          val raw = java.util.Base64.getDecoder().decode(base64)
          require(raw.size <= 25 * 1024 * 1024)
          store.putPhotoBytes(ids.getValue(id), raw)
          staged += ids.getValue(id)
        }
        val oldPhotos = store.state.value.photos.map { it.id }
        store.replace(
          envelope.state.copy(
            photos = envelope.state.photos.map { it.copy(id = ids.getValue(it.id)) },
            messages =
              envelope.state.messages.map { m ->
                m.copy(photoIds = m.photoIds.mapNotNull { ids[it] })
              },
            recipes = envelope.state.recipes.map { it.copy(photoId = ids[it.photoId]) },
          )
        )
        prefs.set("healthToken", "")
        oldPhotos.forEach { store.photoFile(it).delete() }
      } catch (e: Exception) {
        staged.forEach { store.photoFile(it).delete() }
        throw e
      }
    }
}
