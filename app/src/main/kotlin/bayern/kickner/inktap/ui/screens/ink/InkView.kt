package bayern.kickner.inktap.ui.screens.ink

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import bayern.kickner.inktap.R
import bayern.kickner.inktap.core.BarcodeFormat
import bayern.kickner.inktap.core.Content
import bayern.kickner.inktap.core.ContentError
import bayern.kickner.inktap.core.Panel
import bayern.kickner.inktap.core.MonoImage
import bayern.kickner.inktap.core.WriteError
import bayern.kickner.inktap.core.render
import bayern.kickner.inktap.platform.AndroidText
import kotnexlib.ResultOf2

/** Paper color of the e-paper preview, the same as [MonoImage.toArgb] uses. */
private val Paper = Color(0xFFF4F4F0)

/** Highest the preview may get. The 2.13 inch preview stays below it on phones, the 4.2 inch one is capped. */
private val PREVIEW_MAX_HEIGHT = 200.dp

/**
 * The only screen. The header with preview and NFC status stays in place, only the mode chips and input fields
 * below it scroll. So the preview and the progress stay visible while the keyboard is open.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InkView(state: InkState, onAction: (InkAction) -> Unit, onOpenNfcSettings: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(state.nfc) {
        when (state.nfc) {
            NfcStatus.Done -> haptic.performHapticFeedback(HapticFeedbackType.Confirm)
            is NfcStatus.Failed, NfcStatus.FixContentFirst -> haptic.performHapticFeedback(HapticFeedbackType.Reject)
            NfcStatus.Missing, NfcStatus.Off, NfcStatus.Ready, is NfcStatus.Writing -> Unit
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.app_name))
                        Text(
                            stringResource(R.string.title_panel, state.panel.inches),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                },
                actions = { OverflowMenu(state.panel, state.rotate180, onAction) },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding(),
        ) {
            Header(state, onOpenNfcSettings, Modifier.padding(horizontal = 16.dp))
            Spacer(Modifier.height(12.dp))
            ModeChips(state.mode, onAction)
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Fields(state, onAction)
            }
        }
    }
}

@Composable
private fun OverflowMenu(panel: Panel, rotate180: Boolean, onAction: (InkAction) -> Unit) {
    var open by remember { mutableStateOf(false) }
    var choosePanel by remember { mutableStateOf(false) }
    if (choosePanel) {
        PanelDialog(
            selected = panel,
            onSelect = {
                choosePanel = false
                onAction(InkAction.SelectPanel(it))
            },
            onDismiss = { choosePanel = false },
        )
    }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.menu_more))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_display, panel.inches)) },
                onClick = {
                    open = false
                    choosePanel = true
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_rotate)) },
                trailingIcon = { Checkbox(checked = rotate180, onCheckedChange = null) },
                onClick = { onAction(InkAction.Rotate180(rotate180.not())) },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_test_pattern)) },
                onClick = {
                    open = false
                    onAction(InkAction.ArmTestPattern)
                },
            )
        }
    }
}

/** All displays answer with the same UID, so the user picks the model once. The choice is stored. */
@Composable
private fun PanelDialog(selected: Panel, onSelect: (Panel) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_display_title)) },
        text = {
            Column(Modifier.selectableGroup()) {
                Panel.entries.forEach { panel ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = panel == selected, role = Role.RadioButton, onClick = { onSelect(panel) })
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = panel == selected, onClick = null)
                        Spacer(Modifier.size(12.dp))
                        Text(stringResource(R.string.display_option, panel.inches, panel.width, panel.height))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.dialog_display_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_close)) }
        },
    )
}

