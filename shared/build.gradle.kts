import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    android {
        namespace = "com.aistudio.streamforge.shared"
        compileSdk = 36
        minSdk = 24
    }
    
    jvm("desktop")
    
    // Configurazione XCFramework per iOS
    val xcf = XCFramework("Shared")
    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
            xcf.add(this)
            // Fix bundle ID warning
            freeCompilerArgs += listOf("-Xbinary=bundleId=com.example.streamforge.shared")
        }
    }

    jvmToolchain(21)

    targets.configureEach {
        compilations.configureEach {
            compileTaskProvider.configure {
                compilerOptions {
                    freeCompilerArgs.add("-Xexpect-actual-classes")
                }
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
            implementation(compose.components.resources)
            
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.kotlinx.serialization.json)
            implementation("com.fleeksoft.ksoup:ksoup:0.2.1")
            implementation(libs.kamel.image)
        }
        
        androidMain.dependencies {
            implementation("io.ktor:ktor-client-okhttp:${libs.versions.ktor.get()}")
            implementation("androidx.media3:media3-exoplayer:${libs.versions.media3.get()}")
            implementation("androidx.media3:media3-ui:${libs.versions.media3.get()}")
            implementation("androidx.media3:media3-exoplayer-hls:${libs.versions.media3.get()}")
            implementation("androidx.media3:media3-common:${libs.versions.media3.get()}")
        }
        
        val desktopMain by getting {
            dependencies {
                implementation(compose.uiTooling)
                implementation("io.ktor:ktor-client-okhttp:${libs.versions.ktor.get()}")
            }
        }
        
        iosMain.dependencies {
            implementation("io.ktor:ktor-client-darwin:${libs.versions.ktor.get()}")
        }
    }
}