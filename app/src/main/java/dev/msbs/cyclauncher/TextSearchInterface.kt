package dev.msbs.cyclauncher

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun TextSearchInterface(
    viewModel: LauncherViewModel,
    onAppClick: (String) -> Unit,
    onAppLongClick: (AppInfo, androidx.compose.ui.geometry.Offset) -> Unit
) {
    val searchText by viewModel.searchText.collectAsState()
    val filteredApps by viewModel.textFilteredApps.collectAsState()
    val handSide by viewModel.handSide.collectAsState()
    val showShadows by viewModel.showShadows.collectAsState()
    val accentColor by viewModel.accentColor.collectAsState()
    
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(focusRequester) {
        // Small delay to allow layout to settle before requesting focus,
        // which prevents attachment crashes on some physical devices.
        kotlinx.coroutines.delay(100)
        try {
            focusRequester.requestFocus()
        } catch (e: Exception) {
            // Safe fallback
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(16.dp),
        horizontalAlignment = if (handSide == HandSide.LEFT) Alignment.Start else Alignment.End
    ) {
        SearchTextField(
            value = searchText,
            onValueChange = { viewModel.setSearchText(it) },
            handSide = handSide,
            accentColor = accentColor,
            showShadows = showShadows,
            modifier = Modifier.focusRequester(focusRequester)
        )

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = if (handSide == HandSide.LEFT) Alignment.Start else Alignment.End
        ) {
            items(filteredApps, key = { "${it.packageName}/${it.activityName}" }) { app ->
                AppListItemWithIcon(
                    app = app,
                    handSide = handSide,
                    onClick = { onAppClick("${app.packageName}/${app.activityName}") },
                    onLongClick = { offset -> onAppLongClick(app, offset) },
                    showShadows = showShadows
                )
            }
        }
        
        CloseSearchButton(showShadows) { viewModel.toggleTextSearchMode() }
    }
}

@Composable
private fun SearchTextField(
    value: String,
    onValueChange: (String) -> Unit,
    handSide: HandSide,
    accentColor: AccentColor,
    showShadows: Boolean,
    modifier: Modifier = Modifier
) {
    val alignment = if (handSide == HandSide.LEFT) TextAlign.Start else TextAlign.End
    val shadow = if (showShadows) {
        Shadow(
            color = Color.Black.copy(alpha = 0.6f),
            offset = Offset(2f, 2f),
            blurRadius = 4f
        )
    } else null

    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { 
            Text(
                "Search apps...", 
                color = Color.White.copy(alpha = 0.6f),
                textAlign = alignment,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodyLarge.copy(shadow = shadow)
            ) 
        },
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            textAlign = alignment,
            shadow = shadow
        ),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            cursorColor = accentColor.color,
            focusedIndicatorColor = accentColor.color
        ),
        singleLine = true
    )
}

@Composable
private fun CloseSearchButton(showShadows: Boolean, onClick: () -> Unit) {
    val shadow = if (showShadows) {
        Shadow(
            color = Color.Black.copy(alpha = 0.6f),
            offset = Offset(2f, 2f),
            blurRadius = 4f
        )
    } else null

    IconButton(
        onClick = onClick,
        modifier = Modifier.padding(bottom = 16.dp)
    ) {
        Text(
            "✕", 
            color = Color.White, 
            style = MaterialTheme.typography.headlineMedium.copy(shadow = shadow)
        )
    }
}