/** Preview plus status: the part that never scrolls. */
@Composable
private fun Header(state: InkState, onOpenNfcSettings: () -> Unit, modifier: Modifier = Modifier) {
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Preview(state)
            if (state.showsPictureFailure() && state.preview != null) {
                Text(
                    stringResource(R.string.picture_failed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Status(state, onOpenNfcSettings)
        }
    }
}

@Composable
private fun Preview(state: InkState) {
    val shape = RoundedCornerShape(6.dp)
    // Capped height: a 4:3 panel would otherwise push the input fields away while the keyboard is open.
    val frame = Modifier
        .heightIn(max = PREVIEW_MAX_HEIGHT)
        .aspectRatio(state.panel.width.toFloat() / state.panel.height)
        .clip(shape)
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { PreviewContent(state, frame) }
}

@Composable
private fun PreviewContent(state: InkState, frame: Modifier) {
    val preview = state.preview
    if (preview != null) {
        val bitmap = remember(preview) {
            Bitmap.createBitmap(preview.toArgb(), preview.width, preview.height, Bitmap.Config.ARGB_8888).asImageBitmap()
        }
        Image(
            bitmap = bitmap,
            contentDescription = stringResource(R.string.preview_description),
            modifier = frame.background(Paper),
            contentScale = ContentScale.Fit,
            filterQuality = FilterQuality.None,
        )
        return
    }
    val message = problemText(state)
    val isError = state.problem != ContentError.Empty || state.showsPictureFailure()
    Box(
        frame.background(MaterialTheme.colorScheme.surfaceVariant).padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            message,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A failed picture only matters while the picture mode is shown. */
private fun InkState.showsPictureFailure(): Boolean = pictureFailed && mode == Mode.PICTURE && testPattern.not()

@Composable
private fun problemText(state: InkState): String = when {
    state.showsPictureFailure() -> stringResource(R.string.picture_failed)
    else -> when (state.problem) {
        ContentError.Empty, null ->
            if (state.mode == Mode.PICTURE) stringResource(R.string.hint_pick_picture) else stringResource(R.string.hint_empty)
        ContentError.QrTooLong -> stringResource(R.string.error_qr_too_long)
        ContentError.BarcodeTooLong -> stringResource(R.string.error_barcode_too_long)
        ContentError.BarcodeInvalidChars -> stringResource(R.string.error_barcode_chars)
        ContentError.TextTooLong -> stringResource(R.string.error_text_too_long)
    }
}

@Composable
private fun Status(state: InkState, onOpenNfcSettings: () -> Unit) {
    val nfc = state.nfc
    val colors = MaterialTheme.colorScheme
    val (icon, tint) = when (nfc) {
        NfcStatus.Done -> Icons.Default.CheckCircle to colors.primary
        NfcStatus.Off, NfcStatus.Missing, NfcStatus.FixContentFirst, is NfcStatus.Failed -> Icons.Default.ErrorOutline to colors.error
        NfcStatus.Ready, is NfcStatus.Writing -> Icons.Default.Nfc to colors.primary
    }
    val text = when (nfc) {
        NfcStatus.Missing -> stringResource(R.string.nfc_missing)
        NfcStatus.Off -> stringResource(R.string.nfc_off)
        NfcStatus.Ready -> if (state.testPattern) stringResource(R.string.hint_test_pattern) else stringResource(R.string.nfc_ready)
        is NfcStatus.Writing -> stringResource(R.string.nfc_writing)
        NfcStatus.Done -> stringResource(R.string.nfc_done)
        NfcStatus.FixContentFirst -> stringResource(R.string.nfc_fix_content)
        is NfcStatus.Failed -> when (nfc.error) {
            is WriteError.ConnectionLost -> stringResource(R.string.nfc_lost)
            is WriteError.Rejected -> stringResource(R.string.nfc_rejected)
            is WriteError.RefreshTimeout -> stringResource(R.string.nfc_timeout)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (nfc is NfcStatus.Writing) {
            LinearProgressIndicator(progress = { nfc.percent / 100f }, modifier = Modifier.fillMaxWidth())
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
            Spacer(Modifier.size(12.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        }
        if (nfc == NfcStatus.Off) {
            FilledTonalButton(onClick = onOpenNfcSettings, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.nfc_open_settings))
            }
        }
    }
}

/** One swipeable row with all seven modes. Starts scrolled to the selected one, so it is always visible. */
@Composable
private fun ModeChips(selected: Mode, onAction: (InkAction) -> Unit) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = selected.ordinal)
    LazyRow(
        state = listState,
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(Mode.entries) { mode ->
            FilterChip(
                selected = mode == selected,
                onClick = { onAction(InkAction.SelectMode(mode)) },
                label = { Text(stringResource(mode.label())) },
            )
        }
    }
}

private fun Mode.label(): Int = when (this) {
    Mode.QR -> R.string.mode_qr
    Mode.QR_NOTE -> R.string.mode_qr_note
    Mode.QR_CONTENT -> R.string.mode_qr_content
    Mode.TWO_QR -> R.string.mode_two_qr
    Mode.TEXT -> R.string.mode_text
    Mode.BARCODE -> R.string.mode_barcode
    Mode.PICTURE -> R.string.mode_picture
}

@Composable
private fun Fields(state: InkState, onAction: (InkAction) -> Unit) {
    when (state.mode) {
        Mode.QR, Mode.QR_CONTENT -> QrField(state.qr, R.string.field_qr) { onAction(InkAction.EditQr(it)) }
        Mode.QR_NOTE -> {
            QrField(state.qr, R.string.field_qr) { onAction(InkAction.EditQr(it)) }
            InputField(state.note, R.string.field_note, maxLines = 4) { onAction(InkAction.EditNote(it)) }
        }
        Mode.TWO_QR -> {
            QrField(state.qr, R.string.field_qr_left) { onAction(InkAction.EditQr(it)) }
            QrField(state.qr2, R.string.field_qr_right) { onAction(InkAction.EditQr2(it)) }
        }
        Mode.TEXT -> InputField(state.text, R.string.field_text, maxLines = 6) { onAction(InkAction.EditText(it)) }
        Mode.BARCODE -> BarcodeFields(state, onAction)
        Mode.PICTURE -> PictureFields(state, onAction)
    }
}

@Composable
private fun QrField(value: String, label: Int, onChange: (String) -> Unit) =
    InputField(value, label, maxLines = 4, onChange = onChange)

@Composable
private fun InputField(
    value: String,
    label: Int,
    maxLines: Int,
    keyboard: KeyboardOptions = KeyboardOptions.Default,
    supporting: String? = null,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(label)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = maxLines == 1,
        maxLines = maxLines,
        keyboardOptions = keyboard,
        supportingText = supporting?.let { { Text(it) } },
    )
}

@Composable
private fun BarcodeFields(state: InkState, onAction: (InkAction) -> Unit) {
    val format = state.barcodeFormat?.let {
        when (it) {
            BarcodeFormat.EAN_13 -> "EAN-13"
            BarcodeFormat.CODE_128 -> "Code 128"
        }
    }
    InputField(
        value = state.barcode,
        label = R.string.field_barcode,
        maxLines = 1,
        keyboard = KeyboardOptions(keyboardType = KeyboardType.Ascii),
        supporting = format?.let { stringResource(R.string.barcode_detected, it) },
    ) { onAction(InkAction.EditBarcode(it)) }
    SwitchRow(R.string.switch_show_value, state.showBarcodeValue) { onAction(InkAction.ShowBarcodeValue(it)) }
}

@Composable
private fun PictureFields(state: InkState, onAction: (InkAction) -> Unit) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        onAction(InkAction.PickedPicture(uri))
    }
    Button(
        onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(Icons.Default.Image, contentDescription = null)
        Spacer(Modifier.size(8.dp))
        Text(stringResource(if (state.hasPicture) R.string.pick_other_picture else R.string.pick_picture))
    }
    SwitchRow(R.string.switch_crop, state.pictureCrop) { onAction(InkAction.PictureCrop(it)) }
    SwitchRow(R.string.switch_dither, state.pictureDither) { onAction(InkAction.PictureDither(it)) }
}

