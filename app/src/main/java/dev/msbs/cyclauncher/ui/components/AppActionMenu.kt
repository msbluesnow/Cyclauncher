package dev.msbs.cyclauncher.ui.components

import dev.msbs.cyclauncher.model.AppInfo
import dev.msbs.cyclauncher.model.Tag
import dev.msbs.cyclauncher.ui.theme.AccentColor
import dev.msbs.cyclauncher.ui.theme.PopupTheme
import dev.msbs.cyclauncher.ui.theme.PrimaryTextColor
import dev.msbs.cyclauncher.ui.theme.LocalAnimationsEnabled

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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationEndReason
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlin.math.roundToInt

/**
 * Context action menu for an application item (favorites, tags, rename, info, uninstall).
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    val iconPainter = rememberAppIconPainter(app.iconKey, 20)
                    Image(
                        painter = iconPainter,
                        contentDescription = null,
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = app.label,
                        style = MaterialTheme.typography.titleSmall,
                        color = popupTheme.contentColor,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }

                HorizontalDivider(
                    color = popupTheme.dividerColor,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )

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
 * Context action menu for the history section (edit mode, pause/resume recording, clear).
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
 * Dialog for managing tags assigned to an application.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun TagSelectionDialog(
    app: AppInfo,
    allTags: List<Tag>,
    assignedTagIds: List<String>,
    onToggleTag: (String) -> Unit,
    onCreateTag: (String, Color, String?) -> Unit,
    onUpdateTag: (Tag) -> Unit,
    onDeleteTag: (String) -> Unit,
    onDismiss: () -> Unit,
    accentColor: AccentColor = AccentColor.SKY,
    buttonTextColor: PrimaryTextColor = PrimaryTextColor.WHITE,
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
                                color = if (isAssigned) tag.color.copy(alpha = 0.12f) else popupTheme.contentColor.copy(
                                    alpha = 0.04f
                                ),
                                border = BorderStroke(
                                    width = 1.dp,
                                    color = if (isAssigned) tag.color else tag.color.copy(alpha = 0.38f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val vectorIcon = TagIconRegistry.getVectorIcon(tag.emoji)
                                    if (vectorIcon != null) {
                                        Icon(
                                            imageVector = vectorIcon,
                                            contentDescription = null,
                                            tint = if (isAssigned) tag.color else popupTheme.contentColor,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(5.dp))
                                    } else if (!tag.emoji.isNullOrBlank()) {
                                        Text(
                                            text = tag.emoji,
                                            fontSize = 13.sp
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                    }
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
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { showCreateDialog = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accentColor.color,
                        contentColor = buttonTextColor.color
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = buttonTextColor.color
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("New Tag", fontWeight = FontWeight.Bold, color = buttonTextColor.color, fontSize = 14.sp)
                }
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accentColor.color,
                        contentColor = buttonTextColor.color
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Done", fontWeight = FontWeight.Bold, color = buttonTextColor.color, fontSize = 14.sp)
                }
            }
        },
        containerColor = popupTheme.solidBackgroundColor,
        textContentColor = popupTheme.contentColor
    )

    if (showCreateDialog) {
        TagEditDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { name, color, emoji ->
                onCreateTag(name, color, emoji)
                showCreateDialog = false
            },
            accentColor = accentColor,
            buttonTextColor = buttonTextColor,
            popupTheme = popupTheme
        )
    }

    tagToEdit?.let { tag ->
        TagEditDialog(
            tag = tag,
            onDismiss = { tagToEdit = null },
            onConfirm = { name, color, emoji ->
                onUpdateTag(tag.copy(name = name, color = color, emoji = emoji))
                tagToEdit = null
            },
            onDelete = {
                onDeleteTag(tag.id)
                tagToEdit = null
            },
            accentColor = accentColor,
            buttonTextColor = buttonTextColor,
            popupTheme = popupTheme
        )
    }
}

/**
 * Header for TagEditDialog showing title, smooth 2.3-second progress line, and trash icon.
 * Holding the trash icon animates the line from the title text towards the trash icon.
 */
