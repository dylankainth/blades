plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    // Applies app/google-services.json to generate Firebase config at build time.
    // Build will fail without a real google-services.json in app/ — see README.md.
    id("com.google.gms.google-services")
}

android {
    namespace = "com.hackmit.twins"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.blade.app"
        // BLE background scanning APIs used here (ScanSettings, neverForLocation, etc.)
        // require Android 12 (S / API 31)+, which matches the hackathon scope.
        minSdk = 31
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Core / Compose
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")

    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Coil for loading matched-person photos from a URL in Compose.
    implementation("io.coil-kt:coil-compose:2.6.0")

    // Firebase (BoM keeps versions aligned across the individual artifacts).
    implementation(platform("com.google.firebase:firebase-bom:33.1.2"))
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-functions-ktx")
    implementation("com.google.firebase:firebase-messaging-ktx")

    // Facebook Login (public_profile only — see OnboardingScreen.kt).
    implementation("com.facebook.android:facebook-login:17.0.0")

    // Google Sign-In (classic GoogleSignInClient API — simpler to wire up
    // than Credential Manager for this scope). See auth/AuthManager.kt.
    implementation("com.google.android.gms:play-services-auth:21.2.0")

    // QR scanner for pairing a Kindred badge. Runs inside Play services, so
    // the app needs no CAMERA permission and no camera code of its own.
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
