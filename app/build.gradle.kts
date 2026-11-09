plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val persistentKeystorePath = System.getenv("KEYSTORE_PATH")

android {
    namespace = "com.reet.music"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.reet.music"
        minSdk = 24
        targetSdk = 36
        versionCode = 6
        versionName = "1.3.2"
    }

    signingConfigs {
        if (!persistentKeystorePath.isNullOrBlank()) {
            create("persistent") {
                storeFile = file(persistentKeystorePath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        getByName("debug") {
            if (!persistentKeystorePath.isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("persistent")
            }
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
        compose = true
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.02.00"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.media3:media3-exoplayer:1.6.1")
    implementation("androidx.media3:media3-common:1.6.1")
    implementation("androidx.media3:media3-session:1.6.1")
    implementation("com.google.guava:guava:33.4.8-android")
    implementation("androidx.palette:palette-ktx:1.0.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
