package dev.msbs.cyclauncher

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MainMenuScreen(
    viewModel: LauncherViewModel,
    onAppClick: (String) -> Unit,
    onAppLongClick: (AppInfo, Offset) -> Unit
) {
    val favorites by viewModel.favoriteApps.collectAsState()
    val history by viewModel.historyApps.collectAsState()
    val handSide by viewModel.handSide.collectAsState()

    Row(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp)
    ) {
        if (handSide == HandSide.LEFT) {
            FavoritesSection(Modifier.width(64.dp), favorites, onAppClick, onAppLongClick)
            Spacer(modifier = Modifier.width(32.dp))
            HistorySection(Modifier.weight(1f), history, handSide, onAppClick, onAppLongClick)
        } else {
            HistorySection(Modifier.weight(1f), history, handSide, onAppClick, onAppLongClick)
            Spacer(modifier = Modifier.width(32.dp))
            FavoritesSection(Modifier.width(64.dp), favorites, onAppClick, onAppLongClick)
        }
    }
}

@Composable
private fun HistorySection(
    modifier: Modifier,
    history: List<AppInfo>,
    handSide: HandSide,
    onAppClick: (String) -> Unit,
    onAppLongClick: (AppInfo, Offset) -> Unit
) {
    Column(
        modifier = modifier.fillMaxHeight(),
        verticalArrangement = Arrangement.Bottom,
        horizontalAlignment = if (handSide == HandSide.LEFT) Alignment.End else Alignment.Start
    ) {
        Text(
            "HISTORY", 
            color = Color.Cyan, 
            style = MaterialTheme.typography.titleSmall, 
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
                    onLongClick = { offset -> onAppLongClick(app, offset) }
                )
            }
        }
    }
}

@Composable
private fun FavoritesSection(
    modifier: Modifier,
    favorites: List<AppInfo>,
    onAppClick: (String) -> Unit,
    onAppLongClick: (AppInfo, Offset) -> Unit
) {
    Column(
        modifier = modifier.fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom
    ) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.Bottom),
            modifier = Modifier.fillMaxWidth(),
            reverseLayout = true
        ) {
            items(favorites, key = { "${it.packageName}_${it.label}" }) { app ->
                AppIconItem(
                    app = app,
                    onClick = { onAppClick("${app.packageName}/${app.activityName}") },
                    onLongClick = { offset -> onAppLongClick(app, offset) }
                )
            }
            item {
                Text("★", color = Color.Yellow, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}
