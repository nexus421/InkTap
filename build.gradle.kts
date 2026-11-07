// AGP 9 bundles an older Kotlin. Putting the Kotlin Gradle plugin on the classpath raises it (see ANDROID.md).
buildscript {
    dependencies {
        classpath(libs.kotlin.gradle.plugin)
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
