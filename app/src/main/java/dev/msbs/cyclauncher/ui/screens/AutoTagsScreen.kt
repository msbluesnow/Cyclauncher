package dev.msbs.cyclauncher.ui.screens

import dev.msbs.cyclauncher.LauncherViewModel
import dev.msbs.cyclauncher.ui.theme.AccentColor
import dev.msbs.cyclauncher.ui.theme.PopupTheme
import dev.msbs.cyclauncher.ui.theme.PrimaryTextColor
import dev.msbs.cyclauncher.ui.theme.LocalShadowSettings

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
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
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
                            modifier = Modifier
                                .size(24.dp)
                                .offset(1.dp, 1.dp)
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
                text = "TAGS",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    shadow = shadow
                ),
                color = accentColor.color
            )
        }

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
                text = "Use AI to automatically categorize your apps into tags",
                color = primaryTextColor.color.copy(alpha = 0.7f),
                fontSize = 13.sp,
                style = TextStyle(shadow = shadow)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = primaryTextColor.color.copy(alpha = 0.05f)),
                border = BorderStroke(1.dp, primaryTextColor.color.copy(alpha = 0.12f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {

                    StepHeader(1, "Export App List", accentColor, primaryTextColor, buttonTextColor, shadow)

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Export your installed app list (labels, package names, favorites & tags). Choose JSON (for AI categorization) or TXT (human-readable).",
                        color = primaryTextColor.color.copy(alpha = 0.6f),
                        fontSize = 13.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = { showExportFormatDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor.color),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Icon(Icons.Outlined.Upload, contentDescription = null, tint = buttonTextColor.color)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Export App List",
                            color = buttonTextColor.color,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    HorizontalDivider(
                        color = primaryTextColor.color.copy(alpha = 0.08f),
                        modifier = Modifier.padding(vertical = 16.dp)
                    )

                    StepHeader(2, "Send to AI", accentColor, primaryTextColor, buttonTextColor, shadow)

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Copy the prompt below and send it to an AI model (ChatGPT, Claude, Gemini, etc.) along with the exported app list file.",
                        color = primaryTextColor.color.copy(alpha = 0.6f),
                        fontSize = 13.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(primaryTextColor.color.copy(alpha = 0.08f))
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

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(
                                    onClick = {
                                        copyToClipboard(context, AI_PROMPT)
                                        copiedToClipboard = true
                                        Toast
                                            .makeText(context, "Prompt copied!", Toast.LENGTH_SHORT)
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
                                        if (copiedToClipboard) "Copied!" else "Copy Prompt",
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

                    StepHeader(3, "Import Tagged Apps", accentColor, primaryTextColor, buttonTextColor, shadow)

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "After the AI generates the tagged JSON result, save it as a .json file and import it here. All tags, colors, and assignments will be previewed before applying.",
                        color = primaryTextColor.color.copy(alpha = 0.6f),
                        fontSize = 13.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = { importTaggedLauncher.launch("*/*") },
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor.color),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Icon(Icons.Outlined.Download, contentDescription = null, tint = buttonTextColor.color)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Upload Tagged Result",
                            color = buttonTextColor.color,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    if (showExportFormatDialog) {
        ExportFormatDialog(
            accentColor = accentColor,
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
    primaryTextColor: PrimaryTextColor = PrimaryTextColor.WHITE,
    popupTheme: PopupTheme = PopupTheme.DARK,
    onDismiss: () -> Unit,
    onSelect: (ExportFormat) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export Format", color = accentColor.color, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    "Choose a format for the exported app list:",
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
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor.color),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Text("JSON", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = { onSelect(ExportFormat.TXT) },
                        colors = ButtonDefaults.buttonColors(containerColor = popupTheme.contentColor.copy(alpha = 0.12f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Text("TXT", color = accentColor.color, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "JSON — includes labels, favorites & tags.   TXT — human-readable list.",
                    color = popupTheme.secondaryContentColor,
                    fontSize = 11.sp
                )
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = popupTheme.secondaryContentColor) } },
        containerColor = popupTheme.solidBackgroundColor,
        textContentColor = popupTheme.contentColor
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
3. Output the result strictly as a valid JSON file (a JSON array of objects).

SCHEMA FOR EACH OBJECT:
- "package": (string, required) the package name from input
- "label": (string, optional) the app name/label
- "tag": (string, required) the category name
- "color": (string, required) the category HEX color (e.g., "#3B82F6")

CRITICAL OUTPUT RULES:
- Output MUST be valid pure JSON.
- DO NOT wrap the output in conversational text or explanations.
- The output should be directly saveable as a `.json` file.

EXAMPLE OF VALID OUTPUT:
[
  {"package": "com.android.chrome", "label": "Chrome", "tag": "Browsers", "color": "#3B82F6"},
  {"package": "org.telegram.messenger", "label": "Telegram", "tag": "Messengers", "color": "#10B981"},
  {"package": "com.google.android.youtube", "label": "YouTube", "tag": "Video", "color": "#EF4444"},
  {"package": "com.spotify.music", "label": "Spotify", "tag": "Music", "color": "#1DB954"},
  {"package": "com.chess", "label": "Chess", "tag": "Games", "color": "#8B5CF6"}
]

Paste your exported app list below:"""
