package dev.msbs.cyclauncher.ui.screens

import dev.msbs.cyclauncher.LauncherViewModel
import dev.msbs.cyclauncher.ui.theme.AccentColor
import dev.msbs.cyclauncher.ui.theme.PrimaryTextColor

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
 * Screen providing tag backup (import/export) and AI-driven categorization helpers.
 * Guides the user through a 3-step AI categorizing process: exporting app lists, copying the prompt, and uploading tags.
 *
 * @param viewModel The view model supplying state data.
 * @param onBack Callback when pressing back to return to Settings.
 */
@Composable
fun AutoTagsScreen(
    viewModel: LauncherViewModel,
    onBack: () -> Unit
) {
    val accentColor by viewModel.accentColor.collectAsState()
    val primaryTextColor by viewModel.primaryTextColor.collectAsState()
    val showShadows by viewModel.showShadows.collectAsState()
    val context = LocalContext.current

    val shadow = primaryTextColor.getShadow(showShadows)

    var copiedToClipboard by remember { mutableStateOf(false) }
    var showExportFormatDialog by remember { mutableStateOf(false) }

    // Step 1 — Export the installed app list. Same unified method as on the main
    // Settings page. JSON is the machine-friendly format (for the AI workflow),
    // TXT is a human-readable alternative.
    val exportJsonLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let { viewModel.exportAppNamesJson(it) } }

    val exportTxtLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri -> uri?.let { viewModel.exportAppNamesText(it) } }

    // Step 3 — Import the AI-returned tagged JSON.
    val importTaggedLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { viewModel.loadAutoTagsPreview(it) } }

    // Unified tags backup (tags + assignments) — identical to the Tags section
    // in the main Settings page.
    val exportTagsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let { viewModel.exportTagsBackup(it) } }

    val importTagsLauncher = rememberLauncherForActivityResult(
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
                            tint = Color.Black.copy(alpha = 0.25f),
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

                    // Step 1: Export
                    StepHeader(1, "Export App List", accentColor, primaryTextColor, shadow)

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Export the list of your installed apps. Send this file to an AI model to categorize them. Choose JSON (for the AI) or TXT (human-readable).",
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
                        Icon(Icons.Outlined.Upload, contentDescription = null, tint = Color.Black)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Export App List",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    HorizontalDivider(
                        color = primaryTextColor.color.copy(alpha = 0.08f),
                        modifier = Modifier.padding(vertical = 16.dp)
                    )

                    // Step 2: Backup Existing Tags
                    StepHeader(2, "Backup Existing Tags", accentColor, primaryTextColor, shadow)

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Back up your existing tags and their app assignments to a file before applying AI-generated tags.",
                        color = primaryTextColor.color.copy(alpha = 0.6f),
                        fontSize = 13.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = { exportTagsLauncher.launch("cyclauncher_tags.json") },
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor.color),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Icon(Icons.Outlined.Upload, contentDescription = null, tint = Color.Black)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Export Tags Backup",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    HorizontalDivider(
                        color = primaryTextColor.color.copy(alpha = 0.08f),
                        modifier = Modifier.padding(vertical = 16.dp)
                    )

                    // Step 3: Send to AI
                    StepHeader(3, "Send to AI", accentColor, primaryTextColor, shadow)

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Copy the prompt below, paste it into any AI (ChatGPT, Claude, etc.) along with the exported app list.",
                        color = primaryTextColor.color.copy(alpha = 0.6f),
                        fontSize = 13.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Prompt box
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

                    // Step 4: Import Tagged Apps
                    StepHeader(4, "Import Tagged Apps", accentColor, primaryTextColor, shadow)

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "After the AI returns the tagged result, save it to a file and upload it here. Both JSON and TXT files are supported.",
                        color = primaryTextColor.color.copy(alpha = 0.6f),
                        fontSize = 13.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = { importTaggedLauncher.launch("application/*") },
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor.color),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Icon(Icons.Outlined.Download, contentDescription = null, tint = Color.Black)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Upload Tagged Result",
                            color = Color.Black,
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
 * Dialog prompting the user to choose the app list format to export (JSON or TXT).
 */
@Composable
private fun ExportFormatDialog(
    accentColor: AccentColor,
    primaryTextColor: PrimaryTextColor = PrimaryTextColor.WHITE,
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
                    color = primaryTextColor.color.copy(alpha = 0.85f),
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
                        colors = ButtonDefaults.buttonColors(containerColor = primaryTextColor.color.copy(alpha = 0.12f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Text("TXT", color = accentColor.color, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "JSON — for the AI workflow.   TXT — human-readable list.",
                    color = primaryTextColor.color.copy(alpha = 0.5f),
                    fontSize = 11.sp
                )
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Color.Gray) } },
        containerColor = Color(0xFF1E1E1E),
        textContentColor = primaryTextColor.color
    )
}

/**
 * A layout representing a numbered header for each setup stage.
 */
@Composable
private fun StepHeader(
    stepNumber: Int,
    title: String,
    accentColor: AccentColor,
    primaryTextColor: PrimaryTextColor = PrimaryTextColor.WHITE,
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
                color = Color.Black,
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

/**
 * Copies the specified text string to the system clipboard.
 */
private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("AI Prompt", text))
}

private const val AI_PROMPT = """Here is a JSON array of installed mobile apps. Please categorize each app into a group and assign a color (hex format #RRGGBB) to each group.

Requirements:
- Use a minimal number of categories (10-15 groups)
- Group apps into meaningful, concise categories (e.g., "Social", "Games", "Productivity", "Media", etc.)
- Each category should contain at least 2 apps if possible, or a single app if it is truly unique
- Assign each category a distinct color
- Add two new fields to each object: "tag" (category name) and "color" (hex color like "#3B82F6")
- Keep all original fields ("package" and "label") unchanged
- Return ONLY the modified JSON array, no other text, no markdown code blocks

Example of the expected output format:
[
  {"package": "com.android.chrome", "label": "Chrome", "tag": "Browsers", "color": "#3B82F6"},
  {"package": "org.telegram.messenger", "label": "Telegram", "tag": "Messengers", "color": "#10B981"},
  {"package": "com.google.youtube", "label": "YouTube", "tag": "Video", "color": "#EF4444"},
  {"package": "com.spotify.music", "label": "Spotify", "tag": "Music", "color": "#1DB954"},
  {"package": "com.chess.app", "label": "Chess", "tag": "Games", "color": "#8B5CF6"}
]

You can use the JSON or TXT export from the app (both contain the same app list). Paste your exported app list below:"""
