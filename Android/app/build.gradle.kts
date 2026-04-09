import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val keystoreProperties = Properties()
val keystorePropertiesFile = rootProject.file("keystore.properties")

if (keystorePropertiesFile.exists()) {
    keystorePropertiesFile.inputStream().use { keystoreProperties.load(it) }
}

fun propertyOrEnv(propertyName: String, envName: String): String? {
    return keystoreProperties.getProperty(propertyName)?.takeIf { it.isNotBlank() }
        ?: System.getenv(envName)?.takeIf { it.isNotBlank() }
}

android {
    namespace = "com.elik745i.isobotbt"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.elik745i.isobotbt"
        minSdk = 26
        targetSdk = 34
        versionCode = 4
        versionName = "1.2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val storeFilePath = propertyOrEnv("storeFile", "ISOBOT_KEYSTORE_FILE")
            if (!storeFilePath.isNullOrBlank()) {
                storeFile = rootProject.file(storeFilePath)
                storePassword = propertyOrEnv("storePassword", "ISOBOT_KEYSTORE_PASSWORD")
                keyAlias = propertyOrEnv("keyAlias", "ISOBOT_KEY_ALIAS")
                keyPassword = propertyOrEnv("keyPassword", "ISOBOT_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.webkit:webkit:1.15.0")
    implementation("com.google.android.material:material:1.12.0")
}
