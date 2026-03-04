// app/build.gradle.kts
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    // Entfernen Sie: id("org.jetbrains.kotlin.plugin.compose") oder alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.wetterradar"
    compileSdk = 34 // Stellen Sie sicher, dass dies eine stabile Version ist, z.B. 34

    defaultConfig {
        applicationId = "com.wetterradar"
        minSdk = 21
        targetSdk = 34 // Stellen Sie sicher, dass dies eine stabile Version ist, z.B. 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8 // oder VERSION_11, wenn Sie Java 11 Features nutzen
        targetCompatibility = JavaVersion.VERSION_1_8 // oder VERSION_11
    }
    kotlinOptions {
        jvmTarget = "1.8" // oder "11", entsprechend
    }

    // Konfiguration zur Behebung des META-INF-Fehler
    packaging {
        resources {
            excludes += "/META-INF/{DEPENDENCIES, LICENSE, NOTICE, NOTICE.md, LICENSE.md}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.10.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    implementation("com.google.android.gms:play-services-location:21.3.0")
    //implementation("com.google.android.gms:play-services-location-ktx:21.3.0")
// Oder eine neuere Version
    implementation("org.osmdroid:osmdroid-android:6.1.20") // Oder eine andere stabile Version >= 6.1.0

    implementation("com.squareup.okhttp3:okhttp:4.12.0") // Oder eine neuere Version, z.B. 5.x.y (mit Anpassungen)
    // -> NEU: für bzip2-Unterstützung
    implementation("org.apache.commons:commons-compress:1.26.2") // oder eine neuere Version

    implementation ("androidx.preference:preference:1.2.1") // Oder eine neuere Version
}
