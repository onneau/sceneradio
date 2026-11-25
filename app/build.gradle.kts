import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "io.github.onneau.sceneradio"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.onneau.sceneradio"
        minSdk = 24
        targetSdk = 36
        versionCode = (project.findProperty("appVersionCode") as String?)?.toInt() ?: 1
        versionName = (project.findProperty("appVersionName") as String?) ?: "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Signing-gegevens komen uit omgevingsvariabelen (GitHub Actions zet die, zie README).
    // Zonder KEYSTORE_PASSWORD wordt de release-build niet ondertekend.
    val keystorePassword: String? = System.getenv("KEYSTORE_PASSWORD")

    signingConfigs {
        if (!keystorePassword.isNullOrBlank()) {
            create("release") {
                storeFile = System.getenv("KEYSTORE_PATH")?.let { file(it) }
                    ?: rootProject.file("keystore/sceneradio.jks")
                storePassword = keystorePassword
                keyAlias = System.getenv("KEY_ALIAS") ?: "sceneradio"
                keyPassword = keystorePassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfigs.findByName("release")?.let { signingConfig = it }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isDebuggable = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")

    // Jetpack Compose & Material 3
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")

    // Media3 (alle drie exact 1.11.1)
    implementation("androidx.media3:media3-exoplayer:1.11.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.11.1")
    implementation("androidx.media3:media3-session:1.11.1")

    // Room Database 2.8.5 via KSP (alleen runtime en compiler, zelfde versie, geen ktx)
    implementation("androidx.room:room-runtime:2.8.5")
    ksp("androidx.room:room-compiler:2.8.5")

    // DataStore Preferences voor laatst gekozen station
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // Coil Image Loading voor zenderlogo's
    implementation("io.coil-kt:coil-compose:2.5.0")

    // OkHttp & Gson voor Radio Browser, de zenderlijst en de updates
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}
