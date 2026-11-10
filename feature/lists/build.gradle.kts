plugins {
    alias(libs.plugins.app.android.feature)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.kokouliss.listanything.feature.lists"
}

dependencies {
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core)
    implementation(libs.kotlinx.serialization)
    implementation(libs.maplibre.android)
    implementation(libs.reorderable)

    testImplementation(libs.bundles.unit.test)
}
