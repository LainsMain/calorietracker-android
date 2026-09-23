plugins {
 id("com.android.application")
 id("org.jetbrains.kotlin.plugin.compose")
 id("org.jetbrains.kotlin.plugin.serialization")
 id("com.google.devtools.ksp")
 id("com.google.dagger.hilt.android")
}
android {
 namespace = "be.calorietracker"
 compileSdk = 37
 defaultConfig {
  applicationId = "be.calorietracker"
  minSdk = 29
  targetSdk = 37
  versionCode = providers.environmentVariable("VERSION_CODE").orNull?.toInt() ?: rootProject.file("version-code.txt").readText().trim().toInt()
  versionName = providers.environmentVariable("VERSION_NAME").orNull ?: "1.2.1"
  testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
 }
 signingConfigs {
  create("release") {
   providers.environmentVariable("SIGNING_STORE_FILE").orNull?.let { storeFile = file(it) }
   storePassword = providers.environmentVariable("SIGNING_STORE_PASSWORD").orNull
   keyAlias = "calorietracker"
   keyPassword = providers.environmentVariable("SIGNING_KEY_PASSWORD").orNull
  }
 }
 buildTypes { release { signingConfig = signingConfigs.getByName("release"); isMinifyEnabled = true; isShrinkResources = true; proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro") } }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 buildFeatures { compose = true; buildConfig = true }
 packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
 testOptions { unitTests.isReturnDefaultValues = true }
}
kotlin { jvmToolchain(17) }
ksp { arg("room.schemaLocation", "$projectDir/schemas") }
dependencies {
 implementation(platform("androidx.compose:compose-bom:2026.01.00"))
 implementation("androidx.core:core-ktx:1.17.0")
 implementation("androidx.activity:activity-compose:1.12.2")
 implementation("androidx.fragment:fragment-ktx:1.8.9")
 implementation("androidx.compose.material3:material3:1.4.0")
 implementation("androidx.compose.material:material-icons-extended")
 implementation("androidx.compose.ui:ui-tooling-preview")
 debugImplementation("androidx.compose.ui:ui-tooling")
 implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
 implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
 implementation("androidx.room:room-runtime:2.8.4")
 implementation("androidx.room:room-ktx:2.8.4")
 ksp("androidx.room:room-compiler:2.8.4")
 implementation("androidx.datastore:datastore-preferences:1.2.0")
 implementation("androidx.work:work-runtime-ktx:2.11.0")
 implementation("com.google.dagger:hilt-android:2.59.2")
 ksp("com.google.dagger:hilt-compiler:2.59.2")
 implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
 implementation("com.squareup.okhttp3:okhttp:4.12.0")
 implementation("com.android.tools.build:apksig:9.1.1")
 implementation("androidx.health.connect:connect-client:1.1.0")
 implementation("androidx.camera:camera-camera2:1.5.2")
 implementation("androidx.camera:camera-lifecycle:1.5.2")
 implementation("androidx.camera:camera-view:1.5.2")
 implementation("com.google.guava:guava:33.4.8-android")
 implementation("com.google.mlkit:barcode-scanning:17.3.0")
 implementation("androidx.biometric:biometric:1.1.0")
 implementation("androidx.exifinterface:exifinterface:1.4.1")
 testImplementation("junit:junit:4.13.2")
 testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
 androidTestImplementation("androidx.test.ext:junit:1.3.0")
 androidTestImplementation("androidx.test:runner:1.7.0")
 androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
 androidTestImplementation(platform("androidx.compose:compose-bom:2026.01.00"))
 androidTestImplementation("androidx.compose.ui:ui-test-junit4")
 debugImplementation("androidx.compose.ui:ui-test-manifest")
}
