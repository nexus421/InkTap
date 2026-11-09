package bayern.kickner.buchstabierer

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel

/** The Buchstabierer screen, ported from the MainActivity of version 7.0. */
@Composable
fun BuchstabiererScreen(onBack: (() -> Unit)?) {
    val vm: BuchstabiererViewModel = viewModel()
    val loading by vm.loading.collectAsStateWithLifecycle()
    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Box(Modifier.padding(innerPadding)) {
            if (loading) LoadingDialog() else Root(vm, onBack)
        }
    }
}

@Composable
private fun LoadingDialog() {
    AlertDialog(
        onDismissRequest = {},
        confirmButton = {},
        title = { Text(stringResource(R.string.loading_data)) },
        text = { LinearProgressIndicator(Modifier.fillMaxWidth()) },
    )
}

@Composable
private fun Root(vm: BuchstabiererViewModel, onBack: (() -> Unit)?) {
    var input by remember { mutableStateOf("") }
    val history by vm.history.collectAsStateWithLifecycle()

    // Timer für Verlauf
    LaunchedEffect(input) {
        if (input.length >= 2) {
            kotlinx.coroutines.delay(1500)
            vm.addToHistory(input)
        }
    }

    Column(modifier = Modifier.padding(8.dp)) {

        Header(vm, onBack)

        OutlinedTextField(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            value = input,
            shape = MaterialTheme.shapes.medium,
            onValueChange = { input = it.replace("\n", "") },
            placeholder = {
                Text(text = stringResource(R.string.input))
            },
            label = { Text(text = stringResource(R.string.input)) },
            singleLine = true,
            trailingIcon = {
                Icon(
                    Icons.Filled.Clear,
                    contentDescription = stringResource(R.string.clear_input),
                    modifier = Modifier.clickable { input = "" }
                )
            }
        )

        if (history.isNotEmpty()) {
            LazyRow(
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(history) { historyWord ->
                    AssistChip(
                        onClick = { input = historyWord },
                        label = { Text(historyWord, fontSize = 10.sp) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        AnimatedContent(
            targetState = input,
            transitionSpec = {
                fadeIn() togetherWith fadeOut()
            },
            modifier = Modifier
                .weight(1f)
                .systemBarsPadding(),
            label = "spell_animation"
        ) { targetInput ->
            Spelled(vm, targetInput)
        }

        Buttons(vm, input) { input = it }
    }
}

@Composable
private fun Header(vm: BuchstabiererViewModel, onBack: (() -> Unit)?) {
    var showSettings by remember { mutableStateOf(false) }
    var showCustomEditor by remember { mutableStateOf(false) }
    var showQuiz by remember { mutableStateOf(false) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
            }
        }
        Text(
            modifier = Modifier.padding(8.dp),
            text = stringResource(R.string.buchstabierer_name),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.weight(1f))

        IconButton(onClick = { showQuiz = true }) {
            Icon(Icons.Default.School, contentDescription = stringResource(R.string.quiz))
        }

        IconButton(onClick = { showCustomEditor = true }) {
            Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit_custom))
        }

        IconButton(onClick = { showSettings = true }) {
            Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
        }
    }

    if (showSettings) {
        SettingsDialog(vm, onDismiss = { showSettings = false })
    }

    if (showCustomEditor) {
        CustomAlphabetDialog(vm, onDismiss = { showCustomEditor = false })
    }

    if (showQuiz) {
        QuizDialog(vm, onDismiss = { showQuiz = false })
    }
}

@Composable
private fun Buttons(vm: BuchstabiererViewModel, input: String, onClick: (String) -> Unit) {
    val rows by vm.favoriteButtonRows.collectAsStateWithLifecycle()
    Column {
        (1..rows).forEach {
            FavoriteButtonRow(it, input, onClick)
        }
    }
}

@Composable
private fun FavoriteButtonRow(columnIndex: Int, input: String, onClick: (String) -> Unit) {
    val context = LocalContext.current
    var btn1Text by remember { mutableStateOf(FavoriteStorage.get(context, columnIndex, 1)) }
    var btn2Text by remember { mutableStateOf(FavoriteStorage.get(context, columnIndex, 2)) }
    val haptic = LocalHapticFeedback.current

    Row(verticalAlignment = Alignment.CenterVertically) {
        DoubleClickButton(Modifier.weight(1f), onClick = {
            btn1Text?.let { onClick(it) }
        }, onLongClick = {
            FavoriteStorage.store(context, columnIndex, 1, input)
            btn1Text = input.ifBlank { null }
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    text = "Fav ${columnIndex * 2 - 1}"
                )
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    fontSize = 8.sp,
                    color = MaterialTheme.colorScheme.secondary,
                    text = btn1Text ?: stringResource(R.string.hold_to_save)
                )
            }
        }

        DoubleClickButton(Modifier.weight(1f), onClick = {
            btn2Text?.let { onClick(it) }
        }, onLongClick = {
            FavoriteStorage.store(context, columnIndex, 2, input)
            btn2Text = input.ifBlank { null }
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    text = "Fav ${columnIndex * 2}"
                )
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    fontSize = 8.sp,
                    color = MaterialTheme.colorScheme.secondary,
                    text = btn2Text ?: stringResource(R.string.hold_to_save)
                )
            }
        }
    }
}

