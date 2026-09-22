package be.calorietracker.services

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.FileProvider
import be.calorietracker.BuildConfig
import be.calorietracker.data.*
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.Serializable
import okhttp3.*

@Serializable
data class Release(
  val versionCode: Int,
  val versionName: String,
  val apkUrl: String,
  val sha256: String,
  val bytes: Long,
  val notes: String,
  val minSdk: Int = 29,
)

data class DownloadState(
  val received: Long = 0,
  val total: Long = 0,
  val status: String = "idle",
  val error: String? = null,
)

interface UpdateSource {
  suspend fun check(force: Boolean = false): Release?

  suspend fun download(release: Release): File
}

@Singleton
class Updates
@Inject
constructor(@ApplicationContext private val context: Context, private val prefs: Preferences) :
  UpdateSource {
  private val http = OkHttpClient.Builder().callTimeout(java.time.Duration.ofMinutes(5)).build()
  private var call: Call? = null
  val progress = MutableStateFlow(DownloadState())
  private val destination
    get() = File(File(context.cacheDir, "updates").apply { mkdirs() }, "update.apk")

  override suspend fun check(force: Boolean): Release? =
    withContext(Dispatchers.IO) {
      val last = prefs.get("lastUpdateCheck").toLongOrNull() ?: 0
      if (!force && System.currentTimeMillis() - last < 86400000) return@withContext null
      http
        .newCall(
          Request.Builder()
            .url(
              "https://github.com/LainsMain/calorietracker-android/releases/latest/download/update.json"
            )
            .build()
        )
        .execute()
        .use { r ->
          prefs.set("lastUpdateCheck", System.currentTimeMillis().toString())
          if (r.code == 404) return@withContext null
          require(r.isSuccessful) { "Update server returned ${r.code}" }
          val release = codec.decodeFromString<Release>(r.body!!.string())
          prefs.set("lastUpdateCheck", System.currentTimeMillis().toString())
          release.takeIf {
            it.versionCode > BuildConfig.VERSION_CODE &&
              it.minSdk <= android.os.Build.VERSION.SDK_INT
          }
        }
    }

  fun cancel() {
    call?.cancel()
    progress.value = progress.value.copy(status = "cancelled")
  }

  override suspend fun download(release: Release): File =
    withContext(Dispatchers.IO) {
      require(release.bytes in 1..250_000_000 && release.sha256.matches(Regex("[a-fA-F0-9]{64}"))) {
        "Invalid release metadata"
      }
      require(
        release.apkUrl.startsWith(
          "https://github.com/LainsMain/calorietracker-android/releases/download/"
        )
      ) {
        "Untrusted download location"
      }
      val part = File(destination.parentFile, "update.part")
      progress.value = DownloadState(total = release.bytes, status = "downloading")
      try {
        val current = http.newCall(Request.Builder().url(release.apkUrl).build())
        call = current
        current.execute().use { r ->
          require(r.isSuccessful) { "Download failed (${r.code})" }
          r.body!!.byteStream().use { input ->
            part.outputStream().use { out ->
              val buffer = ByteArray(65536)
              var count: Long = 0
              while (true) {
                ensureActive()
                val size = input.read(buffer)
                if (size < 0) break
                out.write(buffer, 0, size)
                count += size
                require(count <= release.bytes) { "Download exceeds advertised size" }
                progress.value = DownloadState(count, release.bytes, "downloading")
              }
            }
          }
        }
        require(part.length() == release.bytes) { "Incomplete download" }
        val digest = MessageDigest.getInstance("SHA-256")
        part.inputStream().use { input ->
          val b = ByteArray(65536)
          while (true) {
            val n = input.read(b)
            if (n < 0) break
            digest.update(b, 0, n)
          }
        }
        require(
          digest.digest().joinToString("") { "%02x".format(it) }.equals(release.sha256, true)
        ) {
          "Checksum mismatch"
        }
        verify(part, release.versionCode)
        check(part.renameTo(destination))
        progress.value = DownloadState(release.bytes, release.bytes, "ready")
        destination
      } catch (e: Exception) {
        part.delete()
        progress.value =
          progress.value.copy(
            status = if (progress.value.status == "cancelled") "cancelled" else "error",
            error = if (progress.value.status == "cancelled") null else e.message,
          )
        throw e
      } finally {
        call = null
      }
    }

  private fun verify(file: File, version: Int) {
    val verified =
      com.android.apksig.ApkVerifier.Builder(file)
        .setMinCheckedPlatformVersion(android.os.Build.VERSION.SDK_INT)
        .build()
        .verify()
    require(verified.isVerified) { "APK cryptographic signature verification failed" }
    val pm = context.packageManager
    val apk =
      pm.getPackageArchiveInfo(file.path, PackageManager.GET_SIGNING_CERTIFICATES)
        ?: error("Invalid APK")
    val installed = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
    require(
      apk.packageName == context.packageName &&
        apk.longVersionCode == version.toLong() &&
        apk.longVersionCode > installed.longVersionCode
    ) {
      "Unexpected package/version"
    }
    val trusted = installed.signingInfo!!.apkContentsSigners.map { it.toCharsString() }.toSet()
    require(apk.signingInfo!!.apkContentsSigners.map { it.toCharsString() }.toSet() == trusted) {
      "Signing certificate mismatch"
    }
  }

  fun install() {
    require(destination.exists())
    if (!context.packageManager.canRequestPackageInstalls()) {
      context.startActivity(
        Intent(
            android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
          )
          .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      )
      return
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", destination)
    context.startActivity(
      Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, "application/vnd.android.package-archive")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    )
  }
}
