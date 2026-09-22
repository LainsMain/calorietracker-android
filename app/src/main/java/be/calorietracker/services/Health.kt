package be.calorietracker.services

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import androidx.health.connect.client.request.*
import androidx.health.connect.client.time.TimeRangeFilter
import be.calorietracker.data.*
import be.calorietracker.domain.*
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class Health
@Inject
constructor(
  @ApplicationContext private val context: Context,
  private val store: Store,
  private val prefs: Preferences,
) {
  private val mutex = Mutex()
  val permissions =
    setOf(
        StepsRecord::class,
        ExerciseSessionRecord::class,
        ActiveCaloriesBurnedRecord::class,
        TotalCaloriesBurnedRecord::class,
        DistanceRecord::class,
        WeightRecord::class,
      )
      .map { HealthPermission.getReadPermission(it) }
      .toSet()

  fun available() = HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

  suspend fun granted(): Set<String> =
    if (available())
      HealthConnectClient.getOrCreate(context).permissionController.getGrantedPermissions()
    else emptySet()

  fun optionalPermissions(): Set<String> {
    if (!available()) return emptySet()
    val features = HealthConnectClient.getOrCreate(context).features
    return buildSet {
      if (
        features.getFeatureStatus(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_IN_BACKGROUND) ==
          HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
      )
        add("android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND")
      if (
        features.getFeatureStatus(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_HISTORY) ==
          HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
      )
        add("android.permission.health.READ_HEALTH_DATA_HISTORY")
    }
  }

  suspend fun sync() = mutex.withLock { syncLocked() }

  private suspend fun syncLocked() {
    if (!available()) return
    val c = HealthConnectClient.getOrCreate(context)
    val access = c.permissionController.getGrantedPermissions()
    if (access.intersect(permissions).isEmpty()) {
      store.update {
        it.copy(
          health = emptyList(),
          measurements = it.measurements.filter { m -> m.source == "Manual" },
        )
      }
      return
    }
    val zone = ZoneId.systemDefault()
    val daysBack = if ("android.permission.health.READ_HEALTH_DATA_HISTORY" in access) 89L else 29L
    val days = (0L..daysBack).map { LocalDate.now().minusDays(it) }
    val recordTypes =
      setOf(
          StepsRecord::class,
          ExerciseSessionRecord::class,
          ActiveCaloriesBurnedRecord::class,
          TotalCaloriesBurnedRecord::class,
          DistanceRecord::class,
          WeightRecord::class,
        )
        .filter { HealthPermission.getReadPermission(it) in access }
        .toSet()
    val previousToken = prefs.get("healthToken")
    if (previousToken.isNotBlank()) {
      try {
        var page = previousToken
        do {
          val changes = c.getChanges(page)
          page = changes.nextChangesToken
          if (changes.changesTokenExpired) {
            prefs.set("healthToken", "")
            break
          }
        } while (changes.hasMore)
      } catch (_: Exception) {
        prefs.set("healthToken", "")
      }
    }
    // Capture before the snapshot: changes that arrive during reconciliation remain observable.
    val newToken = c.getChangesToken(ChangesTokenRequest(recordTypes))
    val results = mutableListOf<HealthDay>()
    val weights = mutableListOf<Measurement>()
    // Reconcile the whole readable window, so updates/deletions and provider-priority changes
    // replace old aggregates rather than accumulating duplicated samples.
    for (day in days) {
      val range =
        TimeRangeFilter.between(
          day.atStartOfDay(zone).toInstant(),
          day.plusDays(1).atStartOfDay(zone).toInstant(),
        )
      val metrics = buildSet {
        if (HealthPermission.getReadPermission(StepsRecord::class) in access)
          add(StepsRecord.COUNT_TOTAL)
        if (HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class) in access)
          add(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)
        if (HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class) in access)
          add(TotalCaloriesBurnedRecord.ENERGY_TOTAL)
        if (HealthPermission.getReadPermission(DistanceRecord::class) in access)
          add(DistanceRecord.DISTANCE_TOTAL)
      }
      val a = if (metrics.isNotEmpty()) c.aggregate(AggregateRequest(metrics, range)) else null
      val workouts = mutableListOf<Workout>()
      val origins = mutableSetOf<String>()
      a?.dataOrigins?.forEach { origins += it.packageName }
      if (HealthPermission.getReadPermission(ExerciseSessionRecord::class) in access) {
        var token: String? = null
        do {
          val r =
            c.readRecords(
              ReadRecordsRequest(ExerciseSessionRecord::class, range, pageToken = token)
            )
          r.records.forEach {
            workouts +=
              Workout(
                id = it.metadata.id,
                title = it.title ?: "Exercise",
                type = it.exerciseType,
                start = it.startTime.toString(),
                end = it.endTime.toString(),
                source = it.metadata.dataOrigin.packageName,
              )
            origins += it.metadata.dataOrigin.packageName
          }
          token = r.pageToken?.takeIf { it.isNotEmpty() }
        } while (token != null)
      }
      if (HealthPermission.getReadPermission(WeightRecord::class) in access) {
        var token: String? = null
        do {
          val r = c.readRecords(ReadRecordsRequest(WeightRecord::class, range, pageToken = token))
          r.records.forEach {
            weights +=
              Measurement(
                id = "health:${it.metadata.id}",
                date = it.time.atZone(zone).toLocalDate().toString(),
                value = it.weight.inKilograms,
                source = it.metadata.dataOrigin.packageName,
                sourceId = it.metadata.id,
              )
          }
          token = r.pageToken?.takeIf { it.isNotEmpty() }
        } while (token != null)
      }
      results +=
        HealthDay(
          day.toString(),
          a?.get(StepsRecord.COUNT_TOTAL),
          a?.get(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)?.inKilocalories,
          a?.get(TotalCaloriesBurnedRecord.ENERGY_TOTAL)?.inKilocalories,
          a?.get(DistanceRecord.DISTANCE_TOTAL)?.inMeters,
          deduplicateWorkouts(workouts),
          origins.toList(),
        )
    }
    store.update {
      it.copy(
        health = results,
        measurements =
          it.measurements.filter { m -> m.source == "Manual" } +
            weights.distinctBy { w -> w.sourceId },
      )
    }
    prefs.set("healthToken", newToken)
    prefs.set("healthSync", now())
  }
}
