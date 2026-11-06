// Shared logic for the Android app and the Windows desktop app.
//
// The code uses java.time and Gson, which Kotlin common code cannot see, so it lives in one JVM
// source set (sharedJvm*) that both the Android target and the desktop target compile against.
// Nothing in it may import Android or Compose.
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
}

kotlin {
    androidLibrary {
        namespace = "com.bdersolutions.daybreak.shared"
        compileSdk = 37
        minSdk = 26
        withHostTest {}
    }

    jvm("desktop") {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }

    sourceSets {
        val sharedJvmMain by creating {
            dependencies {
                api(project(":untis"))
                implementation(libs.gson)
                implementation(libs.kotlinx.coroutines.core)
            }
        }
        val sharedJvmTest by creating {
            dependencies {
                implementation(libs.junit)
            }
        }
        getByName("androidMain").dependsOn(sharedJvmMain)
        getByName("desktopMain").dependsOn(sharedJvmMain)
        getByName("androidHostTest").dependsOn(sharedJvmTest)
        getByName("desktopTest").dependsOn(sharedJvmTest)
    }
}
