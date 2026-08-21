package dev.msbs.cyclauncher.ui.screens

import dev.msbs.cyclauncher.LauncherViewModel
import dev.msbs.cyclauncher.HandSide
import dev.msbs.cyclauncher.SearchMethod
import dev.msbs.cyclauncher.ui.theme.AccentColor
import dev.msbs.cyclauncher.ui.theme.PrimaryTextColor

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/**
 * The settings screen of the launcher, presenting preferences for UI alignment (hand orientation),
 * color theme, adaptive drop shadow toggles, default launcher selection, backup actions, and support links.
 *
 * @param viewModel The view model supplying state data.
 * @param onBack Callback when pressing back or exiting settings.
 */
@Composable
fun SettingsScreen(
    viewModel: LauncherViewModel,
    enabled: Boolean = true,
    onBack: () -> Unit
) {
    val handSide by viewModel.handSide.collectAsState()
    val searchMethod by viewModel.searchMethod.collectAsState()
    val accentColor by viewModel.accentColor.collectAsState()
    val primaryTextColor by viewModel.primaryTextColor.collectAsState()
    val buttonTextColor by viewModel.buttonTextColor.collectAsState()
    val showShadows by viewModel.showShadows.collectAsState()
    val context = LocalContext.current

    var showDefaultLauncherDialog by remember { mutableStateOf(false) }
    var showAutoTagsScreen by remember { mutableStateOf(false) }
    var currentIsDefault by remember { mutableStateOf(viewModel.isDefaultLauncher()) }

    // Unified App List export / import (JSON), used by both Settings and AutoTags
    val exportAppListLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let { viewModel.exportAppNamesJson(it) } }

    val importAppListLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            viewModel.importAppNamesPreview(it) { count ->
                Toast.makeText(context, "Imported $count app labels", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Unified Tags backup export / import (JSON)
    val exportTagsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let { viewModel.exportTagsBackup(it) } }

    val importTagsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { viewModel.loadTagsBackupPreview(it) } }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                currentIsDefault = viewModel.isDefaultLauncher()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val shadow = primaryTextColor.getShadow(showShadows)

    BackHandler(enabled = enabled, onBack = onBack)

    if (showAutoTagsScreen) {
        AutoTagsScreen(
            viewModel = viewModel,
            onBack = { showAutoTagsScreen = false }
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "SETTINGS",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                shadow = shadow
            ),
            color = accentColor.color
        )
        
        Spacer(modifier = Modifier.height(32.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = primaryTextColor.color.copy(alpha = 0.05f)),
            border = BorderStroke(1.dp, primaryTextColor.color.copy(alpha = 0.12f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Handside Selector
                SettingsRow(label = "Preferred Hand:", textColor = primaryTextColor.color, shadow = shadow) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        HandOption("Left", handSide == HandSide.LEFT, accentColor, shadow) {
                            viewModel.setHandSide(HandSide.LEFT)
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        HandOption("Right", handSide == HandSide.RIGHT, accentColor, shadow) {
                            viewModel.setHandSide(HandSide.RIGHT)
                        }
                    }
                }

                HorizontalDivider(color = primaryTextColor.color.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 12.dp))

                // Search Method Selector (single compact line with icons)
                SettingsRow(label = "Search Method:", textColor = primaryTextColor.color, shadow = shadow) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Horizontal rectangle icon for WHEEL (width > height)
                        SearchMethodIconOption(
                            isHorizontal = true,
                            isSelected = searchMethod == SearchMethod.WHEEL,
                            accentColor = accentColor,
                            primaryTextColor = primaryTextColor,
                            onClick = { viewModel.setSearchMethod(SearchMethod.WHEEL) }
                        )

                        // Vertical rectangle icon for SIDE_ALPHABET (height > width)
                        SearchMethodIconOption(
                            isHorizontal = false,
                            isSelected = searchMethod == SearchMethod.SIDE_ALPHABET,
                            accentColor = accentColor,
                            primaryTextColor = primaryTextColor,
                            onClick = { viewModel.setSearchMethod(SearchMethod.SIDE_ALPHABET) }
                        )
                    }
                }

                HorizontalDivider(color = primaryTextColor.color.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Theme Accent Selector
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Theme Accent:", color = primaryTextColor.color, style = TextStyle(shadow = shadow, fontSize = 16.sp))
                        Spacer(modifier = Modifier.height(8.dp))
                        AccentColorDropdown(accentColor, primaryTextColor) { viewModel.setAccentColor(it) }
                    }

                    // Main Color Selector
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Main Color:", color = primaryTextColor.color, style = TextStyle(shadow = shadow, fontSize = 16.sp))
                        Spacer(modifier = Modifier.height(8.dp))
                        MainColorSelector(primaryTextColor) { viewModel.setPrimaryTextColor(it) }
                    }
                }

                HorizontalDivider(color = primaryTextColor.color.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Adaptive Shadows Toggle
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Adaptive Shadows:", color = primaryTextColor.color, style = TextStyle(shadow = shadow, fontSize = 16.sp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(36.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Switch(
                                checked = showShadows,
                                onCheckedChange = { viewModel.setShowShadows(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = accentColor.color,
                                    checkedTrackColor = accentColor.color.copy(alpha = 0.5f)
                                )
                            )
                        }
                    }

                    // Button Text Color Selector
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Button Text:", color = primaryTextColor.color, style = TextStyle(shadow = shadow, fontSize = 16.sp))
                        Spacer(modifier = Modifier.height(8.dp))
                        MainColorSelector(buttonTextColor) { viewModel.setButtonTextColor(it) }
                    }
                }

                HorizontalDivider(color = primaryTextColor.color.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 12.dp))

                // Combined App List & Tags Row in a single line with vertical separator
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left Side: App List (Export / Import)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        Text(
                            text = "App List:",
                            color = primaryTextColor.color,
                            style = TextStyle(shadow = shadow, fontSize = 14.sp, fontWeight = FontWeight.Medium),
                            modifier = Modifier.padding(end = 2.dp)
                        )
                        IconButton(
                            onClick = { exportAppListLauncher.launch("cyclauncher_apps.json") },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (showShadows) {
                                    Icon(Icons.Outlined.Upload, null, tint = primaryTextColor.shadowColor.copy(alpha = 0.25f), modifier = Modifier.size(22.dp).offset(1.dp, 1.dp))
                                }
                                Icon(Icons.Outlined.Upload, null, tint = accentColor.color, modifier = Modifier.size(22.dp))
                            }
                        }
                        IconButton(
                            onClick = { importAppListLauncher.launch("*/*") },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (showShadows) {
                                    Icon(Icons.Outlined.Download, null, tint = primaryTextColor.shadowColor.copy(alpha = 0.25f), modifier = Modifier.size(22.dp).offset(1.dp, 1.dp))
                                }
                                Icon(Icons.Outlined.Download, null, tint = accentColor.color, modifier = Modifier.size(22.dp))
                            }
                        }
                    }

                    // Vertical Separator Divider with margin to prevent overlapping Tags text
                    VerticalDivider(
                        modifier = Modifier
                            .height(24.dp)
                            .padding(horizontal = 6.dp),
                        color = primaryTextColor.color.copy(alpha = 0.2f)
                    )

                    // Right Side: Tags (AutoTags / Export / Import)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        Text(
                            text = "Tags:",
                            color = primaryTextColor.color,
                            style = TextStyle(shadow = shadow, fontSize = 14.sp, fontWeight = FontWeight.Medium),
                            modifier = Modifier.padding(end = 2.dp)
                        )
                        IconButton(
                            onClick = { showAutoTagsScreen = true },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (showShadows) {
                                    Icon(Icons.Outlined.AutoAwesome, null, tint = primaryTextColor.shadowColor.copy(alpha = 0.25f), modifier = Modifier.size(22.dp).offset(1.dp, 1.dp))
                                }
                                Icon(Icons.Outlined.AutoAwesome, null, tint = accentColor.color, modifier = Modifier.size(22.dp))
                            }
                        }
                        IconButton(
                            onClick = { exportTagsLauncher.launch("cyclauncher_tags.json") },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (showShadows) {
                                    Icon(Icons.Outlined.Upload, null, tint = primaryTextColor.shadowColor.copy(alpha = 0.25f), modifier = Modifier.size(22.dp).offset(1.dp, 1.dp))
                                }
                                Icon(Icons.Outlined.Upload, null, tint = accentColor.color, modifier = Modifier.size(22.dp))
                            }
                        }
                        IconButton(
                            onClick = { importTagsLauncher.launch("*/*") },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (showShadows) {
                                    Icon(Icons.Outlined.Download, null, tint = primaryTextColor.shadowColor.copy(alpha = 0.25f), modifier = Modifier.size(22.dp).offset(1.dp, 1.dp))
                                }
                                Icon(Icons.Outlined.Download, null, tint = accentColor.color, modifier = Modifier.size(22.dp))
                            }
                        }
                    }
                }

                HorizontalDivider(color = primaryTextColor.color.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 12.dp))

                // Default Launcher & Relaunch App Row (50/50 split)
                DefaultLauncherAndRelaunchRow(
                    isDefault = currentIsDefault,
                    accentColor = accentColor,
                    primaryTextColor = primaryTextColor,
                    showShadows = showShadows,
                    onDefaultClick = {
                        viewModel.openDefaultLauncherSettings(context)
                        showDefaultLauncherDialog = true
                    },
                    onRelaunchClick = {
                        val pm = context.packageManager
                        val intent = pm.getLaunchIntentForPackage(context.packageName)
                        if (intent != null) {
                            val mainIntent = Intent.makeRestartActivityTask(intent.component)
                            context.startActivity(mainIntent)
                            Runtime.getRuntime().exit(0)
                        }
                    }
                )

                HorizontalDivider(color = primaryTextColor.color.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 12.dp))

                // Tutorial Button
                Button(
                    onClick = {
                        viewModel.startTutorial()
                        onBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor.color),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.School,
                        contentDescription = null,
                        tint = buttonTextColor.color,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Tutorial",
                        color = buttonTextColor.color,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }

                HorizontalDivider(color = primaryTextColor.color.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 12.dp))

                // Support Project Section
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Text("Support Project:", color = primaryTextColor.color, style = TextStyle(shadow = shadow, fontSize = 16.sp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(onClick = {
                            viewModel.openGitHubPage()
                        }) {
                            Text("GitHub ⭐", color = primaryTextColor.color.copy(alpha = 0.8f), style = TextStyle(shadow = shadow, fontSize = 13.sp))
                        }
                        TextButton(onClick = {
                            viewModel.openDiscordPage()
                        }) {
                            Text("Discord 💬", color = primaryTextColor.color.copy(alpha = 0.8f), style = TextStyle(shadow = shadow, fontSize = 13.sp))
                        }
                        TextButton(onClick = { viewModel.openSupportPage() }, modifier = Modifier.weight(1f)) {
                            Text("Tribute 💝", color = accentColor.color, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }

            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // App Version Section (outside Card)
        val versionName = remember {
            try {
                val pm = context.packageManager
                val pi = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    pm.getPackageInfo(context.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0L))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(context.packageName, 0)
                }
                pi.versionName ?: "1.0"
            } catch (e: Exception) {
                "1.0"
            }
        }
        Text(
            text = "Version $versionName",
            color = primaryTextColor.color.copy(alpha = 0.4f),
            style = TextStyle(shadow = shadow, fontSize = 14.sp)
        )

        Spacer(modifier = Modifier.height(24.dp))
    }

    if (showDefaultLauncherDialog) {
        AlertDialog(
            onDismissRequest = { showDefaultLauncherDialog = false },
            title = { Text("Default Launcher", color = accentColor.color) },
            text = { Text(if (currentIsDefault) "Cyclauncher is now your default launcher!" else "Cyclauncher is not set as default. Try again?", color = Color.White) },
            confirmButton = {
                TextButton(onClick = { if (!currentIsDefault) viewModel.openDefaultLauncherSettings(context) else showDefaultLauncherDialog = false }) {
                    Text(if (currentIsDefault) "Great!" else "Set Default", color = accentColor.color)
                }
            },
            dismissButton = { TextButton(onClick = { showDefaultLauncherDialog = false }) { Text("Cancel", color = Color.Gray) } },
            containerColor = Color(0xFF1E1E1E),
            textContentColor = Color.White
        )
    }
}

