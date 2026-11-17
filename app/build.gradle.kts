plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.rallycopilot"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.rallycopilot"
        minSdk = 26
        targetSdk = 35
        versionCode = 9
        versionName = "0.9.0"
    }

    signingConfigs {
        create("stableDebug") {
            storeFile = file(System.getenv("RALLY_DEBUG_KEYSTORE") ?: "${System.getProperty("user.home")}/.android/rallycopilot-debug.keystore")
            storePassword = "rally-copilot-debug-only"
            keyAlias = "rallycopilotdebug"
            keyPassword = "rally-copilot-debug-only"
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("stableDebug")
        }
    }

    buildFeatures { compose = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.00"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("org.maplibre.gl:android-sdk:13.6.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
}