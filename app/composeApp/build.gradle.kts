import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
    kotlin("plugin.compose")
    kotlin("plugin.serialization")
    id("org.jetbrains.compose")
    id("com.android.kotlin.multiplatform.library")
}

kotlin {
    android {
        namespace = "com.travel.app.shared"
        compileSdk = 37
        minSdk = 26
        androidResources.enable = true
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    jvm("desktop") { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }
    listOf(iosArm64(), iosSimulatorArm64()).forEach {
        it.binaries.framework { baseName = "TravelShared"; isStatic = true }
    }
    sourceSets {
        commonMain.dependencies {
            implementation("org.jetbrains.compose.runtime:runtime:1.12.1")
            implementation("org.jetbrains.compose.foundation:foundation:1.12.1")
            implementation("org.jetbrains.compose.ui:ui:1.12.1")
            implementation("org.jetbrains.compose.material3:material3:1.9.0")
            implementation("org.jetbrains.compose.material:material-icons-extended:1.7.3")
            implementation("org.jetbrains.compose.components:components-resources:1.12.1")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
            implementation("io.ktor:ktor-client-core:3.6.0")
            implementation("io.ktor:ktor-client-content-negotiation:3.6.0")
            implementation("io.ktor:ktor-serialization-kotlinx-json:3.6.0")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("io.ktor:ktor-client-mock:3.6.0")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
        }
        androidMain.dependencies {
            implementation("io.ktor:ktor-client-okhttp:3.6.0")
            implementation("androidx.activity:activity-compose:1.13.0")
        }
        iosMain.dependencies { implementation("io.ktor:ktor-client-darwin:3.6.0") }
        getByName("desktopMain") {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.11.0")
                implementation("io.ktor:ktor-client-cio:3.6.0")
            }
        }
    }
}
compose.resources { packageOfResClass = "com.travel.app.generated.resources"; publicResClass = true }
