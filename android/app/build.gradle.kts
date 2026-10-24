import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "de.tobidervogel.aniworld"
    compileSdk = 34

    defaultConfig {
        applicationId = "de.tobidervogel.aniworld"
        // Nur fürs Nord 3 (Android 16). 34 spart alle Sonderwege für ältere Versionen.
        minSdk = 34
        // Bewusst 34 und nicht 36: Ab 35 begrenzt Android dataSync-Dienste auf 6 Stunden am Tag,
        // eine lange Staffel würde dann mitten im Download abbrechen.
        targetSdk = 34
        versionCode = 2
        versionName = "0.2.0"
    }

    signingConfigs {
        // Schlüssel liegt im Repo, damit Cloud, PC und jede spätere Sitzung gleich signieren.
        // Sonst lehnt Android ein Update mit "App nicht installiert" ab und man müsste neu installieren.
        create("eigen") {
            storeFile = file("aniworld.keystore")
            storePassword = "aniworld"
            keyAlias = "aniworld"
            keyPassword = "aniworld"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("eigen")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("eigen")
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
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
    lint {
        // Wie bei Live Up: Der Lint von AGP 8.4.1 rechnet mit Kotlin 2.0 und liest die stdlib von 2.3 falsch,
        // dann gilt jedes remember { liste.count { … } } als "gibt Unit zurück". Wieder an, sobald AGP neuer ist.
        disable += "RememberReturnType"
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    // Höchste Stände, die noch mit compileSdk 34 bauen (die nächsten verlangen 35).
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    // Voller Symbolsatz wie bei Live Up. Ohne R8 landet alles in der APK; für ein Gerät egal.
    implementation("androidx.compose.material:material-icons-extended")

    // Netz: DoH, weil deutsche Provider aniworld.to per DNS sperren (das Python-Original macht dasselbe).
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:okhttp-dnsoverhttps:4.12.0")
    // HTML lesen: Selektoren statt der find()-Ketten aus dem Python-Code, übersteht kleine Umbauten der Seite.
    implementation("org.jsoup:jsoup:1.18.3")
    // Cover laden, über denselben OkHttp-Client (DoH, Cookies).
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Player für Streams und fertige Downloads.
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.4.1")
    implementation("androidx.media3:media3-datasource-okhttp:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")

    testImplementation("junit:junit:4.13.2")
    // org.json ist auf dem Handy eingebaut, in JVM-Tests aber nur ein Stub.
    testImplementation("org.json:json:20250517")
}
