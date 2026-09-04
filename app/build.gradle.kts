plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.charizard.charify"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.jtcharizard.charify"
        minSdk = 26
        targetSdk = 35
        versionCode = 6
        versionName = "0.6.0"
    }

    signingConfigs {
        create("charifyDebug") {
            storeFile = file("charify-debug.keystore")
            storePassword = "charifydebug"
            keyAlias = "charify"
            keyPassword = "charifydebug"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("charifyDebug")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("charifyDebug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
