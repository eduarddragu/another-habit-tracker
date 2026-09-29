plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.ksp)
  alias(libs.plugins.androidx.room)
}

android {
    namespace = "dev.eduarddragu.anotherhabittracker"
    compileSdk = 37
    defaultConfig {
        applicationId = "dev.eduarddragu.anotherhabittracker"
        minSdk = 34
        targetSdk = 36
        versionCode = 35
        versionName = "1.0.0"
    }

    // The signing key lives outside the repo (see README). Without it, builds fall
    // back to the default debug key, so a fresh clone still compiles.
    val signingProps = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
        .associateWith { providers.gradleProperty("aht.$it").orNull }
    val personalKey = if (signingProps.values.all { it != null }) {
        signingConfigs.create("personal") {
            storeFile = file(signingProps.getValue("storeFile")!!)
            storePassword = signingProps.getValue("storePassword")
            keyAlias = signingProps.getValue("keyAlias")
            keyPassword = signingProps.getValue("keyPassword")
        }
    } else null

    buildTypes {
        debug {
            personalKey?.let { signingConfig = it }
        }
        release {
            personalKey?.let { signingConfig = it }
            // R8 matters for Compose: without it a release build is barely faster than debug.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
      compose = true
    }

    packaging {
      resources {
        excludes += "/META-INF/{AL2.0,LGPL2.1}"
        // kotlinx-coroutines debug agent data, unused at runtime.
        excludes += "DebugProbesKt.bin"
      }
    }
}

kotlin {
    jvmToolchain(17)
}

// Schemas are committed so every database change can ship a tested migration.
room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.activity.compose)

  // Arch Components
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.material3)
  // Tooling

  // Unit tests run on the JVM only: instrumented tests uninstall the app from the device afterwards.
  testImplementation(libs.junit)

  // Persistence
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.androidx.room.runtime)
  ksp(libs.androidx.room.compiler)

  // Home-screen widgets
  implementation(libs.androidx.glance.appwidget)
  implementation(libs.androidx.work.runtime)

  // Navigation
  implementation(libs.androidx.navigation3.ui)
  implementation(libs.androidx.navigation3.runtime)
  implementation(libs.androidx.lifecycle.viewmodel.navigation3)
}
