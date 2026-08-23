package dev.msbs.cyclauncher.ui.components

import dev.msbs.cyclauncher.model.AppInfo
import dev.msbs.cyclauncher.model.Tag
import dev.msbs.cyclauncher.ui.theme.AccentColor
import dev.msbs.cyclauncher.ui.theme.PopupTheme
import dev.msbs.cyclauncher.ui.theme.PrimaryTextColor

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlin.math.roundToInt

/**
 * A popup context menu offering actions for a specific application (e.g. favorite toggle, uninstall, tag management, system info).
 * Correctly repositions itself to avoid screen boundary clipping.
 *
 * @param app The application metadata info.
 * @param isFavorite Current favorite status of the application.
 * @param offset The touch input position where the menu was triggered.
 * @param onDismiss Callback to close the menu.
 * @param onToggleFavorite Callback when the user toggles favorite status.
 * @param onUninstall Callback when the user requests to uninstall the application.
 * @param onInfo Callback when the user requests to view system app info.
 * @param onRename Callback when the user requests to rename the application.
 * @param onTagsClick Callback when the user requests to manage application tags.
 * @param accentColor The active theme accent color.
 */
@Composable
fun AppActionMenu(
    app: AppInfo,
    isFavorite: Boolean,
    offset: Offset,
    onDismiss: () -> Unit,
    onToggleFavorite: () -> Unit,
    onUninstall: () -> Unit,
    onInfo: () -> Unit,
    onRename: () -> Unit,
    onTagsClick: () -> Unit,
    accentColor: AccentColor = AccentColor.SKY,
    popupTheme: PopupTheme = PopupTheme.DARK
) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    
    val menuWidth = 240.dp
    val menuWidthPx = with(density) { menuWidth.toPx() }
    
    val itemsCount = 5
    val menuHeightPx = with(density) { (60 + itemsCount * 48).dp.toPx() }
    val borderPadding = with(density) { 16.dp.toPx() }

    var x = offset.x
    var y = offset.y

    if (x + menuWidthPx > screenWidthPx) x = screenWidthPx - menuWidthPx - borderPadding
    if (x < borderPadding) x = borderPadding
    if (y + menuHeightPx > screenHeightPx) y = screenHeightPx - menuHeightPx - borderPadding
    if (y < borderPadding) y = borderPadding

    Popup(
        offset = IntOffset(x.roundToInt(), y.roundToInt()),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        Box(
            modifier = Modifier
                .width(menuWidth)
                .clip(RoundedCornerShape(16.dp))
                .background(popupTheme.backgroundColor)
                .border(1.dp, popupTheme.borderColor, RoundedCornerShape(16.dp))
                .padding(vertical = 8.dp)
        ) {
            Column {
                Text(
                    text = app.label,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.titleSmall,
                    color = accentColor.color
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                MenuItem(
                    text = if (isFavorite) "Remove from Favorites" else "Add to Favorites",
                    icon = if (isFavorite) Icons.Outlined.Star else Icons.Outlined.StarOutline,
                    accentColor = accentColor,
                    popupTheme = popupTheme,
                    onClick = {
                        onToggleFavorite()
                        onDismiss()
                    }
                )

                MenuItem(
                    text = "Manage Tags",
                    icon = Icons.AutoMirrored.Outlined.Label,
                    accentColor = accentColor,
                    popupTheme = popupTheme,
                    onClick = {
                        onTagsClick()
                        onDismiss()
                    }
                )

                MenuItem(
                    text = "Rename",
                    icon = Icons.Outlined.Edit,
                    accentColor = accentColor,
                    popupTheme = popupTheme,
                    onClick = {
                        onRename()
                        onDismiss()
                    }
                )

                MenuItem(
                    text = "Info",
                    icon = Icons.Outlined.Info,
                    accentColor = accentColor,
                    popupTheme = popupTheme,
                    onClick = {
                        onInfo()
                        onDismiss()
                    }
                )

                MenuItem(
                    text = "Uninstall",
                    icon = Icons.Outlined.Delete,
                    accentColor = accentColor,
                    popupTheme = popupTheme,
                    onClick = {
                        onUninstall()
                        onDismiss()
                    }
                )
            }
        }
    }
}

/**
 * Context action menu for the history section, offering options to edit the history list
 * (remove items) or toggle whether history recording is paused.
 *
 * @param isHistoryPaused Current paused status of history recording.
 * @param hasHistoryItems True if there is at least one item in history.
 * @param offset The touch input position where the menu was triggered.
 * @param onDismiss Callback to close the menu.
 * @param onEditHistory Callback when the user selects to edit history.
 * @param onTogglePause Callback when the user toggles history recording pause status.
 * @param onClearHistory Callback when the user selects to clear all history items.
 * @param accentColor The active UI accent color.
 * @param primaryTextColor The primary text color.
 * @param popupTheme The popup theme setting (DARK or LIGHT).
 */
