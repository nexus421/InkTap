plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// The release workflow passes the tag as -PversionName=1.2.3. Local builds fall back to the default.
val appVersionName: String = providers.gradleProperty("versionName").getOrElse("1.0.0")

// Release signing comes from the environment only, never from files in this repository.
// Without INKTAP_KEYSTORE_FILE the release build stays unsigned (fine for local R8 checks).
val releaseKeystore: String? = System.getenv("INKTAP_KEYSTORE_FILE")

android {
    namespace = "bayern.kickner.inktap"
    compileSdk = 37

    defaultConfig {
        applicationId = "bayern.kickner.inktap"
        minSdk = 35
        targetSdk = 37
        versionName = appVersionName
        versionCode = appVersionName.split(".").map(String::toInt).let { (major, minor, patch) ->
            major * 10_000 + minor * 100 + patch
        }
        buildConfigField("long", "BUILD_TIME", "${System.currentTimeMillis()}L")
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = System.getenv("INKTAP_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("INKTAP_KEY_ALIAS")
                keyPassword = System.getenv("INKTAP_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            resValue("string", "app_name", "InkTap Debug")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            resValue("string", "app_name", "InkTap")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
        resValues = true
    }

    // Unit tests render text with AWT, see TestSupport.kt.
    testOptions.unitTests.all { it.systemProperty("java.awt.headless", "true") }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.qrcode.kotlin)
    implementation(libs.kotnexlib)
    implementation(libs.klogger)

    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.junit)
    testImplementation(libs.zxing.core)
}
