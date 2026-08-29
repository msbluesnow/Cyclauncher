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
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/**
 * Main settings screen with preferences for layout, theme, colors, backup, and community links.
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
    val haptic = LocalHapticFeedback.current

    var showDefaultLauncherDialog by remember { mutableStateOf(false) }
    var showAutoTagsScreen by remember { mutableStateOf(false) }
    var showCharacterMappingScreen by remember { mutableStateOf(false) }
    var showKeepAndroidOpenDialog by remember { mutableStateOf(false) }
    val customCharMappings by viewModel.customCharMappings.collectAsState()
    var currentIsDefault by remember { mutableStateOf(viewModel.isDefaultLauncher()) }

    val exportBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let { viewModel.exportTagsBackup(it) } }

    val importBackupLauncher = rememberLauncherForActivityResult(
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
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
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

                    VerticalDivider(
                        modifier = Modifier.height(24.dp).padding(horizontal = 8.dp),
                        color = primaryTextColor.color.copy(alpha = 0.15f)
                    )

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

                HorizontalDivider(
                    color = primaryTextColor.color.copy(alpha = 0.08f),
                    modifier = Modifier.padding(vertical = 12.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
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
                        val visibilityIcon =
                            if (hideStatusBar) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility
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

                HorizontalDivider(
                    color = primaryTextColor.color.copy(alpha = 0.08f),
                    modifier = Modifier.padding(vertical = 12.dp)
                )

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

                HorizontalDivider(
                    color = primaryTextColor.color.copy(alpha = 0.08f),
                    modifier = Modifier.padding(vertical = 12.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Theme Accent:",
                            color = primaryTextColor.color,
                            style = TextStyle(shadow = shadow, fontSize = 15.sp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        AccentColorDropdown(accentColor, primaryTextColor, popupTheme) { viewModel.setAccentColor(it) }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Adaptive Shadows:",
                            color = primaryTextColor.color,
                            style = TextStyle(shadow = shadow, fontSize = 15.sp)
                        )
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
                                MainColorSelector(
                                    shadowColorOverride,
                                    primaryTextColor
                                ) { viewModel.setShadowColor(it) }
                            }
                        }
                    }
                }

                HorizontalDivider(
                    color = primaryTextColor.color.copy(alpha = 0.08f),
                    modifier = Modifier.padding(vertical = 12.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
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

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Spacer(modifier = Modifier.height(24.dp))
                        val isLightAccent = accentColor.color.luminance() > 0.5f
                        val iconTint = if (isLightAccent) Color.Black else Color.White
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(accentColor.color)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    val targetBtnText =
                                        if (isLightAccent) PrimaryTextColor.BLACK else PrimaryTextColor.WHITE
                                    val targetPopupTheme = if (isLightAccent) PopupTheme.LIGHT else PopupTheme.DARK
                                    viewModel.setButtonTextColor(targetBtnText)
                                    viewModel.setPopupTheme(targetPopupTheme)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (showShadows) {
                                Icon(
                                    imageVector = Icons.Outlined.AutoAwesome,
                                    contentDescription = null,
                                    tint = primaryTextColor.getShadowColor(shadowColorOverride).copy(alpha = 0.25f),
                                    modifier = Modifier.size(16.dp).offset(1.dp, 1.dp)
                                )
                            }
                            Icon(
                                imageVector = Icons.Outlined.AutoAwesome,
                                contentDescription = "Auto Contrast",
                                tint = iconTint,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

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

                HorizontalDivider(
                    color = primaryTextColor.color.copy(alpha = 0.08f),
                    modifier = Modifier.padding(vertical = 12.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = primaryTextColor.color.copy(alpha = 0.07f),
                        border = BorderStroke(1.dp, accentColor.color.copy(alpha = 0.35f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "Backup",
                                color = primaryTextColor.color,
                                style = TextStyle(shadow = shadow, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            IconButton(
                                onClick = { exportBackupLauncher.launch("cyclauncher_backup.json") },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    if (showShadows) {
                                        Icon(
                                            imageVector = Icons.Outlined.Upload,
                                            contentDescription = null,
                                            tint = primaryTextColor.getShadowColor(shadowColorOverride)
                                                .copy(alpha = 0.25f),
                                            modifier = Modifier.size(22.dp).offset(1.dp, 1.dp)
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.Outlined.Upload,
                                        contentDescription = "Export Backup",
                                        tint = accentColor.color,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                            IconButton(
                                onClick = { importBackupLauncher.launch("*/*") },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    if (showShadows) {
                                        Icon(
                                            imageVector = Icons.Outlined.Download,
                                            contentDescription = null,
                                            tint = primaryTextColor.getShadowColor(shadowColorOverride)
                                                .copy(alpha = 0.25f),
                                            modifier = Modifier.size(22.dp).offset(1.dp, 1.dp)
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.Outlined.Download,
                                        contentDescription = "Import Backup",
                                        tint = accentColor.color,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }
                    }

                    Surface(
                        onClick = { showAutoTagsScreen = true },
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = primaryTextColor.color.copy(alpha = 0.07f),
                        border = BorderStroke(1.dp, accentColor.color.copy(alpha = 0.35f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (showShadows) {
                                    Icon(
                                        imageVector = Icons.Outlined.AutoAwesome,
                                        contentDescription = null,
                                        tint = primaryTextColor.getShadowColor(shadowColorOverride).copy(alpha = 0.25f),
                                        modifier = Modifier.size(20.dp).offset(1.dp, 1.dp)
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Outlined.AutoAwesome,
                                    contentDescription = null,
                                    tint = accentColor.color,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "AI Tags",
                                color = primaryTextColor.color,
                                style = TextStyle(shadow = shadow, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            )
                        }
                    }
                }

                HorizontalDivider(
                    color = primaryTextColor.color.copy(alpha = 0.08f),
                    modifier = Modifier.padding(vertical = 12.dp)
                )

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

                HorizontalDivider(
                    color = primaryTextColor.color.copy(alpha = 0.08f),
                    modifier = Modifier.padding(vertical = 12.dp)
                )

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
                    Box(contentAlignment = Alignment.Center) {
                        if (showShadows) {
                            Icon(
                                imageVector = Icons.Outlined.School,
                                contentDescription = null,
                                tint = primaryTextColor.getShadowColor(shadowColorOverride).copy(alpha = 0.25f),
                                modifier = Modifier.size(18.dp).offset(1.dp, 1.dp)
                            )
                        }
                        Icon(
                            imageVector = Icons.Outlined.School,
                            contentDescription = null,
                            tint = buttonTextColor.color,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Tutorial",
                        color = buttonTextColor.color,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }

                HorizontalDivider(
                    color = primaryTextColor.color.copy(alpha = 0.08f),
                    modifier = Modifier.padding(vertical = 12.dp)
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (showShadows) {
                                Icon(
                                    imageVector = Icons.Outlined.Favorite,
                                    contentDescription = null,
                                    tint = primaryTextColor.getShadowColor(shadowColorOverride).copy(alpha = 0.25f),
                                    modifier = Modifier.size(18.dp).offset(1.dp, 1.dp)
                                )
                            }
                            Icon(
                                imageVector = Icons.Outlined.Favorite,
                                contentDescription = null,
                                tint = accentColor.color,
                                modifier = Modifier.size(18.dp)
                            )
                        }
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
                            showShadows = showShadows,
                            shadowColorOverride = shadowColorOverride,
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
                            showShadows = showShadows,
                            shadowColorOverride = shadowColorOverride,
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
                            showShadows = showShadows,
                            shadowColorOverride = shadowColorOverride,
                            isHighlight = true,
                            onClick = { viewModel.openSupportPage() },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        KeepAndroidOpenBanner(
            accentColor = accentColor,
            primaryTextColor = primaryTextColor,
            popupTheme = popupTheme,
            showShadows = showShadows,
            onLearnMoreClick = { showKeepAndroidOpenDialog = true },
            onWebsiteClick = { viewModel.openKeepAndroidOpenPage() }
        )

        Spacer(modifier = Modifier.height(20.dp))

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
            text = {
                Text(
                    if (currentIsDefault) "Cyclauncher is now your default launcher!" else "Cyclauncher is not set as default. Try again?",
                    color = popupTheme.contentColor
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (!currentIsDefault) viewModel.openDefaultLauncherSettings(context) else showDefaultLauncherDialog =
                        false
                }) {
                    Text(if (currentIsDefault) "Great!" else "Set Default", color = accentColor.color)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDefaultLauncherDialog = false }) {
                    Text(
                        "Cancel",
                        color = popupTheme.secondaryContentColor
                    )
                }
            },
            containerColor = popupTheme.solidBackgroundColor,
            textContentColor = popupTheme.contentColor
        )
    }
}

