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
        versionCode = 3
        versionName = "1.1"
    }
}