@Composable
private fun TagEditHeader(
    title: String,
    titleColor: Color,
    onDelete: (() -> Unit)?,
    holdDurationMs: Long = 1800L,
    deleteColor: Color = Color(0xFFEF4444)
) {
    val haptic = LocalHapticFeedback.current
    var isPressed by remember { mutableStateOf(false) }
    val progress = remember { Animatable(0f) }

    LaunchedEffect(isPressed) {
        if (isPressed && onDelete != null) {
            val result = progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = holdDurationMs.toInt(),
                    easing = LinearEasing
                )
            )
            if (result.endReason == AnimationEndReason.Finished && progress.value >= 0.99f) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onDelete()
            }
        } else {
            progress.snapTo(0f)
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = titleColor,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp
        )

        if (onDelete != null) {
            // Animated progress line connecting the title to the trash icon
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp)
                    .height(3.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (progress.value > 0f) {
                    // Subtle background track
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .background(deleteColor.copy(alpha = 0.18f))
                    )
                    // Active filling line from title to trash icon
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress.value)
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .background(deleteColor)
                    )
                }
            }

            // Trash Icon Button
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (progress.value > 0f) deleteColor.copy(alpha = 0.15f) else Color.Transparent)
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            isPressed = true
                            waitForUpOrCancellation()
                            isPressed = false
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = "Hold 2.3s to delete",
                    tint = if (progress.value > 0f) deleteColor else deleteColor.copy(alpha = 0.85f),
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

