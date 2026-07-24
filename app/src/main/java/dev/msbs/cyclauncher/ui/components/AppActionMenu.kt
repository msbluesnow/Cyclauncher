package dev.msbs.cyclauncher.ui.components

import dev.msbs.cyclauncher.model.AppInfo
import dev.msbs.cyclauncher.model.Tag
import dev.msbs.cyclauncher.ui.theme.AccentColor
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
 * A popup context menu offering actions for a specific application (e.g. favorite toggle, uninstall, tag management).
 * Correctly repositions itself to avoid screen boundary clipping.
 *
 * @param app The application metadata info.
 * @param isFavorite Current favorite status of the application.
 * @param showRemoveFromHistory True if "Remove from History" option should be shown.
 * @param offset The touch input position where the menu was triggered.
 * @param onDismiss Callback to close the menu.
 * @param onToggleFavorite Callback when the user toggles favorite status.
 * @param onUninstall Callback when the user requests to uninstall the application.
 * @param onRemoveFromHistory Callback when the user requests to remove the application from history.
 * @param onRename Callback when the user requests to rename the application.
 * @param onTagsClick Callback when the user requests to manage application tags.
 * @param accentColor The active theme accent color.
 */
@Composable
fun AppActionMenu(
    app: AppInfo,
    isFavorite: Boolean,
    showRemoveFromHistory: Boolean = false,
    offset: Offset,
    onDismiss: () -> Unit,
    onToggleFavorite: () -> Unit,
    onUninstall: () -> Unit,
    onRemoveFromHistory: () -> Unit = {},
    onRename: () -> Unit,
    onTagsClick: () -> Unit,
    accentColor: AccentColor = AccentColor.SKY
) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    
    val menuWidth = 240.dp
    val menuWidthPx = with(density) { menuWidth.toPx() }
    
    val itemsCount = if (showRemoveFromHistory) { 5 } else { 4 }
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
                .background(Color.Black.copy(alpha = 0.85f))
                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
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
                    onClick = {
                        onToggleFavorite()
                        onDismiss()
                    }
                )

                if (showRemoveFromHistory) {
                    MenuItem(
                        text = "Remove from History",
                        icon = Icons.Outlined.History,
                        accentColor = accentColor,
                        onClick = {
                            onRemoveFromHistory()
                            onDismiss()
                        }
                    )
                }

                MenuItem(
                    text = "Manage Tags",
                    icon = Icons.AutoMirrored.Outlined.Label,
                    accentColor = accentColor,
                    onClick = {
                        onTagsClick()
                        onDismiss()
                    }
                )

                MenuItem(
                    text = "Rename App",
                    icon = Icons.Outlined.Edit,
                    accentColor = accentColor,
                    onClick = {
                        onRename()
                        onDismiss()
                    }
                )

                MenuItem(
                    text = "Uninstall App",
                    icon = Icons.Outlined.Delete,
                    accentColor = accentColor,
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
 * A standard menu item used within the AppActionMenu popup.
 */
@Composable
private fun MenuItem(
    text: String,
    icon: ImageVector,
    accentColor: AccentColor,
    primaryTextColor: PrimaryTextColor = PrimaryTextColor.WHITE,
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
            color = primaryTextColor.color,
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
    accentColor: AccentColor = AccentColor.SKY
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var tagToEdit by remember { mutableStateOf<Tag?>(null) }
    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("App Tags: ${app.label}", color = Color.White) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp)
                    .verticalScroll(scrollState)
            ) {
                if (allTags.isEmpty()) {
                    Text("No tags created yet.", color = Color.Gray, fontSize = 14.sp)
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
                                color = if (isAssigned) tag.color.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.03f),
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
                                        color = if (isAssigned) tag.color else Color.White, 
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
        containerColor = Color(0xFF1E1E1E),
        textContentColor = Color.White
    )

    if (showCreateDialog) {
        TagEditDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { name, color ->
                onCreateTag(name, color)
                showCreateDialog = false
            },
            accentColor = accentColor
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
            accentColor = accentColor
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
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagEditDialog(
    tag: Tag? = null,
    onDismiss: () -> Unit,
    onConfirm: (String, Color) -> Unit,
    onDelete: (() -> Unit)? = null,
    accentColor: AccentColor
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
                Text(if (tag == null) "Create New Tag" else "Edit Tag", color = Color.White)
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
                    placeholder = { Text("Tag Name") },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = accentColor.color,
                        focusedIndicatorColor = accentColor.color
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text("Select Color:", color = Color.White, fontSize = 14.sp)
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
                                    color = Color.White,
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
            TextButton(onClick = onDismiss) { Text("Cancel", color = Color.Gray) }
        },
        containerColor = Color(0xFF1E1E1E),
        textContentColor = Color.White
    )
}

/**
 * A dialog displaying a single text field to rename an application.
 *
 * @param initialValue The original/current name of the application.
 * @param accentColor The active UI accent color.
 * @param onDismiss Callback to close the dialog.
 * @param onConfirm Callback when confirming the new name.
 */
@Composable
fun RenameDialog(
    initialValue: String,
    accentColor: AccentColor,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
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
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
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
                Text("Cancel", color = Color.Gray)
            }
        },
        containerColor = Color(0xFF1E1E1E),
        textContentColor = Color.White
    )
}
