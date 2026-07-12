package dev.msbs.cyclauncher

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

@Composable
fun SettingsScreen(
    viewModel: LauncherViewModel,
    onBack: () -> Unit
) {
    val handSide by viewModel.handSide.collectAsState()
    val accentColor by viewModel.accentColor.collectAsState()
    val showShadows by viewModel.showShadows.collectAsState()
    
    var showDefaultLauncherDialog by remember { mutableStateOf(false) }
    var currentIsDefault by remember { mutableStateOf(viewModel.isDefaultLauncher()) }

    // Observe lifecycle to refresh default launcher status when user returns from settings
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                currentIsDefault = viewModel.isDefaultLauncher()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val shadow = if (showShadows) {
        Shadow(
            color = Color.Black.copy(alpha = 0.6f),
            offset = Offset(2f, 2f),
            blurRadius = 4f
        )
    } else null

    BackHandler(onBack = onBack)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
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

        // Glassmorphic Settings Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.White.copy(alpha = 0.05f)
            ),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                // Handside Selector
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Preferred Hand:", 
                        color = Color.White, 
                        style = TextStyle(shadow = shadow, fontSize = 16.sp)
                    )
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        HandOption("Left", handSide == HandSide.LEFT, accentColor, showShadows) {
                            viewModel.setHandSide(HandSide.LEFT)
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        HandOption("Right", handSide == HandSide.RIGHT, accentColor, showShadows) {
                            viewModel.setHandSide(HandSide.RIGHT)
                        }
                    }
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 12.dp))

                // Accent Color Selector (Dropdown)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Theme Accent:", 
                        color = Color.White, 
                        style = TextStyle(shadow = shadow, fontSize = 16.sp)
                    )
                    
                    AccentColorDropdown(accentColor) { viewModel.setAccentColor(it) }
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 12.dp))

                // Adaptive Shadows Toggle
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Adaptive Shadows:", 
                        color = Color.White, 
                        style = TextStyle(shadow = shadow, fontSize = 16.sp)
                    )
                    Switch(
                        checked = showShadows,
                        onCheckedChange = { viewModel.setShowShadows(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = accentColor.color,
                            checkedTrackColor = accentColor.color.copy(alpha = 0.5f)
                        )
                    )
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 12.dp))

                // Default Launcher Selector
                DefaultLauncherSelector(
                    isDefault = currentIsDefault,
                    accentColor = accentColor,
                    showShadows = showShadows,
                    onClick = { 
                        viewModel.openDefaultLauncherSettings()
                        showDefaultLauncherDialog = true
                    }
                )
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        Button(
            onClick = onBack,
            colors = ButtonDefaults.buttonColors(containerColor = accentColor.color),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Text(
                "Back to Home", 
                color = Color.Black, 
                fontWeight = FontWeight.Bold, 
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }

    if (showDefaultLauncherDialog) {
        AlertDialog(
            onDismissRequest = { showDefaultLauncherDialog = false },
            title = { Text("Default Launcher", color = accentColor.color) },
            text = { 
                Text(
                    if (currentIsDefault) 
                        "Cyclauncher is now your default launcher!" 
                    else 
                        "Cyclauncher is not set as default. Would you like to try again?",
                    color = Color.White
                ) 
            },
            confirmButton = {
                TextButton(onClick = {
                    if (!currentIsDefault) {
                        viewModel.openDefaultLauncherSettings()
                    } else {
                        showDefaultLauncherDialog = false
                    }
                }) {
                    Text(if (currentIsDefault) "Great!" else "Set Default", color = accentColor.color)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDefaultLauncherDialog = false }) {
                    Text("Cancel", color = Color.Gray)
                }
            },
            containerColor = Color(0xFF1E1E1E),
            textContentColor = Color.White
        )
    }
}

@Composable
private fun AccentColorDropdown(
    selectedColor: AccentColor,
    onSelect: (AccentColor) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = 0.1f))
                .clickable { expanded = true }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(selectedColor.color)
            )
            Text(selectedColor.displayName, color = Color.White)
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(Color(0xFF2D2D2D))
        ) {
            AccentColor.entries.forEach { color ->
                DropdownMenuItem(
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(color.color)
                            )
                            Text(color.displayName, color = Color.White)
                        }
                    },
                    onClick = {
                        onSelect(color)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun DefaultLauncherSelector(
    isDefault: Boolean, 
    accentColor: AccentColor,
    showShadows: Boolean,
    onClick: () -> Unit
) {
    val shadow = if (showShadows) {
        Shadow(
            color = Color.Black.copy(alpha = 0.6f),
            offset = Offset(2f, 2f),
            blurRadius = 4f
        )
    } else null

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            "Default Launcher", 
            color = Color.White, 
            style = TextStyle(shadow = shadow, fontWeight = FontWeight.Medium, fontSize = 18.sp)
        )
        Text(
            if (isDefault) "Currently set as default" else "Not set as default", 
            color = if (isDefault) Color.Green else Color.Gray, 
            fontSize = 12.sp,
            style = TextStyle(shadow = shadow)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isDefault) Color.Transparent else Color.White.copy(alpha = 0.1f)
            ),
            border = if (isDefault) BorderStroke(1.dp, Color.Green.copy(alpha = 0.5f)) else null,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                if (isDefault) "Change Default" else "Set as Default", 
                color = if (isDefault) Color.Green else Color.White,
                style = TextStyle(shadow = shadow, fontWeight = FontWeight.Bold)
            )
        }
    }
}

@Composable
private fun HandOption(
    label: String,
    isSelected: Boolean,
    accentColor: AccentColor,
    showShadows: Boolean,
    onClick: () -> Unit
) {
    val shadow = if (showShadows) {
        Shadow(
            color = Color.Black.copy(alpha = 0.6f),
            offset = Offset(2f, 2f),
            blurRadius = 4f
        )
    } else null

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clickable { onClick() }
    ) {
        RadioButton(
            selected = isSelected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(
                selectedColor = accentColor.color,
                unselectedColor = Color.Gray
            )
        )
        Text(
            label, 
            color = if (isSelected) accentColor.color else Color.White,
            style = TextStyle(shadow = shadow)
        )
    }
}
