plugins {
    // The shared module intentionally has no UI or provider-specific implementation.
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    // Android retains its existing UI and can gradually adopt these common contracts.
    android {
        namespace = "com.aistudio.streamforge.shared"
        compileSdk = 36
        minSdk = 24
    }
    // The JVM target is consumed by Compose Desktop on Windows, Linux, and macOS.
    jvm("desktop")
    // Keep common JVM bytecode compatible with the Compose Desktop JDK 21 runtime.
    jvmToolchain(21)

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
            implementation(libs.kamel.image)
        }
        
        val desktopMain by getting {
            dependencies {
                implementation(compose.uiTooling)
            }
        }
    }
}