import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    android {
        namespace = "com.onesteptwo.shared"
        compileSdk = 37
        minSdk = 29
    }
    val xcf = XCFramework("shared")
    listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach {
        it.binaries.framework {
            baseName = "shared"
            xcf.add(this)
        }
    }
    jvmToolchain(21)

    sourceSets {
        commonMain.dependencies {
            implementation(libs.sqldelight.runtime)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.auth)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
        }
        androidMain.dependencies {
            implementation(libs.sqldelight.android.driver)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.clerk.android.api)
        }
        iosMain.dependencies {
            implementation(libs.sqldelight.native.driver)
            implementation(libs.ktor.client.darwin)
        }
    }
}

sqldelight {
    databases {
        create("OneStepTwoDatabase") {
            packageName.set("com.onesteptwo.db")
            verifyMigrations.set(true)    // fails build if sqm file is invalid; enforces REQ-025
            // schemaOutputDirectory enables the generateSchema Gradle task which produces {version}.db
            // required for verifySqlDelightMigration to compare migrations against the current schema
            schemaOutputDirectory.set(file("src/commonMain/sqldelight/migrations"))
        }
    }
}
