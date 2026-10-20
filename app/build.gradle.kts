plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Use one private PKCS#12 signing key for every distributable Centaurix APK.
// CI injects the file and its password; neither is stored in the repository.
val releaseKeyFile = providers.environmentVariable("CENTAURIX_KEYSTORE_FILE").orNull
val releaseKeyPassword = providers.environmentVariable("CENTAURIX_SIGNING_PASSWORD").orNull
val hasReleaseSigning = !releaseKeyFile.isNullOrBlank() && !releaseKeyPassword.isNullOrBlank()

android {
    namespace = "fr.centaurix.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "fr.centaurix.app"
        minSdk = 26
        targetSdk = 35
        // Always increase versionCode for updates. Never reset it.
        // In CI, use a monotonically increasing number so newer APKs replace older builds.
        versionCode = providers.environmentVariable("CENTAURIX_CI_VERSION_CODE")
            .orNull?.toIntOrNull() ?: 106
        versionName = "1.0.0-beta6"
        // Releases from the same signing channel are compatible Android updates.
        val channel = providers.environmentVariable("CENTAURIX_UPDATE_CHANNEL").orNull ?: "none"
        buildConfigField("String", "UPDATE_CHANNEL", "\"$channel\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("centaurixRelease") {
                storeFile = file(releaseKeyFile!!)
                storePassword = releaseKeyPassword
                storeType = "pkcs12"
                keyAlias = "centaurix"
                // This keystore uses the same password for the private key.
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("centaurixRelease")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
}
dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.04.01")
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.0")
    testImplementation("junit:junit:4.13.2")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
