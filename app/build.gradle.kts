plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val releaseSigningValues = listOf(
    providers.environmentVariable("ANDROID_KEYSTORE_PATH").orNull,
    providers.environmentVariable("ANDROID_KEYSTORE_PASSWORD").orNull,
    providers.environmentVariable("ANDROID_KEY_ALIAS").orNull,
    providers.environmentVariable("ANDROID_KEY_PASSWORD").orNull
)
val releaseSigningConfigured = releaseSigningValues.all { !it.isNullOrBlank() }
require(releaseSigningConfigured || releaseSigningValues.all { it.isNullOrBlank() }) {
    "Provide all Android release signing environment variables or none."
}

android {
    namespace = "com.niknolig.app"
    compileSdk = 35
    buildToolsVersion = "35.0.0"

    signingConfigs {
        if (releaseSigningConfigured) {
            create("release") {
                storeFile = file(releaseSigningValues[0]!!)
                storePassword = releaseSigningValues[1]
                keyAlias = releaseSigningValues[2]
                keyPassword = releaseSigningValues[3]
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    defaultConfig {
        applicationId = "com.niknolig.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        getByName("release") {
            if (releaseSigningConfigured) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}
