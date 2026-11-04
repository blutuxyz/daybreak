plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Release builds on CI pass the version of the GitHub release, so every release has a higher
// versionCode than the one before. Local builds keep 1.0 / 1. See update-server/README.md.
val daybreakVersionName = (findProperty("daybreak.versionName") as String?) ?: "1.0"
val daybreakVersionCode = (findProperty("daybreak.versionCode") as String?)?.toIntOrNull() ?: 1

// Base URL of the update API (https only), for example https://updates.example.com. Empty hides
// the update feature. Set it in ~/.gradle/gradle.properties or pass -Pdaybreak.updateApiUrl=...
val daybreakUpdateApiUrl = ((findProperty("daybreak.updateApiUrl") as String?) ?: "")
    .trim()
    .trimEnd('/')

// The signing key stays out of the repository: it comes from the environment (CI) or from
// gradle properties. Without it the release APK is built unsigned and cannot be installed.
fun signingValue(env: String, property: String): String? =
    System.getenv(env)?.takeIf { it.isNotBlank() } ?: (findProperty(property) as String?)?.takeIf { it.isNotBlank() }

val releaseKeystore = signingValue("DAYBREAK_KEYSTORE_FILE", "daybreak.keystoreFile")?.let(::file)

android {
    namespace = "com.bdersolutions.daybreak"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.bdersolutions.daybreak"
        minSdk = 26
        targetSdk = 37
        versionCode = daybreakVersionCode
        versionName = daybreakVersionName
        buildConfigField("String", "UPDATE_API_URL", "\"$daybreakUpdateApiUrl\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseKeystore != null && releaseKeystore.exists()) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = signingValue("DAYBREAK_KEYSTORE_PASSWORD", "daybreak.keystorePassword")
                keyAlias = signingValue("DAYBREAK_KEY_ALIAS", "daybreak.keyAlias")
                keyPassword = signingValue("DAYBREAK_KEY_PASSWORD", "daybreak.keyPassword")
            }
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    implementation(libs.google.play.services.code.scanner)
    implementation(libs.gson)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
    implementation(project(":untis"))

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
