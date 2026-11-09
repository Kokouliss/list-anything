plugins {
    alias(libs.plugins.app.android.feature)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.kokouliss.listanything.feature.auth"
}

dependencies {
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.google.googleid)

    testImplementation(libs.bundles.unit.test)
}
