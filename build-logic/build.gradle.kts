plugins {
    `kotlin-dsl`
}

dependencies {
    compileOnly(libs.android.gradle.plugin)
    compileOnly(libs.kotlin.gradle.plugin)
    compileOnly(libs.compose.compiler.gradle.plugin)
}

gradlePlugin {
    plugins {
        register("appothekLibrary") {
            id = "appothek.android.library"
            implementationClass = "AppothekLibraryPlugin"
        }
        register("appothekTool") {
            id = "appothek.tool"
            implementationClass = "AppothekToolPlugin"
        }
    }
}
