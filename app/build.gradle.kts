import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.gms.google.services)
}

// Sprint 2 (Story 004): the Finnhub API key is a secret, so it comes from
// local.properties (already gitignored for the Android SDK path) rather
// than being hardcoded in source. Add a line like:
//   FINNHUB_API_KEY=your_key_here
// to your own local.properties -- see README.md.
val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(FileInputStream(localPropertiesFile))
}
val finnhubApiKey: String = localProperties.getProperty("FINNHUB_API_KEY", "")
// Stories 005/006: where the PortfolioIQ AI server (ai-server/) runs.
// 10.0.2.2 is the laptop as seen from the Android emulator. The token is
// optional and must match PIQ_SERVER_TOKEN in ai-server/.env if set.
val aiServerUrl: String = localProperties.getProperty("AI_SERVER_URL", "http://10.0.2.2:8000")
val aiServerToken: String = localProperties.getProperty("AI_SERVER_TOKEN", "")

android {
    namespace = "com.portfolioiq"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.portfolioiq"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "FINNHUB_API_KEY", "\"$finnhubApiKey\"")
        buildConfigField("String", "AI_SERVER_URL", "\"$aiServerUrl\"")
        buildConfigField("String", "AI_SERVER_TOKEN", "\"$aiServerToken\"")
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.credentials)
    implementation(libs.credentials.play.services.auth)
    implementation(libs.googleid)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)

    // Sprint 1 auth (Firebase). Requires: Tools > Firebase > Authentication /
    // Firestore in Android Studio to generate google-services.json and apply
    // the google-services plugin -- that step needs your own Google account,
    // so it's not done here. See README.md.
    implementation(platform("com.google.firebase:firebase-bom:33.1.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
}
