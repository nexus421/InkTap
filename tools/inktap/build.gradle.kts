plugins {
    id("appothek.tool")
}

android {
    namespace = "bayern.kickner.inktap"
}

dependencies {
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.qrcode.kotlin)
    implementation(libs.kotnexlib)
    implementation(libs.klogger)

    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.junit)
    testImplementation(libs.zxing.core)
}
