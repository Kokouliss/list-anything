plugins {
    alias(libs.plugins.app.android.application)
    alias(libs.plugins.app.android.application.compose)
    alias(libs.plugins.app.hilt)
    alias(libs.plugins.kotlin.serialization)
}

// google-services.json is per Firebase project and is not committed. Without it the app still
// builds and shows a "setup needed" screen instead of crashing.
if (file("google-services.json").exists()) {
    apply(plugin = libs.plugins.google.services.get().pluginId)
    // Crash reports in the Firebase console. Needs the project file above, so it lives in the same if.
    apply(plugin = libs.plugins.firebase.crashlytics.get().pluginId)
}

// The number of the GitHub Actions run that built it (the "Build N" on the Releases page), so
// each download installs over the one before and a crash report names its build. Local builds are 1.
val buildNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1

// The real signing key. The release workflow writes it here from a GitHub secret; it is never
// committed. See "Signing key" in the README.
val releaseKeystore = file("release.keystore")

android {
    namespace = "com.kokouliss.listanything"

    defaultConfig {
        applicationId = "com.kokouliss.listanything"
        versionCode = buildNumber
        versionName = "0.1.$buildNumber"
    }

    signingConfigs {
        // Shared debug key so every machine produces the same SHA-1 for Google sign-in.
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        if (releaseKeystore.exists()) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // Without the real key the debug key signs it, so the build still installs and signs in.
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
            optimization {
                enable = true
            }
        }
    }
}

dependencies {
    implementation(project(":feature:auth"))
    implementation(project(":feature:spaces"))
    implementation(project(":feature:lists"))
    implementation(project(":core:domain"))
    implementation(project(":core:data"))
    implementation(project(":core:ui"))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.work.runtime)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.messaging)
    implementation(libs.kotlinx.serialization)

    testImplementation(libs.bundles.unit.test)
}
