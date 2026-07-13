package dev.msbs.cyclauncher

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MainMenuScreen(
    viewModel: LauncherViewModel,
    onAppClick: (String) -> Unit,
    onAppLongClick: (AppInfo, Offset) -> Unit,
    onSwipeUp: () -> Unit,
    onSwipeDown: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val favorites by viewModel.favoriteApps.collectAsState()
    val history by viewModel.historyApps.collectAsState()
    val handSide by viewModel.handSide.collectAsState()
    val showShadows by viewModel.showShadows.collectAsState()
    val accentColor by viewModel.accentColor.collectAsState()

    Row(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp)
    ) {
        val favoritesWeight = 1f
        val historyWeight = 1f

        if (handSide == HandSide.LEFT) {
            FavoritesSection(
                Modifier.weight(favoritesWeight), 
                favorites, 
                accentColor,
                showShadows,
                onAppClick, 
                onAppLongClick,
                onSwipeUp,
                onSwipeDown,
                onSettingsClick
            )
            Spacer(modifier = Modifier.width(16.dp))
            HistorySection(Modifier.weight(historyWeight), history, handSide, showShadows, accentColor, onAppClick, onAppLongClick, onSettingsClick)
        } else {
            HistorySection(Modifier.weight(historyWeight), history, handSide, showShadows, accentColor, onAppClick, onAppLongClick, onSettingsClick)
            Spacer(modifier = Modifier.width(16.dp))
            FavoritesSection(
                Modifier.weight(favoritesWeight), 
                favorites, 
                accentColor,
                showShadows,
                onAppClick, 
                onAppLongClick,
                onSwipeUp,
                onSwipeDown,
                onSettingsClick
            )
        }
    }
}

@Composable
private fun HistorySection(
    modifier: Modifier,
    history: List<AppInfo>,
    handSide: HandSide,
    showShadows: Boolean,
    accentColor: AccentColor,
    onAppClick: (String) -> Unit,
    onAppLongClick: (AppInfo, Offset) -> Unit,
    onSettingsClick: () -> Unit
) {
    val shadow = if (showShadows) {
        Shadow(
            color = Color.Black.copy(alpha = 0.6f),
            offset = Offset(2f, 2f),
            blurRadius = 4f
        )
    } else null

    Column(
        modifier = modifier
            .fillMaxHeight()
            .pointerInput(Unit) {
                detectTapGestures(onLongPress = { onSettingsClick() })
            },
        verticalArrangement = Arrangement.Bottom,
        horizontalAlignment = if (handSide == HandSide.LEFT) Alignment.End else Alignment.Start
    ) {
        Text(
            "HISTORY", 
            color = accentColor.color,
            style = MaterialTheme.typography.titleSmall.copy(shadow = shadow), 
            fontWeight = FontWeight.Bold,
            textAlign = if (handSide == HandSide.LEFT) TextAlign.End else TextAlign.Start,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.Bottom),
            modifier = Modifier.fillMaxWidth(),
            reverseLayout = true
        ) {
            items(history, key = { "${it.packageName}_${it.label}" }) { app ->
                AppListItemWithIcon(
                    app = app,
                    handSide = if (handSide == HandSide.LEFT) HandSide.RIGHT else HandSide.LEFT,
                    iconSize = 44,
                    fontSize = 20,
                    onClick = { onAppClick("${app.packageName}/${app.activityName}") },
                    onLongClick = { offset -> onAppLongClick(app, offset) },
                    showShadows = showShadows
                )
            }
        }
    }
}

@Composable
private fun FavoritesSection(
    modifier: Modifier,
    favorites: List<AppInfo>,
    accentColor: AccentColor,
    showShadows: Boolean,
    onAppClick: (String) -> Unit,
    onAppLongClick: (AppInfo, Offset) -> Unit,
    onSwipeUp: () -> Unit,
    onSwipeDown: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val shadow = if (showShadows) {
        Shadow(
            color = Color.Black.copy(alpha = 0.6f),
            offset = Offset(2f, 2f),
            blurRadius = 4f
        )
    } else null

    Column(
        modifier = modifier
            .fillMaxHeight()
            .pointerInput(Unit) {
                detectTapGestures(onLongPress = { onSettingsClick() })
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures { _, dragAmount ->
                    if (dragAmount > 60) {
                        onSwipeDown()
                    } else if (dragAmount < -40) {
                        onSwipeUp()
                    }
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom
    ) {
        // Simplified Favorites display to avoid scroll conflicts with navigation
        favorites.reversed().take(10).forEach { app ->
            AppIconItem(
                app = app,
                onClick = { onAppClick("${app.packageName}/${app.activityName}") },
                onLongClick = { offset -> onAppLongClick(app, offset) }
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
        
        Text(
            text = "★", 
            color = accentColor.color, 
            fontSize = 24.sp, 
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.bodyLarge.copy(shadow = shadow)
        )
        Spacer(modifier = Modifier.height(4.dp))
    }
}
