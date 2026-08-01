import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    kotlin("android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "io.github.recloudstudio.sleepyxposed"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.recloudstudio.sleepyxposed"
        minSdk = 24
        targetSdk = 35
        versionCode = 2
        versionName = "2.0"
        buildConfigField("int", "XPOSED_API", "101")
        buildConfigField("String", "MODULE_CHANNEL", "\"release\"")

        // Phones only — drop x86 emulator ABIs from the APK
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }

    buildTypes {
        debug {
            // Still shrink so debug APKs used for sideload stay small/fast.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
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

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            merges += "META-INF/xposed/*"
            excludes +=
                setOf(
                    "/META-INF/{AL2.0,LGPL2.1}",
                    "META-INF/*.kotlin_module",
                    "META-INF/*.version",
                    "META-INF/LICENSE*",
                    "META-INF/NOTICE*",
                    "DebugProbesKt.bin",
                    "kotlin-tooling-metadata.json"
                )
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.02.00")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    // Prefer lighter lifecycle API used via ProcessLifecycle / Activity — compose helper is tiny
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.runtime:runtime")
    implementation("androidx.compose.material3:material3")
    // IMPORTANT: do NOT add material-icons-extended (tens of MB of icon vectors in DEX)

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    compileOnly("io.github.libxposed:api:101.0.1")
    compileOnly("de.robv.android.xposed:api:82")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-tooling-preview")
    testImplementation("junit:junit:4.13.2")
}
