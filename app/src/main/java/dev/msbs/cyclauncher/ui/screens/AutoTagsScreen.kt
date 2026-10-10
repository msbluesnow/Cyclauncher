package dev.msbs.cyclauncher.ui.screens

import dev.msbs.cyclauncher.ui.theme.capsuleColor
import dev.msbs.cyclauncher.HandSide
import dev.msbs.cyclauncher.LauncherViewModel
import dev.msbs.cyclauncher.ui.theme.AccentColor
import dev.msbs.cyclauncher.ui.theme.PopupTheme
import dev.msbs.cyclauncher.ui.theme.PrimaryTextColor
import dev.msbs.cyclauncher.ui.theme.LocalShadowSettings
import dev.msbs.cyclauncher.ui.components.ScreenTopBar
import dev.msbs.cyclauncher.ui.components.TouchMarqueeText
import dev.msbs.cyclauncher.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.interaction.MutableInteractionSource

import android.content.ClipData
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Screen guiding users through AI-assisted app tagging and categorization.
 */
@Composable
fun AutoTagsScreen(
    viewModel: LauncherViewModel,
    onBack: () -> Unit
) {
    val accentColor by viewModel.accentColor.collectAsState()
    val primaryTextColor by viewModel.primaryTextColor.collectAsState()
    val popupTheme by viewModel.popupTheme.collectAsState()
    val buttonTextColor by viewModel.buttonTextColor.collectAsState()
    val showShadows by viewModel.showShadows.collectAsState()
    val handSide by viewModel.handSide.collectAsState()
    val shadowSettings = LocalShadowSettings.current
    val context = LocalContext.current

    val shadow = primaryTextColor.getShadow(showShadows, shadowSettings.shadowColorOverride)

    var copiedToClipboard by remember { mutableStateOf(false) }
    var showExportFormatDialog by remember { mutableStateOf(false) }

    val exportJsonLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let { viewModel.exportAppNamesJson(it) } }

    val exportTxtLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri -> uri?.let { viewModel.exportAppNamesText(it) } }

    val importTaggedLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { viewModel.loadTagsBackupPreview(it) } }

    BackHandler(onBack = onBack)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ScreenTopBar(
            title = stringResource(R.string.auto_tags_title),
            handSide = handSide,
            accentColor = accentColor,
            primaryTextColor = primaryTextColor,
            showShadows = showShadows,
            shadowColorOverride = shadowSettings.shadowColorOverride,
            onBack = onBack
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = stringResource(R.string.auto_tags_subtitle),
                color = primaryTextColor.color.copy(alpha = 0.7f),
                fontSize = 13.sp,
                style = TextStyle(shadow = shadow)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = primaryTextColor.capsuleColor(0.05f)),
                border = BorderStroke(1.dp, primaryTextColor.color.copy(alpha = 0.12f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {

                    StepHeader(1, stringResource(R.string.auto_tags_step1_title), accentColor, primaryTextColor, buttonTextColor, shadow)

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = stringResource(R.string.auto_tags_step1_desc),
                        color = primaryTextColor.color.copy(alpha = 0.6f),
                        fontSize = 13.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    val step1InteractionSource = remember { MutableInteractionSource() }
                    Button(
                        onClick = { showExportFormatDialog = true },
                        interactionSource = step1InteractionSource,
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor.color),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Outlined.Upload, contentDescription = null, tint = buttonTextColor.color)
                        Spacer(modifier = Modifier.width(8.dp))
                        TouchMarqueeText(
                            text = stringResource(R.string.auto_tags_step1_button),
                            color = buttonTextColor.color,
                            fontWeight = FontWeight.Bold,
                            interactionSource = step1InteractionSource
                        )
                    }

                    HorizontalDivider(
                        color = primaryTextColor.color.copy(alpha = 0.08f),
                        modifier = Modifier.padding(vertical = 16.dp)
                    )

                    StepHeader(2, stringResource(R.string.auto_tags_step2_title), accentColor, primaryTextColor, buttonTextColor, shadow)

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = stringResource(R.string.auto_tags_step2_desc),
                        color = primaryTextColor.color.copy(alpha = 0.6f),
                        fontSize = 13.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(primaryTextColor.capsuleColor(0.08f))
                            .border(
                                1.dp,
                                primaryTextColor.color.copy(alpha = 0.15f),
                                RoundedCornerShape(12.dp)
                            )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = AI_PROMPT,
                                color = primaryTextColor.color.copy(alpha = 0.85f),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            val promptCopiedToastText = stringResource(R.string.auto_tags_prompt_copied_toast)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(
                                    onClick = {
                                        copyToClipboard(context, AI_PROMPT)
                                        copiedToClipboard = true
                                        Toast
                                            .makeText(context, promptCopiedToastText, Toast.LENGTH_SHORT)
                                            .show()
                                    }
                                ) {
                                    Icon(
                                        Icons.Outlined.ContentCopy,
                                        contentDescription = null,
                                        tint = accentColor.color,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        if (copiedToClipboard) stringResource(R.string.auto_tags_copied) else stringResource(R.string.auto_tags_copy_prompt),
                                        color = if (copiedToClipboard) Color.Green else accentColor.color,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(
                        color = primaryTextColor.color.copy(alpha = 0.08f),
                        modifier = Modifier.padding(vertical = 16.dp)
                    )

                    StepHeader(3, stringResource(R.string.auto_tags_step3_title), accentColor, primaryTextColor, buttonTextColor, shadow)

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = stringResource(R.string.auto_tags_step3_desc),
                        color = primaryTextColor.color.copy(alpha = 0.6f),
                        fontSize = 13.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    val step3InteractionSource = remember { MutableInteractionSource() }
                    Button(
                        onClick = { importTaggedLauncher.launch("*/*") },
                        interactionSource = step3InteractionSource,
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor.color),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Outlined.Download, contentDescription = null, tint = buttonTextColor.color)
                        Spacer(modifier = Modifier.width(8.dp))
                        TouchMarqueeText(
                            text = stringResource(R.string.auto_tags_step3_button),
                            color = buttonTextColor.color,
                            fontWeight = FontWeight.Bold,
                            interactionSource = step3InteractionSource
                        )
                    }
                }
            }
        }
    }

    if (showExportFormatDialog) {
        ExportFormatDialog(
            accentColor = accentColor,
            buttonTextColor = buttonTextColor,
            primaryTextColor = primaryTextColor,
            popupTheme = popupTheme,
            onDismiss = { showExportFormatDialog = false },
            onSelect = { format ->
                showExportFormatDialog = false
                when (format) {
                    ExportFormat.JSON -> exportJsonLauncher.launch("cyclauncher_apps.json")
                    ExportFormat.TXT -> exportTxtLauncher.launch("cyclauncher_apps.txt")
                }
            }
        )
    }
}

