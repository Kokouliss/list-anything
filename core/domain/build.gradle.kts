plugins {
    alias(libs.plugins.app.jvm.library)
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    testImplementation(libs.bundles.unit.test)
}
