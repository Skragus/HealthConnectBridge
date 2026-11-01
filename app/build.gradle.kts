import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    id("kotlin-parcelize")
    alias(libs.plugins.ksp)
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(FileInputStream(localPropertiesFile))
}

android {
    namespace = "com.example.healthconnectbridge"
    compileSdk = 36

    defaultConfig {
        applicationId = providers.gradleProperty("bridgeApplicationId").orElse("com.example.healthconnectbridge").get()
        minSdk = 29
        targetSdk = 34
        versionCode = 2
        versionName = "2.0-historical"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    flavorDimensions += "environment"
    productFlavors {
        create("staging") {
            dimension = "environment"
            applicationIdSuffix = ".staging"
            val key = localProperties.getProperty("apiKey")?.replace("\"", "") ?: ""
            val url = localProperties.getProperty("baseUrl")?.replace("\"", "") ?: "https://health-ingest-api-production.up.railway.app/"
            buildConfigField("String", "BASE_URL", "\"$url\"")
            buildConfigField("String", "API_KEY", "\"$key\"")
            buildConfigField("Boolean", "WORKER_PROOF_OF_LIFE_ENABLED", "true")
        }
        create("prod") {
            dimension = "environment"
            val key = localProperties.getProperty("apiKey")?.replace("\"", "") ?: ""
            val url = localProperties.getProperty("baseUrl")?.replace("\"", "") ?: "https://health-ingest-api-production.up.railway.app/"
            buildConfigField("String", "BASE_URL", "\"$url\"")
            buildConfigField("String", "API_KEY", "\"$key\"")
            buildConfigField("Boolean", "WORKER_PROOF_OF_LIFE_ENABLED", "false")
        }
    }

    kotlinOptions { jvmTarget = "11" }

    testOptions { unitTests.isIncludeAndroidResources = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation("androidx.health.connect:connect-client:1.2.0-alpha02")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation("org.jetbrains.kotlin:kotlin-parcelize-runtime")
    
    // Networking & Serialization
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.gson)

    // Background Work
    implementation(libs.androidx.work.runtime.ktx)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.junit)
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.work:work-testing:2.9.0")
    testImplementation("androidx.test:core:1.6.1")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}