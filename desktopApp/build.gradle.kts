import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    // Compose Desktop shares the same Kotlin and Compose toolchain as Android.
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

// One of the desktop-only dependencies (coil3 and/or vlcj's transitive deps) pulls in
// kotlin-stdlib 2.4.0, which is newer than the Kotlin compiler this project uses (2.2.x).
// That mismatch is what produces the wall of "Unresolved reference" / "incompatible
// metadata version" errors during :desktopApp:compileKotlinDesktop.
// Forcing every kotlin-stdlib artifact to the Kotlin plugin's own version fixes it.
configurations.all {
    resolutionStrategy {
        force(
            "org.jetbrains.kotlin:kotlin-stdlib:${libs.versions.kotlin.get()}",
            "org.jetbrains.kotlin:kotlin-stdlib-common:${libs.versions.kotlin.get()}",
            "org.jetbrains.kotlin:kotlin-stdlib-jdk7:${libs.versions.kotlin.get()}",
            "org.jetbrains.kotlin:kotlin-stdlib-jdk8:${libs.versions.kotlin.get()}",
        )
    }
}

kotlin {
    // Compose Desktop is stable on JDK 21 LTS; JDK 26 causes Skiko rendering artifacts.
    jvm("desktop") {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }
    jvmToolchain(21)

    sourceSets {
        val desktopMain by getting {
            // Reuse the existing scraper, HTTP client, and media models verbatim.
            // Android-only UI, Room, and preferences code stays excluded from this JVM target.
            kotlin.srcDir(project(":app").file("src/main/java"))
            kotlin.exclude(
                "**/MainActivity.kt",
                "**/ui/**",
                "**/data/database/**",
                "**/data/network/DomainManager.kt",
            )

            dependencies {
                implementation(project(":shared"))
                implementation(compose.desktop.currentOs)
                // Multiplatform Material 3, not the Android-only Material dependency.
                implementation(compose.material3)
                // Navigation and favourite glyphs used by the desktop shell.
                implementation(compose.materialIconsExtended)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.okhttp)
                implementation(libs.okhttp.dnsoverhttps)
                implementation(libs.jsoup)
                implementation("org.json:json:20240303")
                // LibVLC/VLCJ provides a reliable embedded HLS/DASH player on Windows.
                // Current vlcj 4.x bindings for the locally installed LibVLC player.
                implementation("uk.co.caprica:vlcj:4.11.0")
                // Exposes the native AWT window handle used by mpv's --wid embedding mode.
                implementation("net.java.dev.jna:jna:5.15.0")
                // Remote posters are loaded by the small JVM loader in Main.kt. This avoids
                // mixing Coil's older Skiko binary with Compose Desktop's newer Skiko binary.
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.aistudio.streamforge.desktop.MainKt"
        // Some Windows GPU/driver combinations render corrupt Skiko frames (white/black tiles).
        // Software Skia is slower but provides a stable, deterministic desktop UI.
        jvmArgs += listOf(
            "-Dskiko.renderApi=SOFTWARE",
            "-Dsun.java2d.d3d=false",
            "-Dsun.java2d.opengl=false",
        )

        nativeDistributions {
            // Gradle builds the package format supported by the current host OS.
            targetFormats(TargetFormat.Msi, TargetFormat.Deb, TargetFormat.Rpm, TargetFormat.Dmg)
            packageName = "StreamForge"
            packageVersion = "1.2.2"
            vendor = "StreamForge"
            description = "Desktop companion for authorized StreamForge playback sources"
            appResourcesRootDir.set(project.file("packagingDir"))



            windows {
                shortcut = true
                menu = true
                menuGroup = "StreamForge"
                // Unique GUID for MSI upgrade tracking
                upgradeUuid = "79b99e76-d12d-479b-8e43-e7056c81afec"
                // Use the logo for the installer and shortcut
                iconFile.set(project.file("src/desktopMain/resources/streamforge-logo.ico"))
            }
            
            macOS {
                iconFile.set(project.file("src/desktopMain/resources/streamforge-logo.png"))
            }
            
            linux {
                iconFile.set(project.file("src/desktopMain/resources/streamforge-logo.png"))
            }
        }
    }
}
