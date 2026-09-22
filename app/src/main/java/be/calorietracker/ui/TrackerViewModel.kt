package be.calorietracker.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import be.calorietracker.data.*
import be.calorietracker.domain.*
import be.calorietracker.services.*
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

@HiltViewModel
class TrackerViewModel
@Inject
constructor(
  val store: Store,
  val prefs: Preferences,
  val foods: Foods,
  val coach: Coach,
  val health: Health,
  val backups: Backups,
  val updates: Updates,
) : ViewModel() {
  val state = store.state
  val ready = MutableStateFlow(false)
  val error = MutableStateFlow<String?>(null)
  val results = MutableStateFlow<List<Food>>(emptyList())
  val searching = MutableStateFlow(false)
  val release = MutableStateFlow<Release?>(null)
  private var searchJob: Job? = null
  private var coachJob: Job? = null

  init {
    run {
      store.load()
      foods.load()
      ready.value = true
      if (prefs.get("healthEnabled") == "true")
        try {
          health.sync()
        } catch (_: Exception) {}
      try {
        release.value = updates.check()
      } catch (_: Exception) {}
    }
  }

  private var lastHealthAttempt = 0L

  fun refreshHealthIfEnabled() {
    run {
      if (!ready.value || System.currentTimeMillis() - lastHealthAttempt < 300000) return@run
      lastHealthAttempt = System.currentTimeMillis()
      if (prefs.get("healthEnabled") == "true")
        try {
          health.sync()
        } catch (e: Exception) {
          prefs.set("healthError", e.message ?: "Sync unavailable")
        }
    }
  }

  fun run(block: suspend () -> Unit) = viewModelScope.launch {
    try {
      block()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      error.value = e.message ?: "Something went wrong. Your saved records are unchanged."
    }
  }

  fun search(query: String) {
    searchJob?.cancel()
    results.value = foods.local(query)
    searchJob = run {
      if (query.length >= 2) {
        searching.value = true
        try {
          delay(650)
          val remote = foods.search(query)
          results.value = (foods.local(query) + remote).distinctBy { it.id }
        } finally {
          searching.value = false
        }
      }
    }
  }

  fun barcode(code: String, onFound: (Food?) -> Unit) {
    run { onFound(foods.barcode(code)) }
  }

  fun saveProfile(p: Profile, plan: Plan) {
    run {
      plan.validate()
      store.update {
        it.copy(
          profile = p,
          draft = p,
          plans = it.plans + plan,
          measurements = it.measurements + Measurement(value = p.weightKg),
        )
      }
    }
  }

  fun send(text: String, photos: List<String> = emptyList()) {
    coachJob = run { coach.send(text, photos) }
  }

  fun retryCoach() {
    coachJob = run { coach.retryLast() }
  }

  fun cancelCoach() {
    coach.cancel()
    coachJob?.cancel()
  }
}
