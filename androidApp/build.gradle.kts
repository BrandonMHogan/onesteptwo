import java.util.Properties

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) load(f.inputStream())
}
val clerkPublishableKey: String =
    localProps.getProperty("CLERK_PUBLISHABLE_KEY")
        ?: System.getenv("CLERK_PUBLISHABLE_KEY")
        ?: ""

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.onesteptwo.android"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.onesteptwo.android"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        buildConfigField("String", "CLERK_PUBLISHABLE_KEY", "\"$clerkPublishableKey\"")
    }

    buildTypes {
        debug { /* staging */ }
        release {
            isMinifyEnabled = true
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
    implementation(project(":shared"))
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons.extended)
    implementation(libs.activity.compose)
    implementation(libs.clerk.android.api)
    implementation(libs.navigation.compose)
}
