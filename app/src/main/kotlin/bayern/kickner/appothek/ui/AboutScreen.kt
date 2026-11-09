package bayern.kickner.appothek.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import bayern.kickner.appothek.R

private data class Credit(val name: String, val detail: String)

private val credits = listOf(
    Credit("Appothek", "Apache License 2.0 · github.com/nexus421/Appothek"),
    Credit("Free German Dictionary (Jan Schreiber)", "Public Domain · Wortliste der Zufallswörter"),
    Credit("Jetpack Compose, AndroidX", "Apache License 2.0 · Google"),
    Credit("Kotlin, kotlinx.coroutines", "Apache License 2.0 · JetBrains"),
    Credit("qrcode-kotlin", "MIT License · Rafael Lins (g0dkar)"),
    Credit("KotNexLib, Klogger", "nexus421"),
    Credit("InkTap NFC-Protokoll", "Rekonstruiert aus dem Waveshare SDK und Proxmark3 (cmdhfwaveshare.c)"),
)

/** Version and licences. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val version = context.packageManager.getPackageInfo(context.packageName, 0).versionName
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.appothek_about)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.appothek_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.app_name)) },
                supportingContent = { Text(stringResource(R.string.appothek_version, version ?: "")) },
            )
            HorizontalDivider()
            Text(
                stringResource(R.string.appothek_licences),
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
            )
            credits.forEach { ListItem(headlineContent = { Text(it.name) }, supportingContent = { Text(it.detail) }) }
        }
    }
}