private enum class ExportFormat { JSON, TXT }

/**
 * Dialog prompting user to choose between JSON and TXT format for exporting app list.
 */
@Composable
private fun ExportFormatDialog(
    accentColor: AccentColor,
    buttonTextColor: PrimaryTextColor = PrimaryTextColor.WHITE,
    primaryTextColor: PrimaryTextColor = PrimaryTextColor.WHITE,
    popupTheme: PopupTheme = PopupTheme.DARK,
    onDismiss: () -> Unit,
    onSelect: (ExportFormat) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.auto_tags_export_dialog_title), color = accentColor.color, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    stringResource(R.string.auto_tags_export_dialog_message),
                    color = popupTheme.contentColor.copy(alpha = 0.85f),
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { onSelect(ExportFormat.JSON) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accentColor.color,
                            contentColor = buttonTextColor.color
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Text("JSON", color = buttonTextColor.color, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    Button(
                        onClick = { onSelect(ExportFormat.TXT) },
                        colors = ButtonDefaults.buttonColors(containerColor = popupTheme.contentColor.copy(alpha = 0.12f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Text("TXT", color = accentColor.color, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    stringResource(R.string.auto_tags_export_dialog_hint),
                    color = popupTheme.secondaryContentColor,
                    fontSize = 11.sp
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(stringResource(R.string.common_cancel), color = popupTheme.secondaryContentColor)
            }
        },
        containerColor = popupTheme.solidBackgroundColor,
        textContentColor = popupTheme.contentColor,
        shape = RoundedCornerShape(20.dp)
    )
}

@Composable
private fun StepHeader(
    stepNumber: Int,
    title: String,
    accentColor: AccentColor,
    primaryTextColor: PrimaryTextColor = PrimaryTextColor.WHITE,
    buttonTextColor: PrimaryTextColor = PrimaryTextColor.BLACK,
    shadow: Shadow?
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(accentColor.color),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stepNumber.toString(),
                color = buttonTextColor.color,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                style = TextStyle(shadow = shadow)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = title,
            color = primaryTextColor.color,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            style = TextStyle(shadow = shadow)
        )
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("AI Prompt", text))
}

