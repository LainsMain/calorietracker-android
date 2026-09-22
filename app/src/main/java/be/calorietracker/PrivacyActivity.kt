package be.calorietracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import be.calorietracker.ui.TrackerTheme

/**
 * Public Health Connect permission rationale; available without onboarding or unlocking records.
 */
class PrivacyActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
      TrackerTheme {
        Surface(Modifier.fillMaxSize()) {
          Column(
            Modifier.padding(24.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp),
          ) {
            Text("Your health data, your choice", style = MaterialTheme.typography.headlineLarge)
            Text(
              "CalorieTracker reads only the Health Connect data types you approve: steps, exercise sessions, distance, active/total energy and weight. You can change or revoke access in Health Connect at any time."
            )
            Text(
              "Imported records stay encrypted on this phone. Background sync and older-history access are optional. The app reads health data; release builds never write it back. Exercise calories are not automatically added to your food allowance."
            )
            Text(
              "When you use the optional DeepSeek coach, relevant diary and imported health context is sent to DeepSeek using your API key. There is no application backend, advertising or analytics. Without the coach, no health records are sent to an AI provider."
            )
            Text(
              "Settings contains controls to disconnect Health Connect, delete imported records, delete all local data, and export a password-encrypted backup. Local deletion cannot retract data already submitted to DeepSeek."
            )
            Button(onClick = { finish() }) { Text("Back") }
          }
        }
      }
    }
  }
}