@Composable
private fun SettingsRow(
    label: String,
    textColor: Color = Color.White,
    shadow: Shadow?,
    content: @Composable () -> Unit
) {
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
    showShadows: Boolean = false,
    shadowColorOverride: PrimaryTextColor? = null,
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
                    .background(
                        if (isHighlight) accentColor.color.copy(alpha = 0.2f) else primaryTextColor.color.copy(
                            alpha = 0.08f
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (showShadows) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = primaryTextColor.getShadowColor(shadowColorOverride).copy(alpha = 0.25f),
                        modifier = Modifier.size(18.dp).offset(1.dp, 1.dp)
                    )
                }
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
 * Selector button and dialog for picking the theme accent color.
 * Supports preset pairs, dynamic Material You wallpaper color, and custom color picker.
 */
@Composable
private fun AccentColorDropdown(
    selectedColor: AccentColor,
    primaryTextColor: PrimaryTextColor = PrimaryTextColor.WHITE,
    popupTheme: PopupTheme = PopupTheme.DARK,
    onSelect: (AccentColor) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(primaryTextColor.color.copy(alpha = 0.1f))
                .clickable { showDialog = true }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(selectedColor.color)
                    .border(1.dp, primaryTextColor.color.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (selectedColor.isDynamicWallpaper) {
                    Icon(
                        imageVector = Icons.Outlined.AutoAwesome,
                        contentDescription = null,
                        tint = if (selectedColor.color.luminance() > 0.5f) Color.Black else Color.White,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
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

        if (showDialog) {
            AccentColorDialog(
                selectedColor = selectedColor,
                popupTheme = popupTheme,
                onDismiss = { showDialog = false },
                onSelect = {
                    onSelect(it)
                    showDialog = false
                }
            )
        }
    }
}

/**
 * Modal dialog for selecting theme accent: Material You, curated presets, or interactive color picker.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccentColorDialog(
    selectedColor: AccentColor,
    popupTheme: PopupTheme,
    onDismiss: () -> Unit,
    onSelect: (AccentColor) -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(if (selectedColor.isCustom) 1 else 0) }

    val wallpaperColor = remember(context) { AccentColor.getWallpaperAccentColor(context) }

    val initialHsv = remember(selectedColor) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(selectedColor.color.toArgb(), hsv)
        hsv
    }
    var currentHue by remember { mutableFloatStateOf(initialHsv[0]) }
    var currentSat by remember { mutableFloatStateOf(initialHsv[1].coerceAtLeast(0.1f)) }
    var currentVal by remember { mutableFloatStateOf(initialHsv[2].coerceAtLeast(0.1f)) }

    val customPickedColor = remember(currentHue, currentSat, currentVal) {
        val hsv = floatArrayOf(currentHue, currentSat, currentVal)
        Color(android.graphics.Color.HSVToColor(hsv))
    }

    var hexInputText by remember {
        val argb = selectedColor.color.toArgb()
        mutableStateOf(String.format("%06X", 0xFFFFFF and argb))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = popupTheme.solidBackgroundColor,
        shape = RoundedCornerShape(20.dp),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Theme Accent",
                    color = popupTheme.contentColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Close",
                        tint = popupTheme.secondaryContentColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(popupTheme.contentColor.copy(alpha = 0.08f))
                        .padding(3.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selectedTab == 0) popupTheme.contentColor.copy(alpha = 0.16f) else Color.Transparent)
                            .clickable { selectedTab = 0 }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Presets & Wallpaper",
                            fontSize = 12.sp,
                            fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedTab == 0) popupTheme.contentColor else popupTheme.secondaryContentColor
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selectedTab == 1) popupTheme.contentColor.copy(alpha = 0.16f) else Color.Transparent)
                            .clickable { selectedTab = 1 }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Custom Color",
                            fontSize = 12.sp,
                            fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedTab == 1) popupTheme.contentColor else popupTheme.secondaryContentColor
                        )
                    }
                }

                if (selectedTab == 0) {
                    Text(
                        "Wallpaper Accent (Material You)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = popupTheme.secondaryContentColor
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (selectedColor.isDynamicWallpaper) wallpaperColor.copy(alpha = 0.18f)
                                else popupTheme.contentColor.copy(alpha = 0.06f)
                            )
                            .border(
                                width = if (selectedColor.isDynamicWallpaper) 1.5.dp else 1.dp,
                                color = if (selectedColor.isDynamicWallpaper) wallpaperColor else popupTheme.contentColor.copy(
                                    alpha = 0.12f
                                ),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable {
                                onSelect(AccentColor.wallpaper(context))
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(wallpaperColor)
                                    .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.AutoAwesome,
                                    contentDescription = null,
                                    tint = if (wallpaperColor.luminance() > 0.5f) Color.Black else Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Column {
                                Text(
                                    "Dynamic Wallpaper Color",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = popupTheme.contentColor
                                )
                                Text(
                                    "Matches system wallpaper theme",
                                    fontSize = 11.sp,
                                    color = popupTheme.secondaryContentColor
                                )
                            }
                        }
                        if (selectedColor.isDynamicWallpaper) {
                            Icon(
                                imageVector = Icons.Outlined.Check,
                                contentDescription = "Selected",
                                tint = wallpaperColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        "Echo Icon Theme Presets",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = popupTheme.secondaryContentColor
                    )

                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AccentColor.PRESET_PAIRS.forEach { (light, dark) ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                PresetColorChip(
                                    accent = light,
                                    isSelected = selectedColor == light,
                                    modifier = Modifier.weight(1f),
                                    popupTheme = popupTheme,
                                    onClick = { onSelect(light) }
                                )
                                PresetColorChip(
                                    accent = dark,
                                    isSelected = selectedColor == dark,
                                    modifier = Modifier.weight(1f),
                                    popupTheme = popupTheme,
                                    onClick = { onSelect(dark) }
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        "Interactive Color Picker",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = popupTheme.secondaryContentColor
                    )

                    val quickSwatches = remember {
                        listOf(
                            Color(0xFFF44336), Color(0xFFE91E63), Color(0xFF9C27B0), Color(0xFF673AB7),
                            Color(0xFF3F51B5), Color(0xFF2196F3), Color(0xFF03A9F4), Color(0xFF00BCD4),
                            Color(0xFF009688), Color(0xFF4CAF50), Color(0xFF8BC34A), Color(0xFFCDDC39),
                            Color(0xFFFFEB3B), Color(0xFFFFC107), Color(0xFFFF9800), Color(0xFFFF5722)
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Quick Swatches", fontSize = 11.sp, color = popupTheme.secondaryContentColor)
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            quickSwatches.forEach { swatch ->
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(swatch)
                                        .border(
                                            width = if (customPickedColor == swatch) 2.dp else 1.dp,
                                            color = if (customPickedColor == swatch) popupTheme.contentColor else Color.Black.copy(
                                                alpha = 0.2f
                                            ),
                                            shape = CircleShape
                                        )
                                        .clickable {
                                            val hsv = FloatArray(3)
                                            android.graphics.Color.colorToHSV(swatch.toArgb(), hsv)
                                            currentHue = hsv[0]
                                            currentSat = hsv[1]
                                            currentVal = hsv[2]
                                            val argb = swatch.toArgb()
                                            hexInputText = String.format("%06X", 0xFFFFFF and argb)
                                        }
                                )
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Hue (${currentHue.toInt()}°)",
                                fontSize = 11.sp,
                                color = popupTheme.secondaryContentColor
                            )
                        }
                        HueSlider(
                            hue = currentHue,
                            onHueChange = {
                                currentHue = it
                                val hsv = floatArrayOf(currentHue, currentSat, currentVal)
                                val col = Color(android.graphics.Color.HSVToColor(hsv))
                                hexInputText = String.format("%06X", 0xFFFFFF and col.toArgb())
                            }
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "Saturation (${(currentSat * 100).toInt()}%)",
                            fontSize = 11.sp,
                            color = popupTheme.secondaryContentColor
                        )
                        Slider(
                            value = currentSat,
                            onValueChange = {
                                currentSat = it
                                val hsv = floatArrayOf(currentHue, currentSat, currentVal)
                                val col = Color(android.graphics.Color.HSVToColor(hsv))
                                hexInputText = String.format("%06X", 0xFFFFFF and col.toArgb())
                            },
                            valueRange = 0.05f..1f,
                            colors = SliderDefaults.colors(
                                thumbColor = customPickedColor,
                                activeTrackColor = customPickedColor,
                                inactiveTrackColor = popupTheme.contentColor.copy(alpha = 0.15f)
                            )
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "Brightness (${(currentVal * 100).toInt()}%)",
                            fontSize = 11.sp,
                            color = popupTheme.secondaryContentColor
                        )
                        Slider(
                            value = currentVal,
                            onValueChange = {
                                currentVal = it
                                val hsv = floatArrayOf(currentHue, currentSat, currentVal)
                                val col = Color(android.graphics.Color.HSVToColor(hsv))
                                hexInputText = String.format("%06X", 0xFFFFFF and col.toArgb())
                            },
                            valueRange = 0.05f..1f,
                            colors = SliderDefaults.colors(
                                thumbColor = customPickedColor,
                                activeTrackColor = customPickedColor,
                                inactiveTrackColor = popupTheme.contentColor.copy(alpha = 0.15f)
                            )
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(customPickedColor)
                                .border(1.5.dp, popupTheme.contentColor.copy(alpha = 0.3f), CircleShape)
                        )
                        OutlinedTextField(
                            value = hexInputText,
                            onValueChange = { input ->
                                val filtered = input.filter { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }.take(6)
                                    .uppercase()
                                hexInputText = filtered
                                if (filtered.length == 6) {
                                    try {
                                        val parsed = filtered.toLong(16)
                                        val col = Color((0xFF000000 or parsed).toInt())
                                        val hsv = FloatArray(3)
                                        android.graphics.Color.colorToHSV(col.toArgb(), hsv)
                                        currentHue = hsv[0]
                                        currentSat = hsv[1]
                                        currentVal = hsv[2]
                                    } catch (_: Exception) {
                                    }
                                }
                            },
                            prefix = { Text("#", color = popupTheme.contentColor, fontWeight = FontWeight.Bold) },
                            singleLine = true,
                            label = { Text("HEX Code", color = popupTheme.secondaryContentColor) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = customPickedColor,
                                unfocusedBorderColor = popupTheme.contentColor.copy(alpha = 0.2f),
                                focusedTextColor = popupTheme.contentColor,
                                unfocusedTextColor = popupTheme.contentColor
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Button(
                        onClick = {
                            onSelect(AccentColor.custom(customPickedColor))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = customPickedColor),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            "Apply Custom Color",
                            fontWeight = FontWeight.Bold,
                            color = if (customPickedColor.luminance() > 0.5f) Color.Black else Color.White
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {}
    )
}

@Composable
private fun HueSlider(
    hue: Float,
    onHueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val hueColors = remember {
        listOf(
            Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red
        )
    }

    Slider(
        value = hue,
        onValueChange = onHueChange,
        valueRange = 0f..360f,
        modifier = modifier.drawBehind {
            val trackHeight = 8.dp.toPx()
            val top = (size.height - trackHeight) / 2
            drawRoundRect(
                brush = androidx.compose.ui.graphics.Brush.horizontalGradient(hueColors),
                topLeft = Offset(0f, top),
                size = Size(size.width, trackHeight),
                cornerRadius = CornerRadius(trackHeight / 2, trackHeight / 2)
            )
        },
        colors = SliderDefaults.colors(
            thumbColor = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, 1f))),
            activeTrackColor = Color.Transparent,
            inactiveTrackColor = Color.Transparent
        )
    )
}

@Composable
private fun PresetColorChip(
    accent: AccentColor,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    popupTheme: PopupTheme,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) accent.color.copy(alpha = 0.2f) else popupTheme.contentColor.copy(alpha = 0.05f))
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) accent.color else popupTheme.contentColor.copy(alpha = 0.1f),
                shape = RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(accent.color)
                .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
        )
        Text(
            text = accent.displayName,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = popupTheme.contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Row presenting default launcher preferences and app relaunch action.
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
                colors = ButtonDefaults.buttonColors(containerColor = primaryTextColor.color.copy(alpha = 0.07f)),
                border = BorderStroke(1.dp, accentColor.color.copy(alpha = 0.35f)),
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

@Composable
private fun HandOption(
    label: String,
    isSelected: Boolean,
    accentColor: AccentColor,
    shadow: Shadow?,
    onClick: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onClick() }) {
        RadioButton(
            selected = isSelected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(
                selectedColor = accentColor.color,
                unselectedColor = accentColor.color.copy(alpha = 0.3f)
            )
        )
        Text(
            label,
            color = if (isSelected) accentColor.color else accentColor.color.copy(alpha = 0.4f),
            style = TextStyle(shadow = shadow)
        )
    }
}

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
            Box(
                modifier = Modifier
                    .width(20.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (isSelected) accentColor.color else primaryTextColor.color.copy(alpha = 0.6f))
            )
        } else {
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
 * Selector for Primary Text Color (Main Color) using a split black/white capsule design.
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
            Row(modifier = Modifier.fillMaxSize()) {
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
 * Selector for Popup Theme (Dark / Light).
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
            Row(modifier = Modifier.fillMaxSize()) {
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
