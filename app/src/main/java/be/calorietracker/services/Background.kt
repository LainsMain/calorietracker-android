package be.calorietracker.services

import android.app.*
import android.appwidget.*
import android.content.*
import android.os.Build
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.work.*
import be.calorietracker.MainActivity
import be.calorietracker.R
import be.calorietracker.data.*
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.LocalDate

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WorkerServices {
  fun store(): Store

  fun health(): Health

  fun prefs(): Preferences
}

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
  override suspend fun doWork(): Result {
    val deps = EntryPointAccessors.fromApplication(applicationContext, WorkerServices::class.java)
    return try {
      deps.store().load()
      if (
        deps.prefs().get("healthEnabled") == "true" &&
          "android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND" in deps.health().granted()
      )
        try {
          deps.health().sync()
        } catch (_: Exception) {}
      if (deps.prefs().get("reminders") == "true" && java.time.LocalTime.now().hour in 9..21) {
        val nm = applicationContext.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
          NotificationChannel(
            "tracking",
            "Tracking reminders",
            NotificationManager.IMPORTANCE_DEFAULT,
          )
        )
        val intent =
          PendingIntent.getActivity(
            applicationContext,
            1,
            Intent(applicationContext, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
          )
        val state = deps.store().state.value
        val today = LocalDate.now().toString()
        val message =
          when {
            state.entries.none { it.date == today } ->
              "A moment for your diary: log your latest meal."
            state.measurements.none { it.date >= LocalDate.now().minusDays(7).toString() } ->
              "Ready for a weekly weigh-in?"
            else -> "Check your water and food diary when you have a moment."
          }
        if (
          Build.VERSION.SDK_INT < 33 ||
            applicationContext.checkSelfPermission(
              android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
          nm.notify(
            12,
            NotificationCompat.Builder(applicationContext, "tracking")
              .setSmallIcon(R.drawable.ic_launcher)
              .setContentTitle("A little check-in")
              .setContentText(message)
              .setContentIntent(intent)
              .setAutoCancel(true)
              .build(),
          )
      }
      Result.success()
    } catch (_: Exception) {
      Result.retry()
    }
  }
}

class QuickLogWidget : AppWidgetProvider() {
  override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
    ids.forEach { id ->
      val view = RemoteViews(context.packageName, R.layout.widget)
      val action =
        PendingIntent.getActivity(
          context,
          0,
          Intent(context, MainActivity::class.java).putExtra("quickLog", true),
          PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
      view.setOnClickPendingIntent(R.id.widget_root, action)
      manager.updateAppWidget(id, view)
    }
  }
}
