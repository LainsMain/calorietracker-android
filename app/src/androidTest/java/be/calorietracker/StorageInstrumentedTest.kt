package be.calorietracker

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import be.calorietracker.data.*
import be.calorietracker.domain.*
import be.calorietracker.services.*
import dagger.hilt.android.EntryPointAccessors
import java.io.File
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StorageInstrumentedTest {
  private val context = ApplicationProvider.getApplicationContext<TrackerApplication>()
  private val deps = EntryPointAccessors.fromApplication(context, WorkerServices::class.java)
  private val store = deps.store()

  @Before
  fun prepare() = runBlocking {
    store.load()
    store.clear()
  }

  @After
  fun cleanup() = runBlocking {
    store.clear()
    deps.prefs().clear()
  }

  @Test
  fun proposalsRequireExplicitApplyAndAreIdempotent() = runBlocking {
    val entry =
      Entry(food = Food(name = "Test food", nutrients = Nutrients(kcal = 100.0)), amount = 150.0)
    val proposal =
      Proposal(type = "entry", payload = codec.encodeToString(entry), explanation = "Test proposal")
    store.update { it.copy(proposals = listOf(proposal)) }
    assertTrue(store.state.value.entries.isEmpty())
    coroutineScope { repeat(10) { launch { store.applyProposal(proposal.id) } } }
    assertEquals(1, store.state.value.entries.size)
    store.undoProposal(proposal.id)
    assertTrue(store.state.value.entries.isEmpty())
    store.applyProposal(proposal.id)
    assertTrue(store.state.value.entries.isEmpty())
  }

  @Test
  fun weeklyRecommendationApplyIsIdempotentAndPreservesOldPlan() = runBlocking {
    val old =
      Plan(
        effective = "2026-01-01",
        kcal = 2000.0,
        protein = 100.0,
        fat = 80.0,
        carbs = 220.0,
      )
    val recommendation =
      WeeklyRecommendation(
        id = "weekly-recommendation",
        checkInId = "weekly-check-in",
        sufficientEvidence = true,
        confirmedDayCount = 12,
        currentKcal = 2000.0,
        proposedKcal = 1850.0,
        reason = "Tested multi-week trend",
      )
    store.update {
      it.copy(
        profile = Profile(goal = "lose"),
        plans = listOf(old),
        weeklyRecommendations = listOf(recommendation),
      )
    }
    coroutineScope { repeat(10) { launch { store.applyWeeklyRecommendation(recommendation.id) } } }
    assertEquals(2, store.state.value.plans.size)
    assertEquals(old, store.state.value.plan("2026-01-01"))
    assertEquals(1850.0, store.state.value.plan()!!.kcal, 0.0)
    assertEquals("applied", store.state.value.weeklyRecommendations.single().status)
  }

  @Test
  fun deletedMessagesInvalidateCompaction() = runBlocking {
    val message = Message(role = "user", text = "Private test message")
    store.update {
      it.copy(
        messages = listOf(message),
        summaries =
          listOf(
            Summary(
              text = "Summary",
              messageIds = listOf(message.id),
              from = message.timestamp,
              to = message.timestamp,
            )
          ),
      )
    }
    store.deleteMessage(message.id)
    assertTrue(store.state.value.messages.isEmpty())
    assertTrue(store.state.value.summaries.isEmpty())
  }

  @Test
  fun photosAndKeysAreEncrypted() = runBlocking {
    val raw = "private-image-content".toByteArray()
    val id = newId()
    store.putPhotoBytes(id, raw)
    assertFalse(store.photoFile(id).readBytes().contentEquals(raw))
    assertArrayEquals(raw, store.photoBytes(id))
    deps.prefs().saveKey("secret-example-token")
    assertFalse(File(context.filesDir, "api-key").readText().contains("secret-example-token"))
    assertEquals("secret-example-token", deps.prefs().apiKey())
  }

  @Test
  fun backupRestoresRecordsWithoutHealthOrKeys() = runBlocking {
    val entry =
      Entry(food = Food(name = "Backup test", nutrients = Nutrients(kcal = 100.0)), amount = 50.0)
    store.update {
      it.copy(
        entries = listOf(entry),
        health = listOf(HealthDay(today(), steps = 1000)),
        measurements =
          listOf(
            Measurement(value = 70.0),
            Measurement(value = 71.0, source = "fitness.app", sourceId = "x"),
          ),
      )
    }
    val backups = Backups(context, store, deps.prefs())
    val file = File(context.cacheDir, "test.ctbackup")
    deps.prefs().saveKey("before-export")
    backups.export(Uri.fromFile(file), "long-backup-password", false)
    store.clear()
    deps.prefs().saveKey("after-export")
    backups.restore(Uri.fromFile(file), "long-backup-password")
    assertEquals(entry, store.state.value.entries.single())
    assertTrue(store.state.value.health.isEmpty())
    assertEquals(1, store.state.value.measurements.size)
    assertEquals("after-export", deps.prefs().apiKey())
    file.delete()
    Unit
  }

  @Test
  fun invalidBackupDoesNotReplaceData() = runBlocking {
    val entry =
      Entry(food = Food(name = "Keep me", nutrients = Nutrients(kcal = 100.0)), amount = 50.0)
    store.log(entry)
    val backups = Backups(context, store, deps.prefs())
    val file = File(context.cacheDir, "bad.ctbackup").apply { writeText("broken") }
    try {
      backups.restore(Uri.fromFile(file), "long-backup-password")
      fail("Should reject")
    } catch (_: Exception) {}
    assertEquals(entry, store.state.value.entries.single())
    file.delete()
    Unit
  }

  @Test
  fun apkVerifierRunsOnAndroidAndRejectsCorruption() {
    val original = File(context.applicationInfo.sourceDir)
    val result =
      com.android.apksig.ApkVerifier.Builder(original)
        .setMinCheckedPlatformVersion(android.os.Build.VERSION.SDK_INT)
        .build()
        .verify()
    assertTrue(result.isVerified)
    val corrupt = File(context.cacheDir, "corrupt.apk")
    original.copyTo(corrupt, overwrite = true)
    java.io.RandomAccessFile(corrupt, "rw").use {
      it.seek(4096)
      val byte = it.readByte()
      it.seek(4096)
      it.writeByte(byte.toInt() xor 1)
    }
    val accepted = runCatching {
      com.android.apksig.ApkVerifier.Builder(corrupt)
        .setMinCheckedPlatformVersion(android.os.Build.VERSION.SDK_INT)
        .build()
        .verify()
        .isVerified
    }.getOrDefault(false)
    assertFalse(accepted)
    corrupt.delete()
  }
}