@Composable
private fun Spelled(vm: BuchstabiererViewModel, input: String) {
    val prepairedInput = vm.prepareInput(input)
    LazyColumn(Modifier.fillMaxSize()) {
        items(prepairedInput.toCharArray().toList()) { c ->
            val characterAsString = vm.prepareCharacter(c)
            Row(modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 4.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    modifier = Modifier.weight(1f), 
                    text = characterAsString,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    text = vm.middle(),
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.outline
                )
                Text(
                    modifier = Modifier.weight(5f),
                    text = vm.textForLetter(characterAsString),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
        }
    }
}

@Composable
private fun SettingsDialog(vm: BuchstabiererViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val selectedType by vm.selectedType.collectAsStateWithLifecycle()
    val name by vm.name.collectAsStateWithLifecycle()
    val favoriteRows by vm.favoriteButtonRows.collectAsStateWithLifecycle()
    val showDebug by vm.showDebug.collectAsStateWithLifecycle()

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.save))
            }
        },
        title = {
            Text(text = stringResource(R.string.settings), fontWeight = FontWeight.Bold)
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                TypeOption(vm, Type.DIN5006, R.string.default_din, selectedType)
                TypeOption(vm, Type.DIN5006_2022, R.string.din_5009, selectedType)
                TypeOption(vm, Type.RANDOM_WORD, R.string.random_words, selectedType)
                TypeOption(vm, Type.ROBERT_LOGGIA, R.string.fam_guy, selectedType)
                TypeOption(vm, Type.INTERNATIONAL, R.string.international, selectedType)
                TypeOption(vm, Type.CUSTOM, R.string.custom, selectedType)

                if (selectedType == Type.ROBERT_LOGGIA) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { vm.setName(it.replace("\n", "")) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        label = { Text(text = stringResource(R.string.name)) },
                        singleLine = true
                    )
                }

                Text(
                    text = stringResource(R.string.hint_change_settings),
                    fontStyle = FontStyle.Italic,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.outline
                )

                ListItem(
                    headlineContent = { Text("Favoriten-Zeilen: $favoriteRows") },
                    trailingContent = {
                        Row {
                            IconButton(onClick = { vm.decrementButtonRows() }) {
                                Icon(Icons.Default.Remove, contentDescription = null)
                            }
                            IconButton(onClick = { vm.incrementButtonRows() }) {
                                Icon(Icons.Default.Add, contentDescription = null)
                            }
                        }
                    }
                )

                ListItem(
                    headlineContent = { Text(stringResource(R.string.show_dev_stuff)) },
                    trailingContent = {
                        Switch(checked = showDebug, onCheckedChange = { vm.setShowDebug(it) })
                    }
                )

                Text(
                    text = "App-Version ${context.packageManager.getPackageInfo(context.packageName, 0).versionName}",
                    fontSize = 10.sp,
                    fontStyle = FontStyle.Italic,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    )
}

@Composable
private fun TypeOption(vm: BuchstabiererViewModel, type: Type, labelRes: Int, currentType: Type) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { vm.setSelectedType(type) }
    ) {
        RadioButton(selected = currentType == type, onClick = { vm.setSelectedType(type) })
        Text(text = stringResource(labelRes))
    }
}

