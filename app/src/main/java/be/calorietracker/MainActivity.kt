package be.calorietracker

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import be.calorietracker.ui.*
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
  private val vm: TrackerViewModel by viewModels()
  private var stoppedAt = 0L

  override fun onStop() {
    super.onStop()
    stoppedAt = System.currentTimeMillis()
  }

  override fun onStart() {
    super.onStart()
    if (stoppedAt > 0 && System.currentTimeMillis() - stoppedAt > 60000) showLockedOrApp()
  }

  override fun onResume() {
    super.onResume()
    vm.refreshHealthIfEnabled()
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    showLockedOrApp()
  }

  private fun showLockedOrApp() {
    lifecycleScope.launch {
      if (vm.prefs.get("biometric") == "true") {
        setContent { TrackerTheme { LockScreen { authenticate() } } }
        authenticate()
      } else showApp()
    }
  }

  private fun authenticate() {
    val prompt =
      BiometricPrompt(
        this,
        ContextCompat.getMainExecutor(this),
        object : BiometricPrompt.AuthenticationCallback() {
          override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            showApp()
          }
        },
      )
    prompt.authenticate(
      BiometricPrompt.PromptInfo.Builder()
        .setTitle("Unlock CalorieTracker")
        .setSubtitle("Your diary stays on this device")
        .setAllowedAuthenticators(
          androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG or
            androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
        )
        .build()
    )
  }

  private fun showApp() {
    setContent { TrackerRoot(vm, intent.getBooleanExtra("quickLog", false)) }
  }
}
