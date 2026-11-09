package bayern.kickner.appothek

import android.app.Application
import bayern.kickner.klogger.KLogger

/** Application entry point. Sets up logging once for the whole process. */
class AppothekApp : Application() {
    override fun onCreate() {
        super.onCreate()
        KLogger.configure {
            logToConsole()
            debug = BuildConfig.DEBUG
        }
    }
}
