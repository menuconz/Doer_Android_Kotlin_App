import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
}

// Load secrets from local.properties (gitignored) — falls back to empty string if missing
val localProperties = Properties().apply {
    val localFile = rootProject.file("local.properties")
    if (localFile.exists()) localFile.inputStream().use { load(it) }
}
val mapsApiKey: String = localProperties.getProperty("MAPS_API_KEY", "")

android {
    namespace = "nz.co.doer"
    compileSdk = 35

    defaultConfig {
        applicationId = "nz.co.doer"
        minSdk = 26
        targetSdk = 35
        versionCode = 67
        versionName = "2.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "BASE_URL", "\"https://doerapi.doer.nz/api/\"")
        buildConfigField("String", "MAPS_API_KEY", "\"$mapsApiKey\"")
        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey
        buildConfigField("String", "PAYMARK_URL", "\"https://uat.paymarkclick.co.nz/api/webpayments/paymentservice/rest/WPRequest\"")
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            // Use local API for emulator testing
            // buildConfigField("String", "BASE_URL", "\"http://10.0.2.2:8080/api/\"")
        }
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
        }
    }
}

// JVM Daemon toolchain — ensures consistent JDK across CLI and IDE builds
kotlin {
    jvmToolchain(21)
}

configurations.all {
    resolutionStrategy {
        force("org.jetbrains.kotlin:kotlin-stdlib:2.1.10")
        force("org.jetbrains.kotlin:kotlin-stdlib-jdk7:2.1.10")
        force("org.jetbrains.kotlin:kotlin-stdlib-jdk8:2.1.10")
    }
}

dependencies {
    // Core Android
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.ui.tooling)

    // Hilt DI
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.hilt.navigation.compose)

    // Network
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)

    // DataStore & Security
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.security.crypto)

    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.analytics)

    // Maps (Navigation SDK bundles its own play-services-maps)
    implementation(libs.maps.compose) {
        exclude(group = "com.google.android.gms", module = "play-services-maps")
    }
    implementation(libs.play.services.location)
    configurations.all {
        exclude(group = "com.google.android.gms", module = "play-services-maps")
    }

    // Image Loading
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    // Stripe Payments
    implementation(libs.stripe.android)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // WorkManager
    implementation(libs.work.runtime.ktx)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)

    // AppCompat (for Navigation SDK Activity)
    implementation("androidx.appcompat:appcompat:1.7.0")

    // Google Navigation SDK (turn-by-turn)
    api(libs.google.navigation)

    // Logging
    implementation(libs.timber)
}
