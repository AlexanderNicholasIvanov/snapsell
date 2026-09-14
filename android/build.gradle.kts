plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    // Declared here (apply false) so it is on the build classpath; :app applies
    // it only when app/google-services.json exists. See app/build.gradle.kts.
    alias(libs.plugins.google.services) apply false
}
