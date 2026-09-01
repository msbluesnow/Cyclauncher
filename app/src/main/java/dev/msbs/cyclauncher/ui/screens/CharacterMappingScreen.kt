package dev.msbs.cyclauncher.ui.screens

import dev.msbs.cyclauncher.LauncherViewModel
import dev.msbs.cyclauncher.ui.theme.AccentColor
import dev.msbs.cyclauncher.ui.theme.PopupTheme
import dev.msbs.cyclauncher.ui.theme.PrimaryTextColor
import dev.msbs.cyclauncher.ui.theme.LocalShadowSettings
import dev.msbs.cyclauncher.ui.theme.LocalAnimationsEnabled

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Screen for customizing first-character and emoji mappings to alphabet search buckets.
 */
@Composable
fun CharacterMappingScreen(
    viewModel: LauncherViewModel,
    onBack: () -> Unit = {}
) {
    val accentColor by viewModel.accentColor.collectAsState()
    val primaryTextColor by viewModel.primaryTextColor.collectAsState()
    val popupTheme by viewModel.popupTheme.collectAsState()
    val showShadows by viewModel.showShadows.collectAsState()
    val customMappings by viewModel.customCharMappings.collectAsState()
    val shadowSettings = LocalShadowSettings.current
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val shadow = primaryTextColor.getShadow(showShadows, shadowSettings.shadowColorOverride)

    var inputSymbol by remember { mutableStateOf("") }
    var selectedTargetChar by remember { mutableStateOf('A') }
    var showTargetDropdown by remember { mutableStateOf(false) }
    var showResetConfirmDialog by remember { mutableStateOf(false) }
    var viewingLetterDetail by remember { mutableStateOf<Char?>(null) }

    val alphabetOptions = remember { listOf('#') + ('A'..'Z').toList() }

    val exportCharMappingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let { viewModel.exportCharMappingsJson(it) } }

    val importCharMappingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            viewModel.importCharMappingsJson(it, merge = true) { result ->
                result.onSuccess { count ->
                    Toast.makeText(context, "Imported $count character mappings", Toast.LENGTH_SHORT).show()
                }.onFailure { error ->
                    Toast.makeText(context, "Import failed: ${error.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val groupedMappings = remember(customMappings) {
        customMappings.entries
            .groupBy({ it.value }, { it.key })
            .toSortedMap()
    }

    BackHandler(onBack = onBack)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (showShadows) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = null,
                            tint = primaryTextColor.getShadowColor(shadowSettings.shadowColorOverride).copy(alpha = 0.25f),
                            modifier = Modifier.size(24.dp).offset(1.dp, 1.dp)
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Back to Settings",
                        tint = accentColor.color,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Text(
                text = "CHARACTER MAPPING",
                color = accentColor.color,
                style = TextStyle(
                    shadow = shadow,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                ),
                modifier = Modifier.align(Alignment.Center)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = primaryTextColor.color.copy(alpha = 0.05f)),
                border = BorderStroke(1.dp, primaryTextColor.color.copy(alpha = 0.1f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Customize Search Index",
                        color = accentColor.color,
                        style = TextStyle(shadow = shadow, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Map custom characters, emojis, foreign letters (Arabic, Cyrillic, CJK, etc.) or symbols to a specific letter ('A'–'Z' or '#') for all search modes.",
                        color = primaryTextColor.color.copy(alpha = 0.75f),
                        style = TextStyle(shadow = shadow, fontSize = 13.sp, lineHeight = 18.sp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = primaryTextColor.color.copy(alpha = 0.05f)),
                border = BorderStroke(1.dp, primaryTextColor.color.copy(alpha = 0.12f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Add Custom Mapping",
                        color = primaryTextColor.color,
                        style = TextStyle(shadow = shadow, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        dev.msbs.cyclauncher.ui.components.AppOutlinedTextField(
                            value = inputSymbol,
                            onValueChange = { inputSymbol = it },
                            placeholder = {
                                Text(
                                    text = "e.g. 🤗, ب, Ö",
                                    color = accentColor.color.copy(alpha = 0.69f),
                                    fontSize = 14.sp,
                                    style = TextStyle(shadow = shadow)
                                )
                            },
                            singleLine = true,
                            textStyle = TextStyle(
                                color = primaryTextColor.color,
                                fontSize = 15.sp,
                                shadow = shadow
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = primaryTextColor.color,
                                unfocusedTextColor = primaryTextColor.color,
                                focusedBorderColor = accentColor.color,
                                unfocusedBorderColor = primaryTextColor.color.copy(alpha = 0.2f)
                            ),
                            cursorColor = accentColor.color,
                            modifier = Modifier.weight(1.3f),
                            shape = RoundedCornerShape(10.dp),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { keyboardController?.hide() })
                        )

                        Text(
                            text = "➔",
                            color = primaryTextColor.color.copy(alpha = 0.5f),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            style = TextStyle(shadow = shadow)
                        )

                        Box(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(54.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(primaryTextColor.color.copy(alpha = 0.08f))
                                    .border(1.dp, primaryTextColor.color.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                                    .clickable { showTargetDropdown = true }
                                    .padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = selectedTargetChar.toString(),
                                    color = accentColor.color,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    style = TextStyle(shadow = shadow)
                                )
                                Box(contentAlignment = Alignment.Center) {
                                    if (showShadows) {
                                        Icon(
                                            imageVector = Icons.Outlined.ArrowDropDown,
                                            contentDescription = null,
                                            tint = primaryTextColor.getShadowColor(shadowSettings.shadowColorOverride).copy(alpha = 0.25f),
                                            modifier = Modifier.size(24.dp).offset(1.dp, 1.dp)
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.Outlined.ArrowDropDown,
                                        contentDescription = "Select target letter",
                                        tint = primaryTextColor.color.copy(alpha = 0.6f),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = showTargetDropdown,
                                onDismissRequest = { showTargetDropdown = false },
                                modifier = Modifier.background(popupTheme.solidBackgroundColor)
                            ) {
                                alphabetOptions.forEach { char ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = char.toString(),
                                                color = if (char == selectedTargetChar) accentColor.color else popupTheme.contentColor,
                                                fontWeight = if (char == selectedTargetChar) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        onClick = {
                                            selectedTargetChar = char
                                            showTargetDropdown = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = {
                            val symbol = viewModel.extractFirstSymbol(inputSymbol)
                            if (symbol.isNotEmpty()) {
                                viewModel.addOrUpdateCharMapping(symbol, selectedTargetChar)
                                Toast.makeText(context, "Mapped '$symbol' ➔ '$selectedTargetChar'", Toast.LENGTH_SHORT).show()
                                inputSymbol = ""
                                keyboardController?.hide()
                            } else {
                                Toast.makeText(context, "Please enter a valid character or emoji", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor.color.copy(alpha = 0.2f)),
                        border = BorderStroke(1.dp, accentColor.color.copy(alpha = 0.6f)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().height(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (showShadows) {
                                Icon(
                                    imageVector = Icons.Outlined.Add,
                                    contentDescription = null,
                                    tint = primaryTextColor.getShadowColor(shadowSettings.shadowColorOverride).copy(alpha = 0.25f),
                                    modifier = Modifier.size(18.dp).offset(1.dp, 1.dp)
                                )
                            }
                            Icon(
                                imageVector = Icons.Outlined.Add,
                                contentDescription = null,
                                tint = accentColor.color,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Add Mapping",
                            color = accentColor.color,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            style = TextStyle(shadow = shadow)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = primaryTextColor.color.copy(alpha = 0.05f)),
                border = BorderStroke(1.dp, primaryTextColor.color.copy(alpha = 0.12f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            text = "Backup & Restore",
                            color = primaryTextColor.color,
                            style = TextStyle(shadow = shadow, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Export rules to JSON or import from file",
                            color = primaryTextColor.color.copy(alpha = 0.6f),
                            style = TextStyle(shadow = shadow, fontSize = 12.sp)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { exportCharMappingsLauncher.launch("cyclauncher_character_mappings.json") },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, accentColor.color.copy(alpha = 0.5f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = accentColor.color)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (showShadows) {
                                    Icon(
                                        imageVector = Icons.Outlined.Upload,
                                        contentDescription = null,
                                        tint = primaryTextColor.getShadowColor(shadowSettings.shadowColorOverride).copy(alpha = 0.25f),
                                        modifier = Modifier.size(16.dp).offset(1.dp, 1.dp)
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Outlined.Upload,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Export",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                style = TextStyle(shadow = shadow)
                            )
                        }

                        OutlinedButton(
                            onClick = { importCharMappingsLauncher.launch("*/*") },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, accentColor.color.copy(alpha = 0.5f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = accentColor.color)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (showShadows) {
                                    Icon(
                                        imageVector = Icons.Outlined.Download,
                                        contentDescription = null,
                                        tint = primaryTextColor.getShadowColor(shadowSettings.shadowColorOverride).copy(alpha = 0.25f),
                                        modifier = Modifier.size(16.dp).offset(1.dp, 1.dp)
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Outlined.Download,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Import",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                style = TextStyle(shadow = shadow)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Quick Presets",
                color = primaryTextColor.color,
                style = TextStyle(shadow = shadow, fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PresetChip(
                    label = "Cyrillic / Кириллица (А, Б, В, Г...)",
                    accentColor = accentColor,
                    primaryTextColor = primaryTextColor,
                    shadow = shadow,
                    onClick = {
                        val cyrillicPreset = mapOf(
                            "А" to 'A', "а" to 'A', "Б" to 'B', "б" to 'B', "В" to 'V', "в" to 'V',
                            "Г" to 'G', "г" to 'G', "Ґ" to 'G', "ґ" to 'G', "Д" to 'D', "д" to 'D',
                            "Е" to 'E', "е" to 'E', "Ё" to 'E', "ё" to 'E', "Є" to 'E', "є" to 'E', "Э" to 'E', "э" to 'E',
                            "Ж" to 'J', "ж" to 'J', "З" to 'Z', "з" to 'Z',
                            "И" to 'I', "и" to 'I', "Й" to 'I', "й" to 'I', "І" to 'I', "і" to 'I', "Ї" to 'I', "ї" to 'I', "Ы" to 'I', "ы" to 'I',
                            "К" to 'K', "к" to 'K', "Л" to 'L', "л" to 'L', "М" to 'M', "м" to 'M',
                            "Н" to 'N', "н" to 'N', "О" to 'O', "о" to 'O', "П" to 'P', "п" to 'P',
                            "Р" to 'R', "р" to 'R', "С" to 'S', "с" to 'S', "Т" to 'T', "т" to 'T',
                            "У" to 'U', "у" to 'U', "Ў" to 'U', "ў" to 'U', "Ф" to 'F', "ф" to 'F',
                            "Х" to 'H', "х" to 'H', "Ц" to 'C', "ц" to 'C', "Ч" to 'C', "ч" to 'C',
                            "Ш" to 'S', "ш" to 'S', "Щ" to 'S', "щ" to 'S', "Ю" to 'U', "ю" to 'U',
                            "Я" to 'Y', "я" to 'Y'
                        )
                        viewModel.addCharMappings(cyrillicPreset)
                        Toast.makeText(context, "Added Cyrillic mappings", Toast.LENGTH_SHORT).show()
                    }
                )

                PresetChip(
                    label = "Popular Emojis (🤗, 🎮, 🎵...)",
                    accentColor = accentColor,
                    primaryTextColor = primaryTextColor,
                    shadow = shadow,
                    onClick = {
                        val emojiPreset = mapOf(
                            "🤗" to 'A', "🎮" to 'G', "🎵" to 'M', "📷" to 'P',
                            "💬" to 'M', "⚙️" to 'S', "🌐" to 'B', "🛒" to 'S',
                            "📅" to 'C', "📁" to 'F', "❤️" to 'H', "⭐" to 'S'
                        )
                        viewModel.addCharMappings(emojiPreset)
                        Toast.makeText(context, "Added popular emoji mappings", Toast.LENGTH_SHORT).show()
                    }
                )

                PresetChip(
                    label = "German / Nordic (Ä, Ö, Ü, ß...)",
                    accentColor = accentColor,
                    primaryTextColor = primaryTextColor,
                    shadow = shadow,
                    onClick = {
                        val umlautPreset = mapOf(
                            "Ä" to 'A', "ä" to 'A', "Ö" to 'O', "ö" to 'O',
                            "Ü" to 'U', "ü" to 'U', "ß" to 'S', "Å" to 'A',
                            "å" to 'A', "Æ" to 'A', "æ" to 'A', "Ø" to 'O', "ø" to 'O'
                        )
                        viewModel.addCharMappings(umlautPreset)
                        Toast.makeText(context, "Added German/Nordic mappings", Toast.LENGTH_SHORT).show()
                    }
                )

                PresetChip(
                    label = "Arabic Alphabet (ا, ب, ت...)",
                    accentColor = accentColor,
                    primaryTextColor = primaryTextColor,
                    shadow = shadow,
                    onClick = {
                        val arabicPreset = mapOf(
                            "ا" to 'A', "أ" to 'A', "إ" to 'A', "آ" to 'A',
                            "ب" to 'B', "ت" to 'T', "ث" to 'T', "ج" to 'J',
                            "ح" to 'H', "х" to 'K', "د" to 'D', "ذ" to 'D',
                            "р" to 'R', "ز" to 'Z', "س" to 'S', "ш" to 'S',
                            "ص" to 'S', "ض" to 'D', "ط" to 'T', "ظ" to 'Z',
                            "ع" to 'A', "غ" to 'G', "ف" to 'F', "ق" to 'Q',
                            "ك" to 'K', "ل" to 'L', "м" to 'M', "ن" to 'N',
                            "ه" to 'H', "و" to 'W', "ي" to 'Y', "ى" to 'Y'
                        )
                        viewModel.addCharMappings(arabicPreset)
                        Toast.makeText(context, "Added Arabic mappings", Toast.LENGTH_SHORT).show()
                    }
                )

                PresetChip(
                    label = "French / Spanish (À, É, Ç, Ñ...)",
                    accentColor = accentColor,
                    primaryTextColor = primaryTextColor,
                    shadow = shadow,
                    onClick = {
                        val romancePreset = mapOf(
                            "À" to 'A', "à" to 'A', "Â" to 'A', "â" to 'A',
                            "É" to 'E', "é" to 'E', "È" to 'E', "è" to 'E',
                            "Ê" to 'E', "ê" to 'E', "Ë" to 'E', "ë" to 'E',
                            "Î" to 'I', "î" to 'I', "Ï" to 'I', "ï" to 'I',
                            "Ô" to 'O', "ô" to 'O', "Ç" to 'C', "ç" to 'C',
                            "Ñ" to 'N', "ñ" to 'N', "Ù" to 'U', "ù" to 'U'
                        )
                        viewModel.addCharMappings(romancePreset)
                        Toast.makeText(context, "Added French/Spanish mappings", Toast.LENGTH_SHORT).show()
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (showShadows) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.List,
                                contentDescription = null,
                                tint = primaryTextColor.getShadowColor(shadowSettings.shadowColorOverride).copy(alpha = 0.25f),
                                modifier = Modifier.size(20.dp).offset(1.dp, 1.dp)
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.List,
                            contentDescription = "Letter Rows",
                            tint = primaryTextColor.color,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Text(
                        text = "(${groupedMappings.size} letters, ${customMappings.size} rules)",
                        color = primaryTextColor.color.copy(alpha = 0.75f),
                        style = TextStyle(shadow = shadow, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    )
                }

                if (customMappings.isNotEmpty()) {
                    IconButton(
                        onClick = { showResetConfirmDialog = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (showShadows) {
                                Icon(
                                    imageVector = Icons.Outlined.RestartAlt,
                                    contentDescription = null,
                                    tint = primaryTextColor.getShadowColor(shadowSettings.shadowColorOverride).copy(alpha = 0.25f),
                                    modifier = Modifier.size(20.dp).offset(1.dp, 1.dp)
                                )
                            }
                            Icon(
                                imageVector = Icons.Outlined.RestartAlt,
                                contentDescription = "Reset All Mappings",
                                tint = Color.Red.copy(alpha = 0.85f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (groupedMappings.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No custom mappings added.\nApps will use default character indexing.",
                        color = primaryTextColor.color.copy(alpha = 0.4f),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp,
                        style = TextStyle(shadow = shadow)
                    )
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    groupedMappings.forEach { (targetChar, symbols) ->
                        LetterMappingRow(
                            targetChar = targetChar,
                            symbols = symbols,
                            accentColor = accentColor,
                            primaryTextColor = primaryTextColor,
                            shadow = shadow,
                            showShadows = showShadows,
                            shadowColorOverride = shadowSettings.shadowColorOverride,
                            onRemoveSymbol = { symbol ->
                                viewModel.removeCharMapping(symbol)
                                Toast.makeText(context, "Removed '$symbol'", Toast.LENGTH_SHORT).show()
                            },
                            onAddSymbolClick = {
                                selectedTargetChar = targetChar
                                inputSymbol = ""
                            },
                            onOpenDetails = {
                                viewingLetterDetail = targetChar
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    viewingLetterDetail?.let { targetChar ->
        val symbols = groupedMappings[targetChar] ?: emptyList()
        AlertDialog(
            onDismissRequest = { viewingLetterDetail = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(accentColor.color.copy(alpha = 0.2f))
                            .border(1.dp, accentColor.color.copy(alpha = 0.6f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = targetChar.toString(),
                            color = accentColor.color,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                    Text(
                        text = "Mapped Symbols (${symbols.size})",
                        color = popupTheme.contentColor,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                    if (symbols.isEmpty()) {
                        Text("No symbols mapped to '$targetChar'", color = popupTheme.secondaryContentColor)
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            symbols.chunked(3).forEach { chunk ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    chunk.forEach { symbol ->
                                        Row(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(popupTheme.contentColor.copy(alpha = 0.08f))
                                                .padding(horizontal = 8.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = symbol,
                                                color = popupTheme.contentColor,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            IconButton(
                                                onClick = {
                                                    viewModel.removeCharMapping(symbol)
                                                    Toast.makeText(context, "Removed '$symbol'", Toast.LENGTH_SHORT).show()
                                                },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    if (showShadows) {
                                                        Icon(
                                                            imageVector = Icons.Outlined.Close,
                                                            contentDescription = null,
                                                            tint = primaryTextColor.getShadowColor(shadowSettings.shadowColorOverride).copy(alpha = 0.25f),
                                                            modifier = Modifier.size(14.dp).offset(1.dp, 1.dp)
                                                        )
                                                    }
                                                    Icon(
                                                        imageVector = Icons.Outlined.Close,
                                                        contentDescription = "Delete",
                                                        tint = Color.Red.copy(alpha = 0.8f),
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    repeat(3 - chunk.size) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewingLetterDetail = null }) {
                    Text(
                        text = "Done",
                        color = accentColor.color
                    )
                }
            },
            containerColor = popupTheme.solidBackgroundColor,
            textContentColor = popupTheme.contentColor
        )
    }

    if (showResetConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showResetConfirmDialog = false },
            title = {
                Text(
                    text = "Reset Mappings",
                    color = accentColor.color
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to remove all custom character mappings?",
                    color = popupTheme.contentColor
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.resetCharMappings()
                        showResetConfirmDialog = false
                        Toast.makeText(context, "Mappings reset to default", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text(
                        text = "Reset",
                        color = Color.Red
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmDialog = false }) {
                    Text(
                        text = "Cancel",
                        color = popupTheme.secondaryContentColor
                    )
                }
            },
            containerColor = popupTheme.solidBackgroundColor,
            textContentColor = popupTheme.contentColor
        )
    }
}

/**
 * Row displaying a target Latin character and its mapped symbol chips with an overflow counter.
 */
@Composable
private fun LetterMappingRow(
    targetChar: Char,
    symbols: List<String>,
    accentColor: AccentColor,
    primaryTextColor: PrimaryTextColor,
    shadow: Shadow?,
    showShadows: Boolean = false,
    shadowColorOverride: PrimaryTextColor? = null,
    onRemoveSymbol: (String) -> Unit,
    onAddSymbolClick: () -> Unit,
    onOpenDetails: () -> Unit
) {
    val maxVisible = 4
    val visibleSymbols = symbols.take(maxVisible)
    val overflowCount = symbols.size - maxVisible

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(primaryTextColor.color.copy(alpha = 0.05f))
            .border(1.dp, primaryTextColor.color.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
            .clickable { if (overflowCount > 0) onOpenDetails() }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(accentColor.color.copy(alpha = 0.2f))
                    .border(1.dp, accentColor.color.copy(alpha = 0.6f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = targetChar.toString(),
                    color = accentColor.color,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    style = TextStyle(shadow = shadow)
                )
            }

            Text(
                text = "➔",
                color = primaryTextColor.color.copy(alpha = 0.35f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                style = TextStyle(shadow = shadow)
            )

            visibleSymbols.forEach { symbol ->
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(primaryTextColor.color.copy(alpha = 0.08f))
                        .border(0.8.dp, primaryTextColor.color.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                        .clickable { onRemoveSymbol(symbol) }
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = symbol,
                        color = primaryTextColor.color,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        style = TextStyle(shadow = shadow)
                    )
                    Box(contentAlignment = Alignment.Center) {
                        if (showShadows) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = null,
                                tint = primaryTextColor.getShadowColor(shadowColorOverride).copy(alpha = 0.25f),
                                modifier = Modifier.size(11.dp).offset(1.dp, 1.dp)
                            )
                        }
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Remove $symbol",
                            tint = primaryTextColor.color.copy(alpha = 0.45f),
                            modifier = Modifier.size(11.dp)
                        )
                    }
                }
            }

            if (overflowCount > 0) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(accentColor.color.copy(alpha = 0.15f))
                        .border(1.dp, accentColor.color.copy(alpha = 0.4f), CircleShape)
                        .clickable { onOpenDetails() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "+$overflowCount",
                        color = accentColor.color,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        style = TextStyle(shadow = shadow)
                    )
                }
            }
        }

        IconButton(
            onClick = onAddSymbolClick,
            modifier = Modifier.size(30.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (showShadows) {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = null,
                        tint = primaryTextColor.getShadowColor(shadowColorOverride).copy(alpha = 0.25f),
                        modifier = Modifier.size(16.dp).offset(1.dp, 1.dp)
                    )
                }
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = "Add symbol to '$targetChar'",
                    tint = accentColor.color.copy(alpha = 0.8f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * Chip for applying a preset package of character mappings.
 */
@Composable
private fun PresetChip(
    label: String,
    accentColor: AccentColor,
    primaryTextColor: PrimaryTextColor,
    shadow: Shadow?,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(primaryTextColor.color.copy(alpha = 0.08f))
            .border(1.dp, primaryTextColor.color.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            color = accentColor.color,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            style = TextStyle(shadow = shadow)
        )
    }
}
