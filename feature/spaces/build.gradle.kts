plugins {
    alias(libs.plugins.app.android.feature)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.kokouliss.listanything.feature.spaces"
}

dependencies {
    implementation(libs.kotlinx.serialization)

    testImplementation(libs.bundles.unit.test)
}