@Composable
private fun SwitchRow(label: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(label), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

private fun previewOf(content: Content, panel: Panel = Panel.EPD_2IN13): MonoImage? =
    (render(content, AndroidText, panel) as? ResultOf2.Success)?.value

@Preview(showBackground = true, heightDp = 720)
@Composable
private fun EmptyPreview() = MaterialTheme { InkView(InkState(), {}, {}) }

@Preview(showBackground = true, heightDp = 720)
@Composable
private fun QrPreview() = MaterialTheme {
    val content = Content.QrWithNote("WIFI:T:WPA;S:Gast;P:geheim;;", "Gäste-WLAN scannen")
    InkView(InkState(mode = Mode.QR_NOTE, qr = content.text, note = content.note, preview = previewOf(content), problem = null), {}, {})
}

@Preview(showBackground = true, heightDp = 720)
@Composable
private fun WritingPreview() = MaterialTheme {
    val content = Content.Text("Bin gleich zurück!")
    InkView(
        InkState(mode = Mode.TEXT, text = content.text, preview = previewOf(content), problem = null, nfc = NfcStatus.Writing(60)),
        {},
        {},
    )
}

@Preview(showBackground = true, heightDp = 720)
@Composable
private fun FourTwoPreview() = MaterialTheme {
    val content = Content.QrWithNote("WIFI:T:WPA;S:Gast;P:geheim;;", "Gäste-WLAN scannen")
    val panel = Panel.EPD_4IN2
    InkView(
        InkState(panel = panel, mode = Mode.QR_NOTE, qr = content.text, note = content.note, preview = previewOf(content, panel), problem = null),
        {},
        {},
    )
}