/**
 * A layout row presenting a label and a custom configuration content side-by-side.
 */
@Composable
private fun SettingsRow(label: String, textColor: Color = Color.White, shadow: Shadow?, content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = textColor, style = TextStyle(shadow = shadow, fontSize = 16.sp))
        content()
    }
}

/**
 * Dropdown selector for picking the theme accent color.
 */
@Composable
private fun AccentColorDropdown(selectedColor: AccentColor, primaryTextColor: PrimaryTextColor = PrimaryTextColor.WHITE, onSelect: (AccentColor) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val colorPairs = remember {
        listOf(
            AccentColor.SKY to AccentColor.DARK_SKY,
            AccentColor.LAVENDER to AccentColor.DARK_LAVENDER,
            AccentColor.MINT to AccentColor.DARK_MINT,
            AccentColor.ROSE to AccentColor.DARK_ROSE,
            AccentColor.PEACH to AccentColor.DARK_PEACH,
            AccentColor.SNOW to AccentColor.DARK_SLATE,
        )
    }

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(primaryTextColor.color.copy(alpha = 0.1f))
                .clickable { expanded = true }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(selectedColor.color)
                    .border(1.dp, primaryTextColor.color.copy(alpha = 0.2f), CircleShape)
            )
            Icon(
                imageVector = Icons.Outlined.KeyboardArrowDown,
                contentDescription = null,
                tint = selectedColor.color,
                modifier = Modifier.size(20.dp)
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.width(96.dp).background(Color(0xFF2D2D2D))
        ) {
            colorPairs.forEach { (light, dark) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Light Variant (Left)
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(light.color)
                            .border(
                                width = if (selectedColor == light) 2.dp else 1.dp,
                                color = if (selectedColor == light) Color.White else primaryTextColor.color.copy(alpha = 0.2f),
                                shape = CircleShape
                            )
                            .clickable {
                                onSelect(light)
                                expanded = false
                            }
                    )
                    // Dark Variant (Right)
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(dark.color)
                            .border(
                                width = if (selectedColor == dark) 2.dp else 1.dp,
                                color = if (selectedColor == dark) Color.White else primaryTextColor.color.copy(alpha = 0.2f),
                                shape = CircleShape
                            )
                            .clickable {
                                onSelect(dark)
                                expanded = false
                            }
                    )
                }
            }
        }
    }
}

/**
 * Section block representing default launcher preferences and app relaunch options, split 50/50.
 */
@Composable
private fun DefaultLauncherAndRelaunchRow(
    isDefault: Boolean,
    accentColor: AccentColor,
    primaryTextColor: PrimaryTextColor = PrimaryTextColor.WHITE,
    showShadows: Boolean,
    onDefaultClick: () -> Unit,
    onRelaunchClick: () -> Unit
) {
    val shadow = primaryTextColor.getShadow(showShadows)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Left Column: Default Launcher (50% width)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Default Launcher",
                color = primaryTextColor.color,
                style = TextStyle(shadow = shadow, fontWeight = FontWeight.Medium, fontSize = 15.sp),
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                if (isDefault) "Set as default" else "Not set",
                color = if (isDefault) Color.Green else Color.Gray,
                fontSize = 12.sp,
                style = TextStyle(shadow = shadow)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = onDefaultClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isDefault) Color.Transparent else primaryTextColor.color.copy(alpha = 0.1f)
                ),
                border = if (isDefault) BorderStroke(1.dp, Color.Green.copy(alpha = 0.5f)) else null,
                modifier = Modifier.fillMaxWidth().height(42.dp),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                Text(
                    if (isDefault) "Change" else "Set",
                    color = if (isDefault) Color.Green else primaryTextColor.color,
                    style = TextStyle(shadow = shadow, fontWeight = FontWeight.Bold)
                )
            }
        }

        // Right Column: Relaunch App (50% width)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Relaunch App",
                color = primaryTextColor.color,
                style = TextStyle(shadow = shadow, fontWeight = FontWeight.Medium, fontSize = 15.sp),
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                "May fix some issues",
                color = Color.Gray,
                fontSize = 12.sp,
                style = TextStyle(shadow = shadow)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = onRelaunchClick,
                colors = ButtonDefaults.buttonColors(containerColor = accentColor.color.copy(alpha = 0.15f)),
                border = BorderStroke(1.dp, accentColor.color.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth().height(42.dp),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                Text(
                    "Relaunch",
                    color = accentColor.color,
                    style = TextStyle(shadow = shadow, fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}

/**
 * Radio button option representing hand orientation choice (LEFT or RIGHT).
 */
@Composable
private fun HandOption(label: String, isSelected: Boolean, accentColor: AccentColor, shadow: Shadow?, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onClick() }) {
        RadioButton(selected = isSelected, onClick = onClick, colors = RadioButtonDefaults.colors(selectedColor = accentColor.color, unselectedColor = accentColor.color.copy(alpha = 0.3f)))
        Text(label, color = if (isSelected) accentColor.color else accentColor.color.copy(alpha = 0.4f), style = TextStyle(shadow = shadow))
    }
}

/**
 * Compact icon button representing search method layout choice.
 * Horizontal rectangle (width > height) for Wheel layout.
 * Vertical rectangle (height > width) for Side Alphabet Grid layout.
 */
@Composable
private fun SearchMethodIconOption(
    isHorizontal: Boolean,
    isSelected: Boolean,
    accentColor: AccentColor,
    primaryTextColor: PrimaryTextColor,
    onClick: () -> Unit
) {
    val boxWidth = if (isHorizontal) 36.dp else 22.dp
    val boxHeight = if (isHorizontal) 22.dp else 36.dp

    val bgColor = if (isSelected) accentColor.color.copy(alpha = 0.25f) else primaryTextColor.color.copy(alpha = 0.05f)
    val borderColor = if (isSelected) accentColor.color else primaryTextColor.color.copy(alpha = 0.20f)

    Box(
        modifier = Modifier
            .size(width = boxWidth, height = boxHeight)
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .border(if (isSelected) 1.5.dp else 1.dp, borderColor, RoundedCornerShape(6.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (isHorizontal) {
            // Horizontal bar inside horizontal rectangle (Wheel icon)
            Box(
                modifier = Modifier
                    .width(20.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (isSelected) accentColor.color else primaryTextColor.color.copy(alpha = 0.6f))
            )
        } else {
            // Vertical side bar inside vertical rectangle (Side grid icon)
            Row(
                modifier = Modifier.fillMaxSize().padding(3.dp),
                horizontalArrangement = Arrangement.Start
            ) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (isSelected) accentColor.color else primaryTextColor.color.copy(alpha = 0.6f))
                )
            }
        }
    }
}

