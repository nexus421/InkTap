import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/** Android library defaults shared by `:core` and every tool module. */
class AppothekLibraryPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.library")
        extensions.configure<LibraryExtension> {
            compileSdk = 37
            defaultConfig.minSdk = 35
            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_17
                targetCompatibility = JavaVersion.VERSION_17
            }
            // Unit tests may render text with AWT (InkTap), see its TestSupport.kt.
            testOptions.unitTests.all { it.systemProperty("java.awt.headless", "true") }
        }
    }
}