private const val AI_PROMPT = """You are an AI assistant helping categorize mobile applications for an Android launcher.
Attached or listed below is a list of installed apps (JSON array or text).

TASK:
1. Group all apps into 10-15 clean, meaningful, concise categories/tags (e.g. "Social", "Messengers", "Games", "Productivity", "Media", "Finance", "Tools", "Shopping", "Navigation", "System", "News", "Health", etc.).
2. Assign each category a distinct, aesthetic HEX color code (format: "#RRGGBB").
3. Assign each category an appropriate vector icon key from the list below (preferred for clean auto-tinting) OR an emoji.
   Available Vector Icon keys: "icon:games", "icon:chat", "icon:music", "icon:video", "icon:web", "icon:shop", "icon:money", "icon:work", "icon:book", "icon:tools", "icon:settings", "icon:star", "icon:heart", "icon:bolt", "icon:home", "icon:lock", "icon:palette", "icon:coffee", "icon:fitness", "icon:cloud", "icon:terminal", "icon:phone", "icon:mail", "icon:idea", "icon:map", "icon:alarm", "icon:folder", "icon:shield", "icon:calendar", "icon:photo", "icon:news", "icon:weather".
4. Output the result strictly as a valid JSON file (a JSON array of objects).

SCHEMA FOR EACH OBJECT:
- "package": (string, required) the package name from input
- "label": (string, optional) the app name/label
- "tag": (string, required) the category name
- "color": (string, required) the category HEX color (e.g., "#3B82F6")
- "icon": (string, optional) the icon key (e.g. "icon:games", "icon:chat") or emoji (e.g. "🎮")

CRITICAL OUTPUT RULES:
- Output MUST be valid pure JSON.
- DO NOT wrap the output in conversational text or explanations.
- The output should be directly saveable as a `.json` file.

EXAMPLE OF VALID OUTPUT:
[
  {"package": "com.android.chrome", "label": "Chrome", "tag": "Browsers", "color": "#3B82F6", "icon": "icon:web"},
  {"package": "org.telegram.messenger", "label": "Telegram", "tag": "Messengers", "color": "#10B981", "icon": "icon:chat"},
  {"package": "com.google.android.youtube", "label": "YouTube", "tag": "Video", "color": "#EF4444", "icon": "icon:video"},
  {"package": "com.spotify.music", "label": "Spotify", "tag": "Music", "color": "#1DB954", "icon": "icon:music"},
  {"package": "com.chess", "label": "Chess", "tag": "Games", "color": "#8B5CF6", "icon": "icon:games"},
  {"package": "com.binance.dev", "label": "Binance", "tag": "Finance", "color": "#F59E0B", "icon": "icon:money"}
]

Paste your exported app list below:"""