/**
 * Dialog for creating, editing, or deleting a tag.
 * Supports Vector Icons (tints with tag color), Emojis, and interactive color picker.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagEditDialog(
    tag: Tag? = null,
    onDismiss: () -> Unit,
    onConfirm: (String, Color, String?) -> Unit,
    onDelete: (() -> Unit)? = null,
    accentColor: AccentColor,
    buttonTextColor: PrimaryTextColor = PrimaryTextColor.WHITE,
    popupTheme: PopupTheme = PopupTheme.DARK
) {
    val quickSwatches = remember {
        listOf(
            Color(0xFFF44336), Color(0xFFE91E63), Color(0xFF9C27B0), Color(0xFF673AB7),
            Color(0xFF3F51B5), Color(0xFF2196F3), Color(0xFF03A9F4), Color(0xFF00BCD4),
            Color(0xFF009688), Color(0xFF4CAF50), Color(0xFF8BC34A), Color(0xFFCDDC39),
            Color(0xFFFFEB3B), Color(0xFFFFC107), Color(0xFFFF9800), Color(0xFFFF5722)
        )
    }

    val emojiPresets = remember {
        listOf(
            "📁", "🎮", "💼", "💬", "🎵", "📸", "🌐", "🛒",
            "📚", "⚙️", "🎬", "🚀", "💰", "💡", "🍔", "⭐",
            "❤️", "🔥", "⚡", "🏠", "🔒", "🎨", "🛠️", "🎧"
        )
    }

    var name by remember { mutableStateOf(tag?.name ?: "") }
    var selectedColor by remember { mutableStateOf(tag?.color ?: quickSwatches[0]) }
    var emojiText by remember { mutableStateOf(tag?.emoji ?: "") }
    var showEmojiPicker by remember { mutableStateOf(true) }
    var showAllIconsDialog by remember { mutableStateOf(false) }
    var iconTab by remember { mutableIntStateOf(if (TagIconRegistry.isVectorIcon(tag?.emoji) || tag?.emoji.isNullOrBlank()) 0 else 1) }

    val isInitialCustom = remember(tag?.color) {
        tag?.color != null && !quickSwatches.contains(tag.color)
    }
    var selectedTab by remember { mutableIntStateOf(if (isInitialCustom) 1 else 0) }

    val initialHsv = remember(selectedColor) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(selectedColor.toArgb(), hsv)
        hsv
    }
    var currentHue by remember { mutableFloatStateOf(initialHsv[0]) }
    var currentSat by remember { mutableFloatStateOf(initialHsv[1].coerceAtLeast(0.1f)) }
    var currentVal by remember { mutableFloatStateOf(initialHsv[2].coerceAtLeast(0.1f)) }

    var hexInputText by remember {
        val argb = selectedColor.toArgb()
        mutableStateOf(String.format("%06X", 0xFFFFFF and argb))
    }

    val previewVectorIcon = remember(emojiText) { TagIconRegistry.getVectorIcon(emojiText) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            TagEditHeader(
                title = if (tag == null) "Create New Tag" else "Edit Tag",
                titleColor = popupTheme.contentColor,
                onDelete = onDelete
            )
        },
        text = {
            val animationsEnabled = LocalAnimationsEnabled.current
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Live Tag Preview Box
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(selectedColor.copy(alpha = 0.12f))
                        .border(1.dp, selectedColor.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f).padding(end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (previewVectorIcon != null) {
                            Icon(
                                imageVector = previewVectorIcon,
                                contentDescription = null,
                                tint = selectedColor,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        } else if (emojiText.isNotBlank()) {
                            Text(
                                text = emojiText.trim(),
                                fontSize = 18.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(selectedColor)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            text = if (name.isBlank()) "Tag Preview" else name,
                            color = popupTheme.contentColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(selectedColor.copy(alpha = 0.2f))
                            .border(0.8.dp, selectedColor.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (previewVectorIcon != null) {
                            Icon(
                                imageVector = previewVectorIcon,
                                contentDescription = null,
                                tint = selectedColor,
                                modifier = Modifier.size(14.dp)
                            )
                        } else if (emojiText.isNotBlank()) {
                            Text(
                                text = emojiText.trim(),
                                fontSize = 13.sp
                            )
                        } else {
                            Text(
                                text = "#",
                                color = selectedColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                // Row: Icon Selector Button + Tag Name Field
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Custom Icon / Emoji Button
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (emojiText.isNotBlank()) selectedColor.copy(alpha = 0.15f) else popupTheme.contentColor.copy(
                                    alpha = 0.08f
                                )
                            )
                            .border(
                                width = 1.dp,
                                color = if (emojiText.isNotBlank()) selectedColor else popupTheme.contentColor.copy(
                                    alpha = 0.2f
                                ),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable { showEmojiPicker = !showEmojiPicker },
                        contentAlignment = Alignment.Center
                    ) {
                        if (previewVectorIcon != null) {
                            Icon(
                                imageVector = previewVectorIcon,
                                contentDescription = "Vector Icon",
                                tint = selectedColor,
                                modifier = Modifier.size(24.dp)
                            )
                        } else if (emojiText.isNotBlank()) {
                            Text(
                                text = emojiText.trim(),
                                fontSize = 22.sp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.Folder,
                                contentDescription = "Choose Icon",
                                tint = popupTheme.secondaryContentColor,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    // Tag Name Field
                    AppOutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Tag Name", color = popupTheme.secondaryContentColor) },
                        placeholder = {
                            Text(
                                "e.g. Games, Work",
                                color = popupTheme.secondaryContentColor.copy(alpha = 0.6f)
                            )
                        },
                        textStyle = TextStyle(color = popupTheme.contentColor, fontSize = 15.sp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = selectedColor,
                            unfocusedBorderColor = popupTheme.contentColor.copy(alpha = 0.2f),
                            focusedTextColor = popupTheme.contentColor,
                            unfocusedTextColor = popupTheme.contentColor
                        ),
                        cursorColor = selectedColor,
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }

                // Expandable Icon & Emoji Picker Panel
                if (showEmojiPicker) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(popupTheme.contentColor.copy(alpha = 0.05f))
                            .border(1.dp, popupTheme.contentColor.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Folder Icon",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = popupTheme.secondaryContentColor
                            )
                            if (emojiText.isNotBlank()) {
                                Surface(
                                    shape = CircleShape,
                                    color = Color(0xFFEF4444).copy(alpha = 0.14f),
                                    border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.45f)),
                                    modifier = Modifier.clickable { emojiText = "" }
                                ) {
                                    Text(
                                        text = "Restore",
                                        fontSize = 11.sp,
                                        color = Color(0xFFEF4444),
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }

                        // Tab Switcher between Vector Icons and Emoji (themed with accentColor)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(popupTheme.contentColor.copy(alpha = 0.08f))
                                .padding(2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (iconTab == 0) accentColor.color.copy(alpha = 0.22f) else Color.Transparent)
                                    .clickable { iconTab = 0 }
                                    .padding(vertical = 5.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Vector Icons",
                                    fontSize = 11.sp,
                                    fontWeight = if (iconTab == 0) FontWeight.Bold else FontWeight.Normal,
                                    color = if (iconTab == 0) accentColor.color else popupTheme.secondaryContentColor
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (iconTab == 1) accentColor.color.copy(alpha = 0.22f) else Color.Transparent)
                                    .clickable { iconTab = 1 }
                                    .padding(vertical = 5.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Emoji",
                                    fontSize = 11.sp,
                                    fontWeight = if (iconTab == 1) FontWeight.Bold else FontWeight.Normal,
                                    color = if (iconTab == 1) accentColor.color else popupTheme.secondaryContentColor
                                )
                            }
                        }

                        if (iconTab == 0) {
                            // Material Vector Icons Grid (tinted with accentColor)
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                TagIconRegistry.PRIMARY_ICONS.forEach { vectorItem ->
                                    val formattedKey = TagIconRegistry.formatKey(vectorItem.key)
                                    val isSelected = emojiText == formattedKey || emojiText == vectorItem.key
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (isSelected) accentColor.color.copy(alpha = 0.22f) else popupTheme.contentColor.copy(
                                                    alpha = 0.04f
                                                )
                                            )
                                            .border(
                                                width = if (isSelected) 1.5.dp else 0.5.dp,
                                                color = if (isSelected) accentColor.color else popupTheme.contentColor.copy(
                                                    alpha = 0.15f
                                                ),
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .clickable {
                                                emojiText = if (isSelected) "" else formattedKey
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = vectorItem.icon,
                                            contentDescription = vectorItem.name,
                                            tint = if (isSelected) accentColor.color else popupTheme.contentColor.copy(
                                                alpha = 0.85f
                                            ),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }

                                // More Icons button "+"
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(accentColor.color.copy(alpha = 0.14f))
                                        .border(
                                            width = 1.dp,
                                            color = accentColor.color.copy(alpha = 0.5f),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable {
                                            showAllIconsDialog = true
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Add,
                                        contentDescription = "More Icons",
                                        tint = accentColor.color,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        } else {
                            // Emoji Tab (strictly 1 valid emoji or unicode symbol)
                            val currentEmojiValue = if (TagIconRegistry.isVectorIcon(emojiText)) "" else emojiText
                            AppOutlinedTextField(
                                value = currentEmojiValue,
                                onValueChange = { input ->
                                    if (input.isEmpty()) {
                                        emojiText = ""
                                    } else {
                                        val singleEmoji = EmojiUtils.extractSingleEmoji(input)
                                        if (singleEmoji != null) {
                                            emojiText = singleEmoji
                                        }
                                    }
                                },
                                placeholder = {
                                    Text(
                                        "Type or paste 1 emoji",
                                        fontSize = 12.sp,
                                        color = popupTheme.secondaryContentColor.copy(alpha = 0.6f)
                                    )
                                },
                                textStyle = TextStyle(color = popupTheme.contentColor, fontSize = 15.sp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = accentColor.color,
                                    unfocusedBorderColor = popupTheme.contentColor.copy(alpha = 0.2f),
                                    focusedTextColor = popupTheme.contentColor,
                                    unfocusedTextColor = popupTheme.contentColor
                                ),
                                cursorColor = accentColor.color,
                                shape = RoundedCornerShape(8.dp),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                emojiPresets.forEach { preset ->
                                    val isSelected = emojiText == preset
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSelected) accentColor.color.copy(alpha = 0.25f) else Color.Transparent)
                                            .border(
                                                width = if (isSelected) 1.5.dp else 0.5.dp,
                                                color = if (isSelected) accentColor.color else popupTheme.contentColor.copy(
                                                    alpha = 0.15f
                                                ),
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .clickable {
                                                emojiText = if (emojiText == preset) "" else preset
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(text = preset, fontSize = 18.sp)
                                    }
                                }
                            }
                        }
                    }
                }

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
                            .padding(vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Presets",
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
                            .padding(vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Custom Color",
                            fontSize = 12.sp,
                            fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedTab == 1) popupTheme.contentColor else popupTheme.secondaryContentColor
                        )
                    }
                }

                if (selectedTab == 0) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        quickSwatches.forEach { color ->
                            val isSelected = selectedColor == color
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(
                                        width = if (isSelected) 2.5.dp else 1.dp,
                                        color = if (isSelected) popupTheme.contentColor else Color.Black.copy(alpha = 0.2f),
                                        shape = CircleShape
                                    )
                                    .clickable {
                                        selectedColor = color
                                        val hsv = FloatArray(3)
                                        android.graphics.Color.colorToHSV(color.toArgb(), hsv)
                                        currentHue = hsv[0]
                                        currentSat = hsv[1]
                                        currentVal = hsv[2]
                                        hexInputText = String.format("%06X", 0xFFFFFF and color.toArgb())
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Outlined.Check,
                                        contentDescription = "Selected",
                                        tint = if (color.luminance() > 0.5f) Color.Black else Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                } else {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        quickSwatches.forEach { swatch ->
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(swatch)
                                    .border(
                                        width = if (selectedColor == swatch) 2.dp else 1.dp,
                                        color = if (selectedColor == swatch) popupTheme.contentColor else Color.Black.copy(
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
                                        selectedColor = swatch
                                    }
                            )
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Hue (${currentHue.toInt()}°)", fontSize = 11.sp, color = popupTheme.secondaryContentColor)
                        TagHueSlider(
                            hue = currentHue,
                            onHueChange = {
                                currentHue = it
                                val hsv = floatArrayOf(currentHue, currentSat, currentVal)
                                val col = Color(android.graphics.Color.HSVToColor(hsv))
                                hexInputText = String.format("%06X", 0xFFFFFF and col.toArgb())
                                selectedColor = col
                            }
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
                                selectedColor = col
                            },
                            valueRange = 0.05f..1f,
                            colors = SliderDefaults.colors(
                                thumbColor = selectedColor,
                                activeTrackColor = selectedColor,
                                inactiveTrackColor = popupTheme.contentColor.copy(alpha = 0.15f)
                            )
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
                                selectedColor = col
                            },
                            valueRange = 0.05f..1f,
                            colors = SliderDefaults.colors(
                                thumbColor = selectedColor,
                                activeTrackColor = selectedColor,
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
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(selectedColor)
                                .border(1.5.dp, popupTheme.contentColor.copy(alpha = 0.3f), CircleShape)
                        )
                        AppOutlinedTextField(
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
                                        selectedColor = col
                                    } catch (_: Exception) {
                                    }
                                }
                            },
                            prefix = { Text("#", color = popupTheme.contentColor, fontWeight = FontWeight.Bold) },
                            singleLine = true,
                            label = { Text("HEX Code", color = popupTheme.secondaryContentColor) },
                            textStyle = TextStyle(color = popupTheme.contentColor, fontSize = 15.sp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = selectedColor,
                                unfocusedBorderColor = popupTheme.contentColor.copy(alpha = 0.2f),
                                focusedTextColor = popupTheme.contentColor,
                                unfocusedTextColor = popupTheme.contentColor
                            ),
                            cursorColor = accentColor.color,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        val finalEmoji = emojiText.trim().takeIf { it.isNotBlank() }
                        onConfirm(name.trim(), selectedColor, finalEmoji)
                    }
                },
                enabled = name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = accentColor.color,
                    contentColor = buttonTextColor.color,
                    disabledContainerColor = accentColor.color.copy(alpha = 0.35f),
                    disabledContentColor = buttonTextColor.color.copy(alpha = 0.45f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = if (tag == null) "Create" else "Save",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Cancel", color = popupTheme.secondaryContentColor)
            }
        },
        containerColor = popupTheme.solidBackgroundColor,
        textContentColor = popupTheme.contentColor,
        shape = RoundedCornerShape(20.dp)
    )

    if (showAllIconsDialog) {
        AllTagVectorIconsDialog(
            selectedKey = emojiText,
            selectedColor = selectedColor,
            popupTheme = popupTheme,
            accentColor = accentColor,
            buttonTextColor = buttonTextColor,
            onSelectIcon = { newKey ->
                emojiText = newKey
                showAllIconsDialog = false
            },
            onDismiss = { showAllIconsDialog = false }
        )
    }
}

/**
 * Modal dialog for browsing, filtering by categories, and searching all available Material Vector Icons.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AllTagVectorIconsDialog(
    selectedKey: String,
    selectedColor: Color,
    popupTheme: PopupTheme,
    accentColor: AccentColor,
    buttonTextColor: PrimaryTextColor,
    onSelectIcon: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryIndex by remember { mutableIntStateOf(0) }

    val filteredIcons = remember(searchQuery, selectedCategoryIndex) {
        val q = searchQuery.trim().lowercase()
        if (q.isNotEmpty()) {
            TagIconRegistry.ALL_ICONS.filter {
                it.name.lowercase().contains(q) || it.key.lowercase().contains(q)
            }
        } else if (selectedCategoryIndex == 0) {
            TagIconRegistry.ALL_ICONS
        } else {
            TagIconRegistry.CATEGORIES.getOrNull(selectedCategoryIndex - 1)?.icons ?: TagIconRegistry.ALL_ICONS
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Vector Icons",
                    color = popupTheme.contentColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Close",
                        tint = popupTheme.secondaryContentColor
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Search Bar
                AppOutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            "Search icons...",
                            fontSize = 13.sp,
                            color = popupTheme.secondaryContentColor.copy(alpha = 0.6f)
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = null,
                            tint = popupTheme.secondaryContentColor,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = "Clear",
                                    tint = popupTheme.secondaryContentColor,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    },
                    textStyle = TextStyle(color = popupTheme.contentColor, fontSize = 14.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accentColor.color,
                        unfocusedBorderColor = popupTheme.contentColor.copy(alpha = 0.2f),
                        focusedTextColor = popupTheme.contentColor,
                        unfocusedTextColor = popupTheme.contentColor
                    ),
                    cursorColor = accentColor.color,
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Category Chips (when not searching)
                if (searchQuery.isEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val categoryTitles = remember { listOf("All") + TagIconRegistry.CATEGORIES.map { it.title } }
                        categoryTitles.forEachIndexed { index, title ->
                            val isSelected = selectedCategoryIndex == index
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) accentColor.color.copy(alpha = 0.22f) else popupTheme.contentColor.copy(
                                    alpha = 0.06f
                                ),
                                border = BorderStroke(
                                    width = if (isSelected) 1.2.dp else 0.5.dp,
                                    color = if (isSelected) accentColor.color else popupTheme.contentColor.copy(alpha = 0.15f)
                                ),
                                modifier = Modifier.clickable { selectedCategoryIndex = index }
                            ) {
                                Text(
                                    text = title,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) accentColor.color else popupTheme.secondaryContentColor,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                }

                // Icons Grid
                if (filteredIcons.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No icons found",
                            color = popupTheme.secondaryContentColor,
                            fontSize = 13.sp
                        )
                    }
                } else {
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        filteredIcons.forEach { item ->
                            val formattedKey = TagIconRegistry.formatKey(item.key)
                            val isSelected = selectedKey == formattedKey || selectedKey == item.key
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (isSelected) accentColor.color.copy(alpha = 0.25f) else popupTheme.contentColor.copy(
                                            alpha = 0.04f
                                        )
                                    )
                                    .border(
                                        width = if (isSelected) 1.5.dp else 0.5.dp,
                                        color = if (isSelected) accentColor.color else popupTheme.contentColor.copy(
                                            alpha = 0.15f
                                        ),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .clickable {
                                        onSelectIcon(formattedKey)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = item.name,
                                    tint = if (isSelected) accentColor.color else popupTheme.contentColor.copy(alpha = 0.85f),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = accentColor.color),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Close", color = buttonTextColor.color, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        },
        containerColor = popupTheme.solidBackgroundColor,
        textContentColor = popupTheme.contentColor
    )
}

@Composable
private fun TagHueSlider(
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
                brush = Brush.horizontalGradient(hueColors),
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

/**
 * Dialog for renaming an application label.
 */
