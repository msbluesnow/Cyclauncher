package dev.msbs.cyclauncher.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import dev.msbs.cyclauncher.model.AppInfo
import dev.msbs.cyclauncher.model.Tag
import dev.msbs.cyclauncher.ui.theme.AccentColor
import dev.msbs.cyclauncher.ui.theme.PrimaryTextColor
import kotlin.math.roundToInt

/**
 * A folder-styled UI component representing a custom tag.
 * Shows a border with the tag's color and a 2x2 grid containing up to 4 app icons inside.
 *
 * @param tag The tag instance.
 * @param apps The list of applications associated with this tag.
 * @param onClick Callback triggered when the tag folder is tapped (provides item Offset).
 * @param onLongClick Callback triggered when the tag folder is long-pressed (provides item Offset).
 * @param primaryTextColor Theme text color setting.
 * @param showShadows Whether drop shadows are enabled.
 */
@Composable
fun TagFolderItem(
    tag: Tag,
    apps: List<AppInfo>,
    onClick: (Offset) -> Unit,
    onLongClick: (Offset) -> Unit = {},
    primaryTextColor: PrimaryTextColor = PrimaryTextColor.WHITE,
    showShadows: Boolean = false
) {
    val previewApps = remember(apps) { apps.take(4) }
    var itemPosition by remember { mutableStateOf(Offset.Zero) }
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnLongClick by rememberUpdatedState(onLongClick)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { itemPosition = it.positionInRoot() }
            .pointerInput(tag.id) {
                detectTapGestures(
                    onTap = { currentOnClick(itemPosition + it) },
                    onLongPress = { currentOnLongClick(itemPosition + it) }
                )
            }
            .padding(vertical = 4.dp, horizontal = 2.dp)
    ) {
        // Folder preview box with tag color border
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.Black.copy(alpha = 0.45f))
                .background(tag.color.copy(alpha = 0.12f))
                .border(BorderStroke(1.5.dp, tag.color), shape = RoundedCornerShape(14.dp))
                .padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                verticalArrangement = Arrangement.SpaceEvenly,
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxSize()
            ) {
                // Row 1 (up to 2 icons)
                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    if (previewApps.isNotEmpty()) {
                        MiniAppIconPreview(app = previewApps[0])
                    }
                    if (previewApps.size > 1) {
                        MiniAppIconPreview(app = previewApps[1])
                    } else if (previewApps.size == 1) {
                        Spacer(modifier = Modifier.size(16.dp))
                    }
                }
                // Row 2 (up to 2 icons)
                if (previewApps.size > 2) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        MiniAppIconPreview(app = previewApps[2])
                        if (previewApps.size > 3) {
                            MiniAppIconPreview(app = previewApps[3])
                        } else {
                            Spacer(modifier = Modifier.size(16.dp))
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Tag name
        Text(
            text = tag.name,
            fontSize = 11.sp,
            color = primaryTextColor.color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall.copy(
                shadow = primaryTextColor.getShadow(showShadows)
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun MiniAppIconPreview(app: AppInfo) {
    val painter = rememberAppIconPainter(app.iconKey, 16)
    Image(
        painter = painter,
        contentDescription = null,
        modifier = Modifier
            .size(16.dp)
            .clip(CircleShape),
        contentScale = ContentScale.Fit
    )
}

/**
 * Folder popup displaying all applications assigned to a specific tag.
 * Implemented using [Popup] positioned near the tapped folder icon.
 * Includes top pencil icon to edit tag, and minus overlay on icons when opened in edit mode.
 *
 * @param tag The tag instance.
 * @param apps The list of applications associated with this tag.
 * @param offset Touch coordinate offset where the tag folder icon was tapped.
 * @param isEditMode True if opened in app removal/edit mode.
 * @param onAppClick Callback when an application is tapped.
 * @param onAppLongClick Callback when an application is long-pressed.
 * @param onRemoveAppFromTag Callback when removing an app from tag (tagId, componentKey).
 * @param onEditTag Callback to edit tag properties.
 * @param onDismiss Callback to dismiss the folder popup.
 * @param primaryTextColor User selected text color setting.
 * @param showShadows True if drop shadows are enabled.
 * @param accentColor Active UI accent color.
 */
@Composable
fun TagFolderPopup(
    tag: Tag,
    apps: List<AppInfo>,
    offset: Offset,
    isEditMode: Boolean = false,
    onAppClick: (String) -> Unit = {},
    onAppLongClick: (AppInfo, Offset) -> Unit = { _, _ -> },
    onRemoveAppFromTag: (String, String) -> Unit = { _, _ -> },
    onEditTag: (Tag) -> Unit = {},
    onDismiss: () -> Unit,
    primaryTextColor: PrimaryTextColor = PrimaryTextColor.WHITE,
    showShadows: Boolean = false,
    accentColor: AccentColor = AccentColor.SKY
) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current

    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }

    val popupWidth = 260.dp
    val popupWidthPx = with(density) { popupWidth.toPx() }

    val rows = if (apps.isEmpty()) 1 else ((apps.size + 2) / 3).coerceIn(1, 4)
    val estimatedHeight = if (apps.isEmpty()) 110.dp else (76 + rows * 80).dp
    val popupHeightPx = with(density) { estimatedHeight.toPx() }
    val borderPadding = with(density) { 16.dp.toPx() }

    var x = offset.x
    var y = offset.y

    if (x + popupWidthPx > screenWidthPx - borderPadding) x = screenWidthPx - popupWidthPx - borderPadding
    if (x < borderPadding) x = borderPadding
    if (y + popupHeightPx > screenHeightPx - borderPadding) y = screenHeightPx - popupHeightPx - borderPadding
    if (y < borderPadding) y = borderPadding

    Popup(
        offset = IntOffset(x.roundToInt(), y.roundToInt()),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        Box(
            modifier = Modifier
                .width(popupWidth)
                .heightIn(max = 360.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black.copy(alpha = 0.90f))
                .border(1.dp, tag.color.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Column {
                // Header with Tag Name, Pencil Edit Icon, and Count
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(tag.color)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = tag.name,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }

                    // Top center Pencil icon to trigger TagEditDialog
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { onEditTag(tag) }
                            .padding(4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = "Edit Tag",
                            tint = Color.White.copy(alpha = 0.75f),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Text(
                        text = "${apps.size}",
                        color = tag.color,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (apps.isEmpty()) {
                    Text(
                        text = "No apps in this tag",
                        color = Color.Gray,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(apps, key = { "${it.packageName}/${it.activityName}" }) { app ->
                            TagFolderAppItem(
                                app = app,
                                isEditMode = isEditMode,
                                onClick = {
                                    onAppClick("${app.packageName}/${app.activityName}")
                                    onDismiss()
                                },
                                onLongClick = { appOffset ->
                                    onDismiss()
                                    onAppLongClick(app, appOffset)
                                },
                                onRemoveAppFromTag = onRemoveAppFromTag,
                                tagId = tag.id,
                                primaryTextColor = primaryTextColor,
                                showShadows = showShadows
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TagFolderAppItem(
    app: AppInfo,
    isEditMode: Boolean,
    onClick: () -> Unit,
    onLongClick: (Offset) -> Unit,
    onRemoveAppFromTag: (String, String) -> Unit,
    tagId: String,
    primaryTextColor: PrimaryTextColor,
    showShadows: Boolean
) {
    var itemPosition by remember { mutableStateOf(Offset.Zero) }
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnLongClick by rememberUpdatedState(onLongClick)
    val currentOnRemoveApp by rememberUpdatedState(onRemoveAppFromTag)
    val appKey = "${app.packageName}/${app.activityName}"

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { itemPosition = it.positionInRoot() }
            .pointerInput(appKey) {
                detectTapGestures(
                    onTap = {
                        if (isEditMode) {
                            currentOnRemoveApp(tagId, appKey)
                        } else {
                            currentOnClick()
                        }
                    },
                    onLongPress = { currentOnLongClick(itemPosition + it) }
                )
            }
            .padding(4.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            AppIconItem(
                app = app,
                size = 48,
                onClick = {
                    if (isEditMode) currentOnRemoveApp(tagId, appKey) else currentOnClick()
                },
                onLongClick = { currentOnLongClick(itemPosition + it) }
            )

            // Minus icon overlay in center with 38% opacity when in edit mode
            if (isEditMode) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.65f))
                        .border(1.dp, Color.Red.copy(alpha = 0.6f), CircleShape)
                        .clickable { currentOnRemoveApp(tagId, appKey) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Remove,
                        contentDescription = "Remove from tag",
                        tint = Color.White.copy(alpha = 0.38f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = app.label,
            color = Color.White,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

