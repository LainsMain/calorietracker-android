package be.calorietracker

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.*
import androidx.health.connect.client.records.metadata.Metadata
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import be.calorietracker.domain.*
import be.calorietracker.services.*
import dagger.hilt.android.EntryPointAccessors
import java.time.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 34)
class HealthInstrumentedTest {
  @Test
  fun repeatedSyncAndSourceDeletionReconcile() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<TrackerApplication>()
    val deps = EntryPointAccessors.fromApplication(context, WorkerServices::class.java)
    Assume.assumeTrue(deps.health().available())
    val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
    for (permission in
      listOf(
        "READ_STEPS",
        "WRITE_STEPS",
        "READ_WEIGHT",
        "WRITE_WEIGHT",
        "READ_HEALTH_DATA_IN_BACKGROUND",
      )) {
      automation
        .executeShellCommand(
          "pm grant ${context.packageName} android.permission.health.$permission"
        )
        .use { fd -> java.io.FileInputStream(fd.fileDescriptor).use { it.readBytes() } }
    }
    val client = HealthConnectClient.getOrCreate(context)
    val start = Instant.now().minusSeconds(1200)
    val date = start.atZone(ZoneId.systemDefault()).toLocalDate()
    val inserted =
      client.insertRecords(
        listOf(
          StepsRecord(
            start,
            null,
            start.plusSeconds(600),
            null,
            500,
            Metadata.autoRecorded(
              device =
                androidx.health.connect.client.records.metadata.Device(
                  type = androidx.health.connect.client.records.metadata.Device.TYPE_PHONE
                )
            ),
          ),
          WeightRecord(
            start,
            null,
            androidx.health.connect.client.units.Mass.kilograms(77.0),
            Metadata.manualEntry(),
          ),
        )
      )
    try {
      deps.store().load()
      deps.store().clear()
      deps.health().sync()
      val first = deps.store().state.value
      assertEquals(1, first.health.count { it.date == date.toString() })
      val range =
        androidx.health.connect.client.time.TimeRangeFilter.between(
          date.atStartOfDay(ZoneId.systemDefault()).toInstant(),
          date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant(),
        )
      val platformSteps =
        client
          .aggregate(
            androidx.health.connect.client.request.AggregateRequest(
              setOf(StepsRecord.COUNT_TOTAL),
              range,
            )
          )[StepsRecord.COUNT_TOTAL]
      assertEquals(platformSteps, first.health.first { it.date == date.toString() }.steps)
      assertTrue(
        client
          .readRecords(
            androidx.health.connect.client.request.ReadRecordsRequest(StepsRecord::class, range)
          )
          .records
          .any { it.metadata.id == inserted.recordIdsList[0] }
      )
      assertEquals(1, first.measurements.count { it.source == context.packageName })
      deps.health().sync()
      assertEquals(
        first.health.map { it.date to it.steps },
        deps.store().state.value.health.map { it.date to it.steps },
      )
      assertEquals(
        1,
        deps.store().state.value.measurements.count { it.source == context.packageName },
      )
      client.deleteRecords(
        StepsRecord::class,
        recordIdsList = listOf(inserted.recordIdsList[0]),
        clientRecordIdsList = emptyList(),
      )
      client.deleteRecords(
        WeightRecord::class,
        recordIdsList = listOf(inserted.recordIdsList[1]),
        clientRecordIdsList = emptyList(),
      )
      deps.health().sync()
      assertTrue(deps.store().state.value.measurements.none { it.source == context.packageName })
      assertTrue(
        client
          .readRecords(
            androidx.health.connect.client.request.ReadRecordsRequest(StepsRecord::class, range)
          )
          .records
          .none { it.metadata.id == inserted.recordIdsList[0] }
      )
      assertEquals(
        client
          .aggregate(
            androidx.health.connect.client.request.AggregateRequest(
              setOf(StepsRecord.COUNT_TOTAL),
              range,
            )
          )[StepsRecord.COUNT_TOTAL],
        deps.store().state.value.health.first { it.date == date.toString() }.steps,
      )
    } finally {
      client.deleteRecords(
        StepsRecord::class,
        recordIdsList = listOf(inserted.recordIdsList[0]),
        clientRecordIdsList = emptyList(),
      )
      client.deleteRecords(
        WeightRecord::class,
        recordIdsList = listOf(inserted.recordIdsList[1]),
        clientRecordIdsList = emptyList(),
      )
      deps.store().clear()
      deps.prefs().clear()
    }
  }
}
