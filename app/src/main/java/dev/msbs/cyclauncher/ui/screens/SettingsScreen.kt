package dev.msbs.cyclauncher.ui.screens

import dev.msbs.cyclauncher.LauncherViewModel
import dev.msbs.cyclauncher.HandSide
import dev.msbs.cyclauncher.SearchMethod
import dev.msbs.cyclauncher.ui.theme.AccentColor
import dev.msbs.cyclauncher.ui.theme.PopupTheme
import dev.msbs.cyclauncher.ui.theme.PrimaryTextColor
import dev.msbs.cyclauncher.ui.components.KeepAndroidOpenBanner
import dev.msbs.cyclauncher.ui.components.KeepAndroidOpenDialog

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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import dev.msbs.cyclauncher.ui.theme.LocalAnimationsEnabled
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
import androidx.compose.ui.text.style.TextOverflow
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
    val popupTheme by viewModel.popupTheme.collectAsState()
    val showShadows by viewModel.showShadows.collectAsState()
    val shadowColorOverride by viewModel.shadowColor.collectAsState()
    val hideStatusBar by viewModel.hideStatusBar.collectAsState()
    val animationsEnabled by viewModel.animationsEnabled.collectAsState()
    val context = LocalContext.current

    var showDefaultLauncherDialog by remember { mutableStateOf(false) }
    var showAutoTagsScreen by remember { mutableStateOf(false) }
    var showCharacterMappingScreen by remember { mutableStateOf(false) }
    var showKeepAndroidOpenDialog by remember { mutableStateOf(false) }
    val customCharMappings by viewModel.customCharMappings.collectAsState()
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

    val shadow = primaryTextColor.getShadow(showShadows, shadowColorOverride)

    BackHandler(enabled = enabled && !showCharacterMappingScreen && !showAutoTagsScreen, onBack = onBack)

    if (showCharacterMappingScreen) {
        CharacterMappingScreen(
            viewModel = viewModel,
            onBack = { showCharacterMappingScreen = false }
        )
        return
    }

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
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                Box {
                    if (showShadows) {
                        val shadowOffset = 1.dp
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = null,
                            tint = primaryTextColor.getShadowColor(shadowColorOverride),
                            modifier = Modifier
                                .size(24.dp)
                                .offset(x = shadowOffset, y = shadowOffset)
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Back",
                        tint = accentColor.color,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Text(
                text = "SETTINGS",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    shadow = shadow
                ),
                color = accentColor.color
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = primaryTextColor.color.copy(alpha = 0.05f)),
            border = BorderStroke(1.dp, primaryTextColor.color.copy(alpha = 0.12f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Combined Hand Side and Search Method Row
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left Side: Preferred Hand
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1.1f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (showShadows) {
                                Icon(
                                    imageVector = Icons.Outlined.PanTool,
                                    contentDescription = null,
                                    tint = primaryTextColor.getShadowColor(shadowColorOverride).copy(alpha = 0.25f),
                                    modifier = Modifier.size(20.dp).offset(1.dp, 1.dp)
                                )
                            }
                            Icon(
                                imageVector = Icons.Outlined.PanTool,
                                contentDescription = null,
                                tint = primaryTextColor.color.copy(alpha = 0.7f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            HandOption("L", handSide == HandSide.LEFT, accentColor, shadow) {
                                viewModel.setHandSide(HandSide.LEFT)
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            HandOption("R", handSide == HandSide.RIGHT, accentColor, shadow) {
                                viewModel.setHandSide(HandSide.RIGHT)
                            }
                        }
                    }

                    // Vertical Separator
                    VerticalDivider(
                        modifier = Modifier.height(24.dp).padding(horizontal = 8.dp),
                        color = primaryTextColor.color.copy(alpha = 0.15f)
                    )

                    // Right Side: Search Method
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (showShadows) {
                                Icon(
                                    imageVector = Icons.Outlined.Search,
                                    contentDescription = null,
                                    tint = primaryTextColor.getShadowColor(shadowColorOverride).copy(alpha = 0.25f),
                                    modifier = Modifier.size(20.dp).offset(1.dp, 1.dp)
                                )
                            }
                            Icon(
                                imageVector = Icons.Outlined.Search,
                                contentDescription = null,
                                tint = primaryTextColor.color.copy(alpha = 0.7f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            SearchMethodIconOption(
                                isHorizontal = true,
                                isSelected = searchMethod == SearchMethod.WHEEL,
                                accentColor = accentColor,
                                primaryTextColor = primaryTextColor,
                                handSide = handSide,
                                onClick = { viewModel.setSearchMethod(SearchMethod.WHEEL) }
                            )

                            SearchMethodIconOption(
                                isHorizontal = false,
                                isSelected = searchMethod == SearchMethod.SIDE_ALPHABET,
                                accentColor = accentColor,
                                primaryTextColor = primaryTextColor,
                                handSide = handSide,
                                onClick = { viewModel.setSearchMethod(SearchMethod.SIDE_ALPHABET) }
                            )
                        }
                    }
                }

                HorizontalDivider(color = primaryTextColor.color.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 12.dp))

                // Hide Status Bar & Animations in a single row
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Hide Status Bar (Fullscreen Mode)
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Hide Status Bar:",
                            color = primaryTextColor.color,
                            style = TextStyle(shadow = shadow, fontSize = 13.5.sp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val visibilityIcon = if (hideStatusBar) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility
                        IconButton(
                            onClick = { viewModel.setHideStatusBar(!hideStatusBar) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (showShadows) {
                                    Icon(
                                        imageVector = visibilityIcon,
                                        contentDescription = null,
                                        tint = primaryTextColor.getShadowColor(shadowColorOverride).copy(alpha = 0.25f),
                                        modifier = Modifier.size(22.dp).offset(1.dp, 1.dp)
                                    )
                                }
                                Icon(
                                    imageVector = visibilityIcon,
                                    contentDescription = "Toggle status bar visibility",
                                    tint = accentColor.color,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }

                    // Animations Toggle
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Animations:",
                            color = primaryTextColor.color,
                            style = TextStyle(shadow = shadow, fontSize = 13.5.sp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Switch(
                            checked = animationsEnabled,
                            onCheckedChange = { viewModel.setAnimationsEnabled(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = accentColor.color,
                                checkedTrackColor = accentColor.color.copy(alpha = 0.5f)
                            )
                        )
                    }
                }

                HorizontalDivider(color = primaryTextColor.color.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 12.dp))

                // Character Mapping (Custom First-Letter / Emoji / Language indexing)
                SettingsRow(label = "Character Mapping:", textColor = primaryTextColor.color, shadow = shadow) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(primaryTextColor.color.copy(alpha = 0.1f))
                            .clickable { showCharacterMappingScreen = true }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = if (customCharMappings.isEmpty()) "Default" else "${customCharMappings.size} active",
                            color = accentColor.color,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            style = TextStyle(shadow = shadow)
                        )
                        Box(contentAlignment = Alignment.Center) {
                            if (showShadows) {
                                Icon(
                                    imageVector = Icons.Outlined.Tune,
                                    contentDescription = null,
                                    tint = primaryTextColor.getShadowColor(shadowColorOverride).copy(alpha = 0.25f),
                                    modifier = Modifier.size(18.dp).offset(1.dp, 1.dp)
                                )
                            }
                            Icon(
                                imageVector = Icons.Outlined.Tune,
                                contentDescription = "Configure character mappings",
                                tint = accentColor.color,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                HorizontalDivider(color = primaryTextColor.color.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 12.dp))

                // Theme Accent & Adaptive Shadows in a single row
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Theme Accent Selector (50%)
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Theme Accent:", color = primaryTextColor.color, style = TextStyle(shadow = shadow, fontSize = 15.sp))
                        Spacer(modifier = Modifier.height(8.dp))
                        AccentColorDropdown(accentColor, primaryTextColor, popupTheme) { viewModel.setAccentColor(it) }
                    }

                    // Adaptive Shadows (50%)
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Adaptive Shadows:", color = primaryTextColor.color, style = TextStyle(shadow = shadow, fontSize = 15.sp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(36.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Switch(
                                checked = showShadows,
                                onCheckedChange = { viewModel.setShowShadows(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = accentColor.color,
                                    checkedTrackColor = accentColor.color.copy(alpha = 0.5f)
                                )
                            )
                            Box(modifier = Modifier.weight(1f)) {
                                MainColorSelector(shadowColorOverride, primaryTextColor) { viewModel.setShadowColor(it) }
                            }
                        }
                    }
                }

                HorizontalDivider(color = primaryTextColor.color.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 12.dp))

                // Main Color, Button Text, and Popup Theme all 3 in a single row
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Main Color Selector
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Main Color:",
                            color = primaryTextColor.color,
                            style = TextStyle(shadow = shadow, fontSize = 13.sp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        MainColorSelector(primaryTextColor, primaryTextColor) { viewModel.setPrimaryTextColor(it) }
                    }

                    // Button Text Color Selector
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Button Text:",
                            color = primaryTextColor.color,
                            style = TextStyle(shadow = shadow, fontSize = 13.sp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        MainColorSelector(buttonTextColor, primaryTextColor) { viewModel.setButtonTextColor(it) }
                    }

                    // Popup Theme Selector
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Popup Theme:",
                            color = primaryTextColor.color,
                            style = TextStyle(shadow = shadow, fontSize = 13.sp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        PopupThemeSelector(popupTheme, primaryTextColor) { viewModel.setPopupTheme(it) }
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
                                    Icon(Icons.Outlined.Upload, null, tint = primaryTextColor.getShadowColor(shadowColorOverride).copy(alpha = 0.25f), modifier = Modifier.size(22.dp).offset(1.dp, 1.dp))
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
                                    Icon(Icons.Outlined.Download, null, tint = primaryTextColor.getShadowColor(shadowColorOverride).copy(alpha = 0.25f), modifier = Modifier.size(22.dp).offset(1.dp, 1.dp))
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
                                    Icon(Icons.Outlined.AutoAwesome, null, tint = primaryTextColor.getShadowColor(shadowColorOverride).copy(alpha = 0.25f), modifier = Modifier.size(22.dp).offset(1.dp, 1.dp))
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
                                    Icon(Icons.Outlined.Upload, null, tint = primaryTextColor.getShadowColor(shadowColorOverride).copy(alpha = 0.25f), modifier = Modifier.size(22.dp).offset(1.dp, 1.dp))
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
                                    Icon(Icons.Outlined.Download, null, tint = primaryTextColor.getShadowColor(shadowColorOverride).copy(alpha = 0.25f), modifier = Modifier.size(22.dp).offset(1.dp, 1.dp))
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
                    shadowColorOverride = shadowColorOverride,
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

                // Support & Community Section
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Favorite,
                            contentDescription = null,
                            tint = accentColor.color,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Support & Community",
                            color = primaryTextColor.color,
                            style = TextStyle(
                                shadow = shadow,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CommunityButton(
                            title = "GitHub",
                            subtitle = "⭐ Project",
                            icon = Icons.Outlined.Code,
                            accentColor = accentColor,
                            primaryTextColor = primaryTextColor,
                            shadow = shadow,
                            isHighlight = false,
                            onClick = { viewModel.openGitHubPage() },
                            modifier = Modifier.weight(1f)
                        )

                        CommunityButton(
                            title = "Discord",
                            subtitle = "Join chat",
                            icon = Icons.AutoMirrored.Outlined.Chat,
                            accentColor = accentColor,
                            primaryTextColor = primaryTextColor,
                            shadow = shadow,
                            isHighlight = false,
                            onClick = { viewModel.openDiscordPage() },
                            modifier = Modifier.weight(1f)
                        )

                        CommunityButton(
                            title = "Tribute",
                            subtitle = "Sponsor",
                            icon = Icons.Outlined.VolunteerActivism,
                            accentColor = accentColor,
                            primaryTextColor = primaryTextColor,
                            shadow = shadow,
                            isHighlight = true,
                            onClick = { viewModel.openSupportPage() },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Keep Android Open Countdown Banner
        KeepAndroidOpenBanner(
            accentColor = accentColor,
            primaryTextColor = primaryTextColor,
            popupTheme = popupTheme,
            showShadows = showShadows,
            onLearnMoreClick = { showKeepAndroidOpenDialog = true },
            onWebsiteClick = { viewModel.openKeepAndroidOpenPage() }
        )

        Spacer(modifier = Modifier.height(20.dp))

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

    if (showKeepAndroidOpenDialog) {
        KeepAndroidOpenDialog(
            popupTheme = popupTheme,
            accentColor = accentColor,
            onDismiss = { showKeepAndroidOpenDialog = false },
            onOpenWebsite = { viewModel.openKeepAndroidOpenPage() }
        )
    }

    if (showDefaultLauncherDialog) {
        AlertDialog(
            onDismissRequest = { showDefaultLauncherDialog = false },
            title = { Text("Default Launcher", color = accentColor.color) },
            text = { Text(if (currentIsDefault) "Cyclauncher is now your default launcher!" else "Cyclauncher is not set as default. Try again?", color = popupTheme.contentColor) },
            confirmButton = {
                TextButton(onClick = { if (!currentIsDefault) viewModel.openDefaultLauncherSettings(context) else showDefaultLauncherDialog = false }) {
                    Text(if (currentIsDefault) "Great!" else "Set Default", color = accentColor.color)
                }
            },
            dismissButton = { TextButton(onClick = { showDefaultLauncherDialog = false }) { Text("Cancel", color = popupTheme.secondaryContentColor) } },
            containerColor = popupTheme.solidBackgroundColor,
            textContentColor = popupTheme.contentColor
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
 * Interactive card button for Support & Community links.
 */
@Composable
private fun CommunityButton(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: AccentColor,
    primaryTextColor: PrimaryTextColor,
    shadow: Shadow?,
    isHighlight: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerBg = if (isHighlight) {
        accentColor.color.copy(alpha = 0.16f)
    } else {
        primaryTextColor.color.copy(alpha = 0.05f)
    }

    val borderColor = if (isHighlight) {
        accentColor.color.copy(alpha = 0.45f)
    } else {
        primaryTextColor.color.copy(alpha = 0.12f)
    }

    val iconTint = if (isHighlight) accentColor.color else primaryTextColor.color.copy(alpha = 0.85f)
    val titleColor = if (isHighlight) accentColor.color else primaryTextColor.color

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(containerBg)
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (isHighlight) accentColor.color.copy(alpha = 0.2f) else primaryTextColor.color.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = iconTint,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = title,
                color = titleColor,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                style = TextStyle(shadow = shadow)
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = subtitle,
                color = primaryTextColor.color.copy(alpha = if (isHighlight) 0.85f else 0.55f),
                fontSize = 11.sp,
                style = TextStyle(shadow = shadow)
            )
        }
    }
}

/**
 * Dropdown selector for picking the theme accent color.
 */
@Composable
private fun AccentColorDropdown(
    selectedColor: AccentColor,
    primaryTextColor: PrimaryTextColor = PrimaryTextColor.WHITE,
    popupTheme: PopupTheme = PopupTheme.DARK,
    onSelect: (AccentColor) -> Unit
) {
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
            val shadowSettings = dev.msbs.cyclauncher.ui.theme.LocalShadowSettings.current
            Box(contentAlignment = Alignment.Center) {
                if (shadowSettings.showShadows) {
                    Icon(
                        imageVector = Icons.Outlined.KeyboardArrowDown,
                        contentDescription = null,
                        tint = primaryTextColor.getShadowColor(shadowSettings.shadowColorOverride).copy(alpha = 0.25f),
                        modifier = Modifier.size(20.dp).offset(1.dp, 1.dp)
                    )
                }
                Icon(
                    imageVector = Icons.Outlined.KeyboardArrowDown,
                    contentDescription = null,
                    tint = selectedColor.color,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.width(96.dp).background(popupTheme.solidBackgroundColor)
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
                                color = if (selectedColor == light) popupTheme.contentColor else primaryTextColor.color.copy(alpha = 0.2f),
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
                                color = if (selectedColor == dark) popupTheme.contentColor else primaryTextColor.color.copy(alpha = 0.2f),
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
    shadowColorOverride: PrimaryTextColor? = null,
    onDefaultClick: () -> Unit,
    onRelaunchClick: () -> Unit
) {
    val shadow = primaryTextColor.getShadow(showShadows, shadowColorOverride)
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
    handSide: HandSide = HandSide.LEFT,
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
                horizontalArrangement = if (handSide == HandSide.RIGHT) Arrangement.End else Arrangement.Start
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
private fun MainColorSelector(
    selectedColor: PrimaryTextColor,
    primaryTextColor: PrimaryTextColor = PrimaryTextColor.WHITE,
    onSelect: (PrimaryTextColor) -> Unit
) {
    val animationsEnabled = LocalAnimationsEnabled.current
    val isBlackSelected = selectedColor == PrimaryTextColor.BLACK
    val thumbOffset by animateFloatAsState(
        targetValue = if (isBlackSelected) 0f else 1f,
        animationSpec = if (animationsEnabled) spring(stiffness = Spring.StiffnessMediumLow) else snap(),
        label = "thumbOffset"
    )
    val thumbColor by animateColorAsState(
        targetValue = if (isBlackSelected) Color.White else Color.Black,
        animationSpec = if (animationsEnabled) spring() else snap(),
        label = "thumbColor"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(primaryTextColor.color.copy(alpha = 0.1f))
            .clickable { onSelect(if (isBlackSelected) PrimaryTextColor.WHITE else PrimaryTextColor.BLACK) }
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .height(16.dp)
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
                            val cornerRadius = 3.dp.toPx()
                            
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
                            val cornerRadius = 3.dp.toPx()
                            
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
            val thumbSize = 6.5.dp
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

/**
 * Selector for Popup Background Theme (Dark / Light).
 * Reuses the split black/white capsule badge design matching MainColorSelector.
 */
@Composable
private fun PopupThemeSelector(
    selectedTheme: PopupTheme,
    primaryTextColor: PrimaryTextColor = PrimaryTextColor.WHITE,
    onSelect: (PopupTheme) -> Unit
) {
    val animationsEnabled = LocalAnimationsEnabled.current
    val isDarkSelected = selectedTheme == PopupTheme.DARK
    val thumbOffset by animateFloatAsState(
        targetValue = if (isDarkSelected) 0f else 1f,
        animationSpec = if (animationsEnabled) spring(stiffness = Spring.StiffnessMediumLow) else snap(),
        label = "thumbOffset"
    )
    val thumbColor by animateColorAsState(
        targetValue = if (isDarkSelected) Color.White else Color.Black,
        animationSpec = if (animationsEnabled) spring() else snap(),
        label = "thumbColor"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(primaryTextColor.color.copy(alpha = 0.1f))
            .clickable { onSelect(if (isDarkSelected) PopupTheme.LIGHT else PopupTheme.DARK) }
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .height(16.dp)
        ) {
            // Background halves
            Row(modifier = Modifier.fillMaxSize()) {
                // Left half (Dark / Black)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clipToBounds()
                        .drawBehind {
                            val cornerRadius = 3.dp.toPx()
                            drawRoundRect(
                                color = Color.Black,
                                topLeft = Offset.Zero,
                                size = Size(size.width + cornerRadius, size.height),
                                cornerRadius = CornerRadius(cornerRadius, cornerRadius)
                            )
                        }
                )
                // Right half (Light / White)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clipToBounds()
                        .drawBehind {
                            val cornerRadius = 3.dp.toPx()
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
            val thumbSize = 6.5.dp
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
