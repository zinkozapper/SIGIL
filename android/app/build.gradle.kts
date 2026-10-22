plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val apiBaseUrl = providers.gradleProperty("sigilApiBaseUrl")
    .orElse("http://100.64.0.1:3000/")
    .get()
    .let { if (it.endsWith("/")) it else "$it/" }

android {
    namespace = "com.sigil.client"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.sigil.client"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.work:work-runtime-ktx:2.9.1")
}
