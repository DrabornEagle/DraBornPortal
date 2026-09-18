plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.draborneagle.drabornportal"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.draborneagle.drabornportal"
        minSdk = 23
        targetSdk = 36
        versionCode = 1
        versionName = "0.4.2"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("dkdRelease") {
            val dkdStorePath = System.getenv("DKD_KEYSTORE_PATH")
            if (!dkdStorePath.isNullOrBlank()) {
                storeFile = file(dkdStorePath)
                storePassword = System.getenv("DKD_ANDROID_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("DKD_ANDROID_KEY_ALIAS")
                keyPassword = System.getenv("DKD_ANDROID_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (!System.getenv("DKD_KEYSTORE_PATH").isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("dkdRelease")
            }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.ui:ui:1.12.1")
    implementation("androidx.compose.ui:ui-tooling-preview:1.12.1")
    implementation("androidx.compose.foundation:foundation:1.12.1")
    implementation("androidx.compose.material3:material3:1.4.0")
    debugImplementation("androidx.compose.ui:ui-tooling:1.12.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("com.google.mlkit:translate:17.0.3")
    testImplementation("junit:junit:4.13.2")
}