@Composable
private fun CustomAlphabetDialog(vm: BuchstabiererViewModel, onDismiss: () -> Unit) {
    val customMap by vm.customAlphabet.collectAsStateWithLifecycle()
    val letters = ('A'..'Z').map { it.toString() } + listOf("Ä", "Ö", "Ü", "ẞ")

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Fertig") }
        },
        title = { Text("Eigene Buchstabiertafel", fontWeight = FontWeight.Bold) },
        text = {
            Box(modifier = Modifier.heightIn(max = 400.dp)) {
                LazyColumn {
                    items(letters) { letter ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            Text(
                                text = letter,
                                modifier = Modifier.width(32.dp),
                                fontWeight = FontWeight.Bold
                            )
                            OutlinedTextField(
                                value = customMap[letter] ?: "",
                                onValueChange = { vm.updateCustomAlphabet(letter, it) },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text("Wort für $letter") },
                                singleLine = true,
                                shape = MaterialTheme.shapes.small
                            )
                        }
                    }
                }
            }
        }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DoubleClickButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier
            .padding(4.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = 2.dp,
        content = content
    )
}

@Composable
private fun QuizDialog(vm: BuchstabiererViewModel, onDismiss: () -> Unit) {
    val letters = ('A'..'Z').map { it.toString() } + listOf("Ä", "Ö", "Ü")
    var currentLetter by remember { mutableStateOf(letters.random()) }
    var options by remember { mutableStateOf(generateOptions(vm, currentLetter)) }
    var score by remember { mutableIntStateOf(0) }
    var feedback by remember { mutableStateOf<Boolean?>(null) }
    val haptic = LocalHapticFeedback.current
    val selectedType by vm.selectedType.collectAsStateWithLifecycle()

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Beenden") }
        },
        title = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Buchstabier-Quiz", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    Text("Score: $score", style = MaterialTheme.typography.bodyMedium)
                }
                Text(
                    text = "Alphabet: ${stringResource(getLabelForType(selectedType))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = currentLetter,
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(Modifier.height(24.dp))

                options.forEach { option ->
                    Button(
                        onClick = {
                            val isCorrect =
                                vm.textForLetter(currentLetter)
                                    .equals(option, true)
                            if (isCorrect) {
                                score++
                                feedback = true
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                // Nächste Frage
                                currentLetter = letters.random()
                                options = generateOptions(vm, currentLetter)
                            } else {
                                feedback = false
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = if (feedback == false && vm.textForLetter(currentLetter).equals(option, true).not()
                        )
                            ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                        else ButtonDefaults.buttonColors()
                    ) {
                        Text(option)
                    }
                }

                if (feedback == false) {
                    Text(
                        "Falsch! Versuch es nochmal.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    )
}

private fun generateOptions(vm: BuchstabiererViewModel, correctLetter: String): List<String> {
    val correct = vm.textForLetter(correctLetter)
    val wrongOptions = mutableSetOf<String>()

    // Sammle Alternativen vom GLEICHEN Buchstaben aus anderen Alphabeten (außer Robert Loggia)
    val alphabetTypes = listOf(Type.DIN5006, Type.DIN5006_2022, Type.INTERNATIONAL)

    alphabetTypes.forEach { type ->
        val term = when (type) {
            Type.DIN5006 -> Table.entries.find { it.short.equals(correctLetter, true) }?.nameGer
            Type.DIN5006_2022 -> Table.entries.find {
                it.short.equals(
                    correctLetter,
                    true
                )
            }?.nameGer2022

            Type.INTERNATIONAL -> Table.entries.find {
                it.short.equals(
                    correctLetter,
                    true
                )
            }?.international

            else -> null
        }
        if (term != null && term.equals(correct, true).not()) {
            wrongOptions.add(term)
        }
    }

    // Falls nicht genug Alternativen da sind (z.B. alle gleich), fülle mit zufälligen Wörtern auf
    if (wrongOptions.size < 3) {
        val letters = ('A'..'Z').map { it.toString() } + listOf("Ä", "Ö", "Ü")
        while (wrongOptions.size < 3) {
            val randomLetter = letters.random()
            if (randomLetter != correctLetter) {
                wrongOptions.add(vm.textForLetter(randomLetter))
            }
        }
    }

    return (wrongOptions.take(3) + correct).shuffled()
}

private fun getLabelForType(type: Type): Int {
    return when (type) {
        Type.DIN5006 -> R.string.default_din
        Type.DIN5006_2022 -> R.string.din_5009
        Type.RANDOM_WORD -> R.string.random_words
        Type.ROBERT_LOGGIA -> R.string.fam_guy
        Type.INTERNATIONAL -> R.string.international
        Type.CUSTOM -> R.string.custom
    }
}