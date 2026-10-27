import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val props = Properties().apply {
    val f = rootProject.file("gradle.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun p(k: String) = props.getProperty(k) ?: System.getenv(k) ?: ""

android {
    namespace = "com.venice.spy"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.venice.spy"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "SUPABASE_URL", "\"${p("SUPABASE_URL")}\"")
        buildConfigField("String", "SUPABASE_KEY", "\"${p("SUPABASE_ANON_KEY")}\"")
        buildConfigField("String", "DEVICE_NAME", "\"${p("DEVICE_NAME")}\"")
    }

    signingConfigs {
        create("rel") {
            storeFile = file("keystore.jks")
            storePassword = System.getenv("KS_PASS") ?: "venice123"
            keyAlias = System.getenv("KS_ALIAS") ?: "venice"
            keyPassword = System.getenv("KS_PASS") ?: "venice123"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("rel")
        }
    }

    buildFeatures { buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.camera:camera-core:1.3.4")
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("com.google.android.gms:play-services-location:21.3.0")
}