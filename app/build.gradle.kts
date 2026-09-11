plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val ciVersionCode = System.getenv("FAMILJEKALENDER_VERSION_CODE")?.toIntOrNull()
val ciVersionName = System.getenv("FAMILJEKALENDER_VERSION_NAME")
val signingStoreFile = System.getenv("FAMILJEKALENDER_KEYSTORE_PATH")
val signingStorePassword = System.getenv("FAMILJEKALENDER_KEYSTORE_PASSWORD")
val signingKeyAlias = System.getenv("FAMILJEKALENDER_KEY_ALIAS")
val signingKeyPassword = System.getenv("FAMILJEKALENDER_KEY_PASSWORD")
val hasCiSigning = listOf(
    signingStoreFile,
    signingStorePassword,
    signingKeyAlias,
    signingKeyPassword
).all { !it.isNullOrBlank() }

android {
    namespace = "se.familjekalender.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "se.familjekalender.app"
        minSdk = 26
        targetSdk = 36
        versionCode = ciVersionCode ?: 4
        versionName = ciVersionName ?: "4.3.0"
    }

    signingConfigs {
        if (hasCiSigning) {
            create("ciRelease") {
                storeFile = file(signingStoreFile!!)
                storePassword = signingStorePassword
                keyAlias = signingKeyAlias
                keyPassword = signingKeyPassword
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            if (hasCiSigning) {
                signingConfig = signingConfigs.getByName("ciRelease")
            }
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
    implementation(platform("androidx.compose:compose-bom:2025.08.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("com.onesignal:OneSignal:[5.6.1, 5.9.99]")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
