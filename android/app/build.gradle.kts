plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// FCM is enabled only when you add your own google-services.json (docs/FCM_SETUP.md).
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

android {
    namespace = "com.homepager"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.homepager"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    // family = sideloaded, every trigger incl. volume-key accessibility service.
    // play   = Play Store-safe subset (no accessibility key filter, no direct SMS).
    flavorDimensions += "dist"
    productFlavors {
        create("family") {
            dimension = "dist"
            applicationIdSuffix = ".family"
            buildConfigField("boolean", "VOLUME_TRIGGER", "true")
            buildConfigField("boolean", "DIRECT_SMS", "true")
            manifestPlaceholders["cleartext"] = "true" // home server over plain http on LAN/VPN
        }
        create("play") {
            dimension = "dist"
            buildConfigField("boolean", "VOLUME_TRIGGER", "false")
            buildConfigField("boolean", "DIRECT_SMS", "false")
            manifestPlaceholders["cleartext"] = "false"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.android.gms:play-services-nearby:19.3.0")
    implementation(platform("com.google.firebase:firebase-bom:33.5.1"))
    implementation("com.google.firebase:firebase-messaging")
    testImplementation("junit:junit:4.13.2")
}