@Composable
fun RenameDialog(
    initialValue: String,
    accentColor: AccentColor,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    buttonTextColor: PrimaryTextColor = PrimaryTextColor.WHITE,
    popupTheme: PopupTheme = PopupTheme.DARK
) {
    var text by remember { mutableStateOf(initialValue) }
    val animationsEnabled = LocalAnimationsEnabled.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename Application", color = accentColor.color) },
        text = {
            AppTextField(
                value = text,
                onValueChange = { text = it },
                textStyle = TextStyle(color = popupTheme.contentColor, fontSize = 15.sp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedTextColor = popupTheme.contentColor,
                    unfocusedTextColor = popupTheme.contentColor,
                    focusedIndicatorColor = accentColor.color
                ),
                cursorColor = accentColor.color,
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { if (text.isNotBlank()) onConfirm(text.trim()) },
                enabled = text.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = accentColor.color,
                    contentColor = buttonTextColor.color,
                    disabledContainerColor = accentColor.color.copy(alpha = 0.35f),
                    disabledContentColor = buttonTextColor.color.copy(alpha = 0.45f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Rename", color = buttonTextColor.color, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Cancel", color = popupTheme.secondaryContentColor)
            }
        },
        containerColor = popupTheme.solidBackgroundColor,
        textContentColor = popupTheme.contentColor,
        shape = RoundedCornerShape(20.dp)
    )
}
