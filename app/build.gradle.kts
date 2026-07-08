import java.util.Properties
plugins {
    alias(libs.plugins.android.application)
}

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { load(it) }
    }
}

val geminiApiKey = localProperties.getProperty("GEMINI_API_KEY") ?: ""
val geminiModel = localProperties.getProperty("GEMINI_MODEL") ?: "gemini-3.1-flash-lite"

android {
    namespace = "hcmute.com.smarteduapp"
    compileSdk = 35

    defaultConfig {
        applicationId = "hcmute.com.smarteduapp"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "GEMINI_API_KEY", "\"${geminiApiKey}\"")
        buildConfigField("String", "GEMINI_MODEL", "\"${geminiModel}\"")

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

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)
    implementation(libs.room.runtime)
    annotationProcessor(libs.room.compiler)
    implementation(libs.okhttp)
    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}
