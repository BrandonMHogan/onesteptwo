import java.util.Properties

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) load(f.inputStream())
}
val clerkPublishableKey: String =
    localProps.getProperty("CLERK_PUBLISHABLE_KEY")
        ?: System.getenv("CLERK_PUBLISHABLE_KEY")
        ?: ""
// Defaults to the Railway staging API (.planning/STATE.md §Service URLs) — override via
// local.properties/env for pointing at production or a local backend during development.
val apiBaseUrl: String =
    localProps.getProperty("API_BASE_URL")
        ?: System.getenv("API_BASE_URL")
        ?: "https://onesteptwo-staging.up.railway.app"

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.onesteptwo.android"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.onesteptwo.android"
        minSdk = 29
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"

        buildConfigField("String", "CLERK_PUBLISHABLE_KEY", "\"$clerkPublishableKey\"")
        buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
    }

    buildTypes {
        debug { /* staging */ }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        // okhttp3 logging-interceptor and jspecify both include this manifest;
        // exclude it to avoid a mergeJavaResource conflict in the debug APK.
        resources.excludes += "META-INF/versions/9/OSGI-INF/**"
    }
}

kotlin {
    jvmToolchain(21)
}

// clerk-android-api:1.0.31 transitively brings in androidx.browser:1.10.0 which declares
// minAgpVersion=8.9.1 in its AAR metadata. Since we use the headless Clerk SDK (email/password
// only — no OAuth browser flows in Phase 3), force browser to 1.8.0 which is compatible with
// AGP 8.7.3. The OAuth path can be revisited when AGP is upgraded in a future maintenance phase.
configurations.all {
    resolutionStrategy {
        force("androidx.browser:browser:1.8.0")
    }
}

dependencies {
    implementation(dependencies.project(":shared"))
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons.extended)
    implementation(libs.activity.compose)
    implementation(libs.clerk.android.api)
    implementation(libs.navigation.compose)
    implementation(libs.timber)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
}
