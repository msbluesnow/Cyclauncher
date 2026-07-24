package dev.msbs.cyclauncher.ui.screens

import dev.msbs.cyclauncher.LauncherViewModel
import dev.msbs.cyclauncher.HandSide
import dev.msbs.cyclauncher.model.AppInfo
import dev.msbs.cyclauncher.ui.theme.AccentColor
import dev.msbs.cyclauncher.ui.theme.PrimaryTextColor
import dev.msbs.cyclauncher.ui.components.AppListItemWithIcon

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

/**
 * Layout representing the full-text keyboard-based search screen.
 * Displays a search text field and a dynamic scrollable list of matched applications.
 *
 * @param viewModel The view model supplying state data.
 * @param onAppClick Callback when an application is clicked.
 * @param onAppLongClick Callback when an application is long-pressed.
 */
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
    val primaryTextColor by viewModel.primaryTextColor.collectAsState()
    
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
            primaryTextColor = primaryTextColor,
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
                    primaryTextColor = primaryTextColor,
                    showShadows = showShadows
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        CloseSearchButton(
            handSide = handSide,
            accentColor = accentColor,
            primaryTextColor = primaryTextColor,
            showShadows = showShadows,
            onClick = { viewModel.toggleTextSearchMode() }
        )
    }
}

/**
 * Button to close the text search interface, aligned left or right depending on preferred hand.
 */
@Composable
private fun CloseSearchButton(
    handSide: HandSide,
    accentColor: AccentColor,
    primaryTextColor: PrimaryTextColor,
    showShadows: Boolean,
    onClick: () -> Unit
) {
    val shadow = primaryTextColor.getShadow(showShadows)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp, start = 8.dp, end = 8.dp),
        horizontalArrangement = if (handSide == HandSide.LEFT) Arrangement.Start else Arrangement.End
    ) {
        IconButton(onClick = onClick) {
            Text(
                "✕",
                color = accentColor.color,
                style = MaterialTheme.typography.headlineMedium.copy(shadow = shadow)
            )
        }
    }
}

/**
 * Text field for entering search queries. Focuses automatically on launch.
 */
@Composable
private fun SearchTextField(
    value: String,
    onValueChange: (String) -> Unit,
    handSide: HandSide,
    accentColor: AccentColor,
    primaryTextColor: PrimaryTextColor,
    showShadows: Boolean,
    modifier: Modifier = Modifier
) {
    val alignment = if (handSide == HandSide.LEFT) TextAlign.Start else TextAlign.End
    val shadow = primaryTextColor.getShadow(showShadows)

    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { 
            Text(
                "Search apps...", 
                color = primaryTextColor.color.copy(alpha = 0.6f),
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
            focusedTextColor = primaryTextColor.color,
            unfocusedTextColor = primaryTextColor.color,
            cursorColor = accentColor.color,
            focusedIndicatorColor = accentColor.color
        ),
        singleLine = true
    )
}
