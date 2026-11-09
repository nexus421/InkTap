plugins {
    id("appothek.android.library")
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "bayern.kickner.appothek.core"
    buildFeatures.compose = true
}

dependencies {
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.material3)

    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.junit)
}