@Composable
fun HistoryActionMenu(
    isHistoryPaused: Boolean,
    hasHistoryItems: Boolean,
    offset: Offset,
    onDismiss: () -> Unit,
    onEditHistory: () -> Unit,
    onTogglePause: () -> Unit,
    onClearHistory: (() -> Unit)? = null,
    accentColor: AccentColor = AccentColor.SKY,
    primaryTextColor: PrimaryTextColor = PrimaryTextColor.WHITE,
    popupTheme: PopupTheme = PopupTheme.DARK
) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current

    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }

    val menuWidth = 250.dp
    val menuWidthPx = with(density) { menuWidth.toPx() }

    val itemsCount = if (hasHistoryItems && onClearHistory != null) 3 else 2
    val menuHeightPx = with(density) { (60 + itemsCount * 48).dp.toPx() }
    val borderPadding = with(density) { 16.dp.toPx() }

    var x = offset.x
    var y = offset.y

    if (x + menuWidthPx > screenWidthPx) x = screenWidthPx - menuWidthPx - borderPadding
    if (x < borderPadding) x = borderPadding
    if (y + menuHeightPx > screenHeightPx) y = screenHeightPx - menuHeightPx - borderPadding
    if (y < borderPadding) y = borderPadding

    Popup(
        offset = IntOffset(x.roundToInt(), y.roundToInt()),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        Box(
            modifier = Modifier
                .width(menuWidth)
                .clip(RoundedCornerShape(16.dp))
                .background(popupTheme.backgroundColor)
                .border(1.dp, popupTheme.borderColor, RoundedCornerShape(16.dp))
                .padding(vertical = 8.dp)
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.History,
                        contentDescription = null,
                        tint = accentColor.color,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "History",
                        style = MaterialTheme.typography.titleSmall,
                        color = popupTheme.contentColor,
                        fontWeight = FontWeight.Bold
                    )
                    if (isHistoryPaused) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "(Paused)",
                            style = MaterialTheme.typography.labelSmall,
                            color = popupTheme.secondaryContentColor
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                if (hasHistoryItems) {
                    MenuItem(
                        text = "Edit History",
                        icon = Icons.Outlined.Edit,
                        accentColor = accentColor,
                        popupTheme = popupTheme,
                        onClick = {
                            onDismiss()
                            onEditHistory()
                        }
                    )
                }

                MenuItem(
                    text = if (isHistoryPaused) "Resume Recording" else "Pause Recording",
                    icon = if (isHistoryPaused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause,
                    accentColor = accentColor,
                    popupTheme = popupTheme,
                    onClick = {
                        onDismiss()
                        onTogglePause()
                    }
                )

                if (hasHistoryItems && onClearHistory != null) {
                    MenuItem(
                        text = "Clear History",
                        icon = Icons.Outlined.Delete,
                        accentColor = accentColor,
                        popupTheme = popupTheme,
                        onClick = {
                            onDismiss()
                            onClearHistory()
                        }
                    )
                }
            }
        }
    }
}

/**
 * A standard menu item used within the AppActionMenu popup.
 */
