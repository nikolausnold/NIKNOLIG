import java.net.URI

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val releaseVersionName = providers.environmentVariable("VERSION_NAME").orNull ?: "0.1.0"
val releaseVersionCode = providers.environmentVariable("VERSION_CODE").orNull?.toIntOrNull() ?: 1
val releaseStoreFile = providers.environmentVariable("ANDROID_KEYSTORE_PATH").orNull
val releaseStorePassword = providers.environmentVariable("ANDROID_KEYSTORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("ANDROID_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("ANDROID_KEY_PASSWORD").orNull
val releaseSigningConfigured = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).all { !it.isNullOrBlank() }
val backendBaseUrl = providers.environmentVariable("NIKNOLIG_BACKEND_BASE_URL").orNull.orEmpty()
if (backendBaseUrl.isNotEmpty()) {
    val endpoint = runCatching { URI(backendBaseUrl) }.getOrNull()
    require(
        endpoint != null && endpoint.scheme == "https" && !endpoint.host.isNullOrBlank() &&
            endpoint.rawAuthority == endpoint.host && endpoint.rawPath in listOf("", "/") &&
            endpoint.rawQuery == null && endpoint.rawFragment == null
    ) { "NIKNOLIG_BACKEND_BASE_URL must be an HTTPS origin without credentials, port, path, query or fragment" }
}

android {
    namespace = "com.niknolig.app"
    compileSdk = 35
    buildToolsVersion = "35.0.0"
    buildFeatures {
        buildConfig = true
    }

    signingConfigs {
        if (releaseSigningConfigured) {
            create("release") {
                storeFile = file(requireNotNull(releaseStoreFile))
                storePassword = requireNotNull(releaseStorePassword)
                keyAlias = requireNotNull(releaseKeyAlias)
                keyPassword = requireNotNull(releaseKeyPassword)
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
        versionCode = releaseVersionCode
        versionName = releaseVersionName
        buildConfigField("String", "BACKEND_BASE_URL", "\"$backendBaseUrl\"")
    }

    dependencies {
        testImplementation("junit:junit:4.13.2")
    }

    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.findByName("release")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}
