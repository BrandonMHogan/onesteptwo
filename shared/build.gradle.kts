plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.sqldelight)
}

kotlin {
    androidTarget()
    listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach {
        it.binaries.framework { baseName = "shared" }
    }
    jvmToolchain(21)

    sourceSets {
        commonMain.dependencies {
            implementation(libs.sqldelight.runtime)
        }
        androidMain.dependencies {
            implementation(libs.sqldelight.android.driver)
        }
        iosMain.dependencies {
            implementation(libs.sqldelight.native.driver)
        }
    }
}

android {
    namespace = "com.onesteptwo.shared"
    compileSdk = 36
    defaultConfig {
        minSdk = 29
    }
}

sqldelight {
    databases {
        create("OneStepTwoDatabase") {
            packageName.set("com.onesteptwo.db")
            verifyMigrations.set(true)    // fails build if sqm file is invalid; enforces REQ-025
        }
    }
}