@Composable
private fun MenuItem(
    text: String,
    icon: ImageVector,
    accentColor: AccentColor,
    popupTheme: PopupTheme = PopupTheme.DARK,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accentColor.color,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            color = popupTheme.contentColor,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

/**
 * A dialog allowing the user to view, edit, select, or create tags for a specific application.
 *
 * @param app The target application info.
 * @param allTags The list of all created tags.
 * @param assignedTagIds The list of tag IDs currently assigned to this app.
 * @param onToggleTag Callback triggered when toggling a tag's assignment.
 * @param onCreateTag Callback triggered when a new tag is created.
 * @param onUpdateTag Callback triggered when a tag is edited.
 * @param onDeleteTag Callback triggered when a tag is deleted.
 * @param onDismiss Callback to close the dialog.
 * @param accentColor The active UI accent color.
 * @param popupTheme The popup theme setting (DARK or LIGHT).
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun TagSelectionDialog(
    app: AppInfo,
    allTags: List<Tag>,
    assignedTagIds: List<String>,
    onToggleTag: (String) -> Unit,
    onCreateTag: (String, Color) -> Unit,
    onUpdateTag: (Tag) -> Unit,
    onDeleteTag: (String) -> Unit,
    onDismiss: () -> Unit,
    accentColor: AccentColor = AccentColor.SKY,
    popupTheme: PopupTheme = PopupTheme.DARK
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var tagToEdit by remember { mutableStateOf<Tag?>(null) }
    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("App Tags: ${app.label}", color = popupTheme.contentColor) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp)
                    .verticalScroll(scrollState)
            ) {
                if (allTags.isEmpty()) {
                    Text("No tags created yet.", color = popupTheme.secondaryContentColor, fontSize = 14.sp)
                } else {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        allTags.forEach { tag ->
                            val isAssigned = assignedTagIds.contains(tag.id)
                            Surface(
                                modifier = Modifier.combinedClickable(
                                    onClick = { onToggleTag(tag.id) },
                                    onLongClick = { tagToEdit = tag }
                                ),
                                shape = CircleShape,
                                color = if (isAssigned) tag.color.copy(alpha = 0.12f) else popupTheme.contentColor.copy(alpha = 0.04f),
                                border = BorderStroke(
                                    width = 1.dp, 
                                    color = if (isAssigned) tag.color else tag.color.copy(alpha = 0.38f)
                                )
                            ) {
                                Box(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = tag.name, 
                                        color = if (isAssigned) tag.color else popupTheme.contentColor, 
                                        fontSize = 13.sp,
                                        fontWeight = if (isAssigned) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { showCreateDialog = true }) {
                    Icon(Icons.Outlined.Add, null, modifier = Modifier.size(18.dp), tint = accentColor.color)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("New Tag", color = accentColor.color)
                }
                TextButton(onClick = onDismiss) {
                    Text("Done", fontWeight = FontWeight.Bold, color = accentColor.color)
                }
            }
        },
        containerColor = popupTheme.solidBackgroundColor,
        textContentColor = popupTheme.contentColor
    )

    if (showCreateDialog) {
        TagEditDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { name, color ->
                onCreateTag(name, color)
                showCreateDialog = false
            },
            accentColor = accentColor,
            popupTheme = popupTheme
        )
    }

    tagToEdit?.let { tag ->
        TagEditDialog(
            tag = tag,
            onDismiss = { tagToEdit = null },
            onConfirm = { name, color ->
                onUpdateTag(tag.copy(name = name, color = color))
                tagToEdit = null
            },
            onDelete = {
                onDeleteTag(tag.id)
                tagToEdit = null
            },
            accentColor = accentColor,
            popupTheme = popupTheme
        )
    }
}

/**
 * A dialog allowing the user to create a new tag or modify/delete an existing one.
 * Includes a text field for name input and a color selection grid.
 *
 * @param tag The tag instance being edited, or null if creating a new tag.
 * @param onDismiss Callback to close the dialog.
 * @param onConfirm Callback when saving or creating a tag (supplying name and color).
 * @param onDelete Callback when deleting this tag.
 * @param accentColor The active UI accent color.
 * @param popupTheme The popup theme setting (DARK or LIGHT).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagEditDialog(
    tag: Tag? = null,
    onDismiss: () -> Unit,
    onConfirm: (String, Color) -> Unit,
    onDelete: (() -> Unit)? = null,
    accentColor: AccentColor,
    popupTheme: PopupTheme = PopupTheme.DARK
) {
    var name by remember { mutableStateOf(tag?.name ?: "") }
    val colors = listOf(
        Color(0xFFEF4444), Color(0xFFF97316), Color(0xFFFACC15),
        Color(0xFF4ADE80), Color(0xFF2DD4BF), Color(0xFF3B82F6),
        Color(0xFF8B5CF6), Color(0xFFD946EF), Color(0xFF94A3B8)
    )
    var selectedColor by remember { mutableStateOf(tag?.color ?: colors[0]) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(if (tag == null) "Create New Tag" else "Edit Tag", color = popupTheme.contentColor)
                if (onDelete != null) {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Delete", tint = Color(0xFFEF4444))
                    }
                }
            }
        },
        text = {
            Column {
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text("Tag Name", color = popupTheme.secondaryContentColor) },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedTextColor = popupTheme.contentColor,
                        unfocusedTextColor = popupTheme.contentColor,
                        cursorColor = accentColor.color,
                        focusedIndicatorColor = accentColor.color
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text("Select Color:", color = popupTheme.contentColor, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    colors.forEach { color ->
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    width = if (selectedColor == color) 2.dp else 0.dp,
                                    color = popupTheme.contentColor,
                                    shape = CircleShape
                                )
                                .clickable { selectedColor = color }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onConfirm(name, selectedColor) },
                enabled = name.isNotBlank()
            ) {
                Text(if (tag == null) "Create" else "Save", fontWeight = FontWeight.Bold, color = accentColor.color)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = popupTheme.secondaryContentColor) }
        },
        containerColor = popupTheme.solidBackgroundColor,
        textContentColor = popupTheme.contentColor
    )
}

/**
 * A dialog displaying a single text field to rename an application.
 *
 * @param initialValue The original/current name of the application.
 * @param accentColor The active UI accent color.
 * @param popupTheme The popup theme setting (DARK or LIGHT).
 * @param onDismiss Callback to close the dialog.
 * @param onConfirm Callback when confirming the new name.
 */
@Composable
fun RenameDialog(
    initialValue: String,
    accentColor: AccentColor,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    popupTheme: PopupTheme = PopupTheme.DARK
) {
    var text by remember { mutableStateOf(initialValue) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename Application", color = accentColor.color) },
        text = {
            TextField(
                value = text,
                onValueChange = { text = it },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedTextColor = popupTheme.contentColor,
                    unfocusedTextColor = popupTheme.contentColor,
                    cursorColor = accentColor.color,
                    focusedIndicatorColor = accentColor.color
                ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) {
                Text("Rename", color = accentColor.color, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = popupTheme.secondaryContentColor)
            }
        },
        containerColor = popupTheme.solidBackgroundColor,
        textContentColor = popupTheme.contentColor
    )
}
