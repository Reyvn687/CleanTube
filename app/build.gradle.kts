plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// API açarları build zamanı verilir (ikinci açar ixtiyaridir; birincinin limiti bitəndə ona keçilir).
// Mənbə: GitHub secret-ləri (YOUTUBE_API_KEY, YOUTUBE_API_KEY_2) və ya layihə kökündəki
// youtube_api_key.txt faylı (hər sətirdə bir açar, "#" ilə başlayan sətirlər sayılmır).
val fileKeys: List<String> = rootProject.file("youtube_api_key.txt").let { f ->
    if (f.exists()) {
        f.readLines()
            .map { it.trim().trim('"') }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
    } else emptyList()
}
val ytKey1: String = System.getenv("YOUTUBE_API_KEY")?.trim().orEmpty()
    .ifEmpty { fileKeys.getOrNull(0).orEmpty() }
val ytKey2: String = System.getenv("YOUTUBE_API_KEY_2")?.trim().orEmpty()
    .ifEmpty { fileKeys.getOrNull(1).orEmpty() }

android {
    namespace = "com.cleantube"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.cleantube"
        minSdk = 24
        targetSdk = 34
        versionCode = 6
        versionName = "4.1"
        buildConfigField("String", "YOUTUBE_API_KEY", "\"$ytKey1\"")
        buildConfigField("String", "YOUTUBE_API_KEY_2", "\"$ytKey2\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("com.pierfrancescosoffritti.androidyoutubeplayer:core:13.0.0")
}
