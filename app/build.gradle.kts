
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.rocktek.customlauncher"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.rocktek.customlauncher"
        minSdk = 26
        targetSdk = 32
        versionCode = 2
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}
