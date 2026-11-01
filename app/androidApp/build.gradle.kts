plugins {
    id("com.android.application")
    kotlin("plugin.compose")
}
android {
    namespace = "com.travel.app"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.travel.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    buildFeatures { compose = true }
}
dependencies {
    implementation(project(":composeApp"))
    implementation("androidx.activity:activity-compose:1.13.0")
}
