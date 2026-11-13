plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "com.dex.security"
    compileSdk = 35
    defaultConfig { applicationId = "com.dex.security"; minSdk = 26; targetSdk = 35; versionCode = 1; versionName = "0.1.0" }
    signingConfigs {
        create("dexRelease") {
            storeFile = file("release.keystore")
            storePassword = System.getenv("DEX_STORE_PASSWORD") ?: ""
            keyAlias = System.getenv("DEX_KEY_ALIAS") ?: ""
            keyPassword = System.getenv("DEX_KEY_PASSWORD") ?: ""
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            if (file("release.keystore").exists()) signingConfig = signingConfigs.getByName("dexRelease")
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies { implementation("androidx.core:core:1.13.1") }
