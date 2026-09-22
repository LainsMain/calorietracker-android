package be.calorietracker

import android.app.Application
import androidx.work.*
import be.calorietracker.services.SyncWorker
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.TimeUnit

@HiltAndroidApp
class TrackerApplication : Application() {
  override fun onCreate() {
    super.onCreate()
    WorkManager.getInstance(this)
      .enqueueUniquePeriodicWork(
        "health-and-reminders",
        ExistingPeriodicWorkPolicy.KEEP,
        PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS).build(),
      )
  }
}
