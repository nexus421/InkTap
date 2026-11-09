plugins {
    id("appothek.tool")
}

android {
    namespace = "bayern.kickner.buchstabierer"
}

dependencies {
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.datastore.preferences)

    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.junit)
}