/**
 * Selector for Primary Text Color (Main Color).
 */
@Composable
private fun MainColorSelector(selectedColor: PrimaryTextColor, onSelect: (PrimaryTextColor) -> Unit) {
    val isBlackSelected = selectedColor == PrimaryTextColor.BLACK
    val thumbOffset by animateFloatAsState(
        targetValue = if (isBlackSelected) 0f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "thumbOffset"
    )
    val thumbColor by animateColorAsState(
        targetValue = if (isBlackSelected) Color.White else Color.Black,
        label = "thumbColor"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(selectedColor.color.copy(alpha = 0.1f))
            .clickable { onSelect(if (isBlackSelected) PrimaryTextColor.WHITE else PrimaryTextColor.BLACK) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth(0.81f)
                .height(20.dp)
        ) {
            // Background halves
            Row(modifier = Modifier.fillMaxSize()) {
                // Left half (Black)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clipToBounds()
                        .drawBehind {
                            val borderPx = 1.5.dp.toPx()
                            val cornerRadius = 4.dp.toPx()
                            
                            // Draw background (extends to the right)
                            drawRoundRect(
                                color = Color.Black,
                                topLeft = Offset.Zero,
                                size = Size(size.width + cornerRadius, size.height),
                                cornerRadius = CornerRadius(cornerRadius, cornerRadius)
                            )
                        }
                )
                // Right half (White)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clipToBounds()
                        .drawBehind {
                            val borderPx = 1.5.dp.toPx()
                            val cornerRadius = 4.dp.toPx()
                            
                            // Draw background (extends to the left)
                            drawRoundRect(
                                color = Color.White,
                                topLeft = Offset(-cornerRadius, 0f),
                                size = Size(size.width + cornerRadius, size.height),
                                cornerRadius = CornerRadius(cornerRadius, cornerRadius)
                            )
                        }
                )
            }

            // Thumb
            // Inner height is 20.dp - 3.dp (border thickness on top/bottom) = 17.dp.
            // 50% of inner height = 8.5.dp.
            val thumbSize = 8.5.dp
            val startOffset = (maxWidth * 0.25f) - (thumbSize / 2)
            val endOffset = (maxWidth * 0.75f) - (thumbSize / 2)
            val currentOffset = startOffset + (endOffset - startOffset) * thumbOffset

            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = currentOffset)
                    .size(thumbSize)
                    .clip(CircleShape)
                    .background(thumbColor)
            )
        }
    }
}
