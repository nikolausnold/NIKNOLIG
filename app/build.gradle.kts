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
val configuredVersionCode = providers.environmentVariable("ANDROID_VERSION_CODE").orNull
val appVersionCode = configuredVersionCode?.toIntOrNull() ?: 1
require(releaseSigningConfigured || releaseSigningValues.all { it.isNullOrBlank() }) {
    "Provide all Android release signing environment variables or none."
}
require(
    configuredVersionCode == null ||
        configuredVersionCode.toIntOrNull()?.let { it > 0 } == true
) {
    "ANDROID_VERSION_CODE must be a positive integer."
}

android {
    namespace = "com.niknolig.app"
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    signingConfigs {
        if (releaseSigningConfigured) {
            create("release") {
                storeFile = file(releaseSigningValues[0]!!)
                storeType = "PKCS12"
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
        targetSdk = 36
        versionCode = appVersionCode
        versionName = providers.environmentVariable("ANDROID_VERSION_NAME").orElse("0.1.0").get()
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
