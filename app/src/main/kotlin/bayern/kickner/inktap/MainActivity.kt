package bayern.kickner.inktap

import android.content.Intent
import android.nfc.NfcAdapter
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import bayern.kickner.inktap.ui.screens.ink.InkAction
import bayern.kickner.inktap.ui.screens.ink.InkView
import bayern.kickner.inktap.ui.screens.ink.InkViewModel
import bayern.kickner.inktap.ui.screens.ink.NfcStatus

/** The only activity: theme, NFC reader mode and the ink screen. */
class MainActivity : ComponentActivity() {

    private val vm: InkViewModel by viewModels()
    private val adapter: NfcAdapter? by lazy { NfcAdapter.getDefaultAdapter(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        vm.onAction(InkAction.Load, this)
        setContent {
            val scheme = if (isSystemInDarkTheme()) dynamicDarkColorScheme(this) else dynamicLightColorScheme(this)
            MaterialTheme(colorScheme = scheme) {
                InkView(
                    state = vm.state,
                    onAction = { vm.onAction(it, this) },
                    onOpenNfcSettings = { startActivity(Intent(Settings.ACTION_NFC_SETTINGS)) },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val nfc = adapter
        val status = when {
            nfc == null -> NfcStatus.Missing
            nfc.isEnabled.not() -> NfcStatus.Off
            else -> NfcStatus.Ready
        }
        vm.onAction(InkAction.NfcChanged(status), this)
        // Only NFC-A, and no NDEF probing by the platform: the display is busy enough being powered by the field.
        nfc?.enableReaderMode(
            this,
            { tag -> runOnUiThread { vm.onAction(InkAction.TagFound(tag), this) } },
            NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
            null,
        )
    }

    override fun onPause() {
        super.onPause()
        adapter?.disableReaderMode(this)
    }
}
