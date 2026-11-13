plugins { id("com.android.application") }
android {
    namespace = "com.dex.security"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.dex.security.qrdemo"
        minSdk = 26
        targetSdk = 28
        versionCode = 2
        versionName = "0.2.0-demo"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
dependencies {
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
}
