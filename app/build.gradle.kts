plugins {
    alias(libs.plugins.android.application)
}

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
