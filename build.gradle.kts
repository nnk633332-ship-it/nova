plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.nova.assistant"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.nova.assistant"
        minSdk = 26
        targetSdk = 35
        versionCode = 10
        versionName = "10.0-repair"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}
