package bayern.kickner.inktap

import android.content.Context
import android.content.Intent
import android.nfc.NfcAdapter
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import bayern.kickner.appothek.core.Tool
import bayern.kickner.appothek.core.ToolAvailability
import bayern.kickner.appothek.core.ToolColors
import bayern.kickner.inktap.ui.screens.ink.InkAction
import bayern.kickner.inktap.ui.screens.ink.InkView
import bayern.kickner.inktap.ui.screens.ink.InkViewModel
import bayern.kickner.inktap.ui.screens.ink.NfcStatus

object InkTapTool : Tool {
    override val id = "inktap"
    override val nameRes = R.string.inktap_name
    override val descriptionRes = R.string.inktap_description
    override val motifRes = R.drawable.motif_inktap
    override val colors = ToolColors(
        light = Color(0xFF12A37A) to Color(0xFF0C7C8C),
        dark = Color(0xFF0D7559) to Color(0xFF095A66),
    )

    override fun availability(context: Context): ToolAvailability =
        if (NfcAdapter.getDefaultAdapter(context) == null) ToolAvailability.Unavailable(R.string.inktap_no_nfc)
        else ToolAvailability.Available

    /** What the former InkTap MainActivity did: load the inputs, NFC reader mode while the screen is resumed. */
    @Composable
    override fun Content(onBack: (() -> Unit)?) {
        val activity = LocalActivity.current ?: return
        val vm: InkViewModel = viewModel()
        LaunchedEffect(Unit) { vm.onAction(InkAction.Load, activity) }
        LifecycleResumeEffect(activity) {
            val nfc = NfcAdapter.getDefaultAdapter(activity)
            val status = when {
                nfc == null -> NfcStatus.Missing
                nfc.isEnabled.not() -> NfcStatus.Off
                else -> NfcStatus.Ready
            }
            vm.onAction(InkAction.NfcChanged(status), activity)
            // Only NFC-A, and no NDEF probing by the platform: the display is busy enough being powered by the field.
            nfc?.enableReaderMode(
                activity,
                { tag -> activity.runOnUiThread { vm.onAction(InkAction.TagFound(tag), activity) } },
                NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
                null,
            )
            onPauseOrDispose { nfc?.disableReaderMode(activity) }
        }
        InkView(
            state = vm.state,
            onAction = { vm.onAction(it, activity) },
            onOpenNfcSettings = { activity.startActivity(Intent(Settings.ACTION_NFC_SETTINGS)) },
            onBack = onBack,
        )
    }
}
