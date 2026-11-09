package bayern.kickner.buchstabierer

import android.content.Context
import java.io.File

/**
 * Where the Buchstabierer stores its data since version 7.0. The names are frozen: users who update from the
 * Play Store app keep their settings, history, own table and favourites only because these files are read as they
 * are. No other part of the Appothek may use these names, two DataStores on one file crash the app.
 */
object LegacyStorage {
    /** DataStore with table, name, debug switch, favourite rows, history and own table. */
    const val DATASTORE_NAME = "settings"

    /** SharedPreferences with the favourite texts, the pre-DataStore settings and the migration marker. */
    const val PREFS_NAME = "settings"

    /** True if the Buchstabierer has stored anything on this device, e.g. before the update to the Appothek. */
    fun exists(context: Context): Boolean =
        File(context.filesDir, "datastore/$DATASTORE_NAME.preferences_pb").exists() ||
            File(context.dataDir, "shared_prefs/$PREFS_NAME.xml").exists()
}
