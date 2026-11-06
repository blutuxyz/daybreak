// Windows desktop app. It reuses the :untis client and shows the same timetable and
// absences as the Android app. Android-only features (widget, background sync, app updates,
// QR scanning) are not part of this module.
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

val daybreakVersionName = (findProperty("daybreak.versionName") as String?)
    ?.takeIf { it.matches(Regex("""\d+\.\d+\.\d+""")) } ?: "1.0.0"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
    // Build and package with JDK 17, the version the workflows install and the app targets. The
    // bundled runtime is made from this JDK, so it matches the Java used everywhere else.
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":untis"))
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation(compose.foundation)
    implementation(compose.runtime)
    implementation(compose.ui)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.gson)
    // JNA gives the desktop app access to Windows DPAPI (Crypt32) for the stored WebUntis key.
    implementation("net.java.dev.jna:jna:5.14.0")
    implementation("net.java.dev.jna:jna-platform:5.14.0")

    testImplementation(libs.junit)
}

compose.desktop {
    application {
        mainClass = "com.bdersolutions.daybreak.desktop.MainKt"
        // Compose runs jlink and jpackage with this JDK. Without it the packaging used the JDK 25 that
        // Gradle downloads for itself, and jlink failed in CI.
        javaHome = javaToolchains.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(17))
        }.get().metadata.installationPath.asFile.absolutePath

        nativeDistributions {
            // The MSI is the old installer, no longer released. The one people get is :desktop:windowsInstaller
            // (Inno Setup, see below); jpackage still builds the app image that it packs.
            targetFormats(TargetFormat.Msi)
            packageName = "Daybreak"
            packageVersion = daybreakVersionName
            description = "Daybreak timetable for WebUntis"
            vendor = "BDER Solutions"
            // Bundle every standard module instead of the reduced set jlink picks. A reduced runtime
            // is the suspected cause of "Failed to launch JVM" on Windows; not confirmed yet.
            includeAllModules = true

            windows {
                menuGroup = "Daybreak"
                // The .exe, the shortcuts and the installer use this icon. Without it jpackage falls
                // back to the default Java/Kotlin icon.
                iconFile.set(project.file("packaging/daybreak.ico"))
                shortcut = true
                dirChooser = true
                // Fixed for this product, so a newer MSI upgrades an older install.
                upgradeUuid = "6f1c2a7e-3b8d-4c59-9e0a-2d7f4b1e8c35"
            }
        }
    }
}

// The version and the update server address (the same daybreak.updateApiUrl property the Android
// app uses) are written into a resource file, so the running app knows its own build and where
// to look for a newer one. Without the property the in-app update check stays off.
val daybreakUpdateApiUrl = ((findProperty("daybreak.updateApiUrl") as String?) ?: "").trim().trimEnd('/')
val buildInfoDir = layout.buildDirectory.dir("generated/daybreak-build-info").get().asFile
File(buildInfoDir, "daybreak-build.properties").apply {
    parentFile.mkdirs()
    writeText("versionName=$daybreakVersionName\nupdateApiUrl=$daybreakUpdateApiUrl\n")
}
sourceSets.getByName("main").resources.srcDir(buildInfoDir)

// Portable build: the app image (folder with Daybreak.exe and its runtime) zipped up.
tasks.register<Zip>("portableZip") {
    group = "distribution"
    description = "Zips the Windows app image into a portable archive."
    dependsOn("createDistributable")
    from(layout.buildDirectory.dir("compose/binaries/main/app/Daybreak"))
    into("Daybreak")
    archiveFileName.set("Daybreak-$daybreakVersionName-windows-portable.zip")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
}

// One-click installer: Inno Setup packs the app image (createDistributable) into
// Daybreak-<version>-setup.exe, which installs per user without pages or administrator rights, see
// packaging/windows/daybreak.iss. This replaces the MSI wizard of jpackage as the thing that is
// released. The compiler (ISCC.exe) comes from the INNO_SETUP_COMPILER environment variable or
// -Pdaybreak.innoCompiler, else the PATH, else the usual folders. GitHub's windows-latest runners have
// Inno Setup 6 installed; locally: winget install JRSoftware.InnoSetup
fun findInnoCompiler(): String? {
    val configured = ((findProperty("daybreak.innoCompiler") as String?) ?: System.getenv("INNO_SETUP_COMPILER"))
        ?.takeIf { it.isNotBlank() }
    if (configured != null) return configured
    val folders = listOfNotNull(
        System.getenv("PATH")?.split(File.pathSeparatorChar).orEmpty(),
        listOfNotNull(
            System.getenv("LOCALAPPDATA")?.let { "$it/Programs/Inno Setup 6" },
            System.getenv("ProgramFiles(x86)")?.let { "$it/Inno Setup 6" },
            System.getenv("ProgramFiles")?.let { "$it/Inno Setup 6" },
        ),
    ).flatten()
    return folders.map { File(it, "ISCC.exe") }.firstOrNull { it.isFile }?.absolutePath
}

tasks.register("windowsInstaller") {
    group = "distribution"
    description = "Builds the one-click Windows installer (Daybreak-<version>-setup.exe) with Inno Setup."
    dependsOn("createDistributable")
    // Plain values only, so the configuration cache can store the task.
    val version = daybreakVersionName
    val compiler = findInnoCompiler()
    val script = file("packaging/windows/daybreak.iss")
    val appImage = layout.buildDirectory.dir("compose/binaries/main/app/Daybreak").get().asFile
    val outputDir = layout.buildDirectory.dir("installer").get().asFile
    inputs.dir(appImage)
    inputs.dir(script.parentFile)
    inputs.property("version", version)
    outputs.dir(outputDir)
    doLast {
        if (compiler == null) {
            throw GradleException(
                "Inno Setup 6 (ISCC.exe) was not found. Install it with 'winget install JRSoftware.InnoSetup' " +
                    "or set INNO_SETUP_COMPILER to the path of ISCC.exe.",
            )
        }
        outputDir.mkdirs()
        outputDir.listFiles { f -> f.name.endsWith("-setup.exe") }?.forEach { it.delete() }
        val exitCode = ProcessBuilder(
            compiler,
            "/Qp",
            "/DAppVersion=$version",
            "/DSourceDir=${appImage.absolutePath}",
            "/DOutputDir=${outputDir.absolutePath}",
            script.absolutePath,
        ).inheritIO().start().waitFor()
        check(exitCode == 0) { "Inno Setup failed with exit code $exitCode." }
        check(File(outputDir, "Daybreak-$version-setup.exe").isFile) { "Inno Setup did not produce the installer." }
    }
}
