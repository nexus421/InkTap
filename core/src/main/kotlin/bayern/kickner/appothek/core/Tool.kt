package bayern.kickner.appothek.core

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable

/**
 * One tool of the Appothek. Register a new tool in `app/.../Tools.kt` and give it its activity and launcher alias
 * in the app manifest. Tools only know `:core`, never each other.
 */
interface Tool {
    /** Stable lowercase id, used for settings, shortcuts and launcher entries. Never change it. */
    val id: String

    @get:StringRes
    val nameRes: Int

    /** One short line shown on the start screen card. */
    @get:StringRes
    val descriptionRes: Int

    val colors: ToolColors

    /** Vector drawable used as the large faded motif on the card. */
    @get:DrawableRes
    val motifRes: Int

    fun availability(context: Context): ToolAvailability

    /**
     * The whole tool screen. [onBack] is set when the tool runs inside the Appothek start screen and should show a
     * back arrow; it is null when the tool runs as its own app.
     */
    @Composable
    fun Content(onBack: (() -> Unit)?)
}

sealed interface ToolAvailability {
    data object Available : ToolAvailability

    /** The tool cannot run on this device at all, [reasonRes] tells the user why. */
    data class Unavailable(@param:StringRes val reasonRes: Int) : ToolAvailability
}
