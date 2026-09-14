import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    // NOTE: no org.jetbrains.kotlin.android -- AGP 9 has built-in Kotlin.
}

// Firebase's google-services plugin fails the build when google-services.json
// is missing. The file is gitignored, so apply the plugin only when a
// developer has dropped one in. Without it the app still builds and runs;
// AuthManager reports isConfigured=false and the debug "continue without
// sign-in" path is the way in.
val googleServicesJson = file("google-services.json")
if (googleServicesJson.exists()) {
    apply(plugin = libs.plugins.google.services.get().pluginId)
}

// Build-time configuration: local.properties (gitignored) wins over -P /
// gradle.properties project properties, which win over the defaults.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun configValue(name: String, default: String): String =
    localProps.getProperty(name)?.takeIf { it.isNotBlank() }
        ?: (project.findProperty(name) as String?)?.takeIf { it.isNotBlank() }
        ?: default

// 10.0.2.2 is the Android emulator's alias for the host machine's loopback.
val backendUrl = configValue("snapsell.backendUrl", "http://10.0.2.2:8000/")
val googleWebClientId = configValue("snapsell.googleWebClientId", "")

android {
    namespace = "com.alexivanov.snapsell"
    // Compose 1.12 (BOM 2026.08.00) requires compiling against API 37.
    // targetSdk stays at 36: compileSdk only allows newer APIs, while
    // targetSdk opts into new runtime behavior. Those move separately.
    compileSdk = 37

    defaultConfig {
        applicationId = "com.alexivanov.snapsell"
        // ML Kit Subject Segmentation requires API 24.
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        buildConfigField("String", "BACKEND_URL", "\"$backendUrl\"")
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"$googleWebClientId\"")
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        // jvmTarget defaults to compileOptions.targetCompatibility under built-in Kotlin.
        freeCompilerArgs.add("-opt-in=kotlin.RequiresOptIn")
    }
}

ksp {
    // Room schema export, so migrations can be diffed and tested later.
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation(libs.activity.compose)
    implementation(libs.core.ktx)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.navigation.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.datastore.preferences)
    implementation(libs.exifinterface)
    implementation(libs.browser)

    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)

    implementation(libs.mlkit.subject.segmentation)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.credentials)
    implementation(libs.credentials.play.services.auth)
    implementation(libs.googleid)

    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    implementation(libs.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
}
