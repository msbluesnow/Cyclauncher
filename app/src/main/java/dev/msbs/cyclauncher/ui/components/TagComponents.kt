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
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarOutline
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
import dev.msbs.cyclauncher.ui.theme.PopupTheme
import dev.msbs.cyclauncher.ui.theme.PrimaryTextColor
import dev.msbs.cyclauncher.ui.theme.LocalShadowSettings
import kotlin.math.roundToInt

/**
 * Tag folder item displaying a colored border and a 2x2 preview of assigned app icons.
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

        Text(
            text = tag.name,
            fontSize = 11.sp,
            color = primaryTextColor.color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall.copy(
                shadow = primaryTextColor.getShadow(showShadows, LocalShadowSettings.current.shadowColorOverride)
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
 * Popup dialog showing all applications within a tag folder with edit and management actions.
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
    accentColor: AccentColor = AccentColor.SKY,
    popupTheme: PopupTheme = PopupTheme.DARK
) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current

    val popupWidth = 260.dp
    val popupWidthPx = with(density) { popupWidth.toPx() }

    val rows = if (apps.isEmpty()) 1 else ((apps.size + 2) / 3).coerceIn(1, 4)
    val estimatedHeight = if (apps.isEmpty()) 110.dp else (76 + rows * 80).dp
    val popupHeightPx = with(density) { estimatedHeight.toPx() }
    val borderPadding = with(density) { 16.dp.toPx() }

    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }

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
                .background(popupTheme.backgroundColor)
                .border(1.dp, tag.color.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(tag.color)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        BoxWithConstraints(modifier = Modifier.weight(1f)) {
                            var fontSize by remember(tag.name, maxWidth) { mutableStateOf(17.sp) }
                            var readyToDraw by remember(tag.name, maxWidth) { mutableStateOf(tag.name.length <= 10) }

                            Text(
                                text = tag.name,
                                color = if (readyToDraw) popupTheme.contentColor else Color.Transparent,
                                fontWeight = FontWeight.Bold,
                                fontSize = fontSize,
                                maxLines = 2,
                                lineHeight = (fontSize.value * 1.15f).sp,
                                overflow = TextOverflow.Ellipsis,
                                softWrap = true,
                                onTextLayout = { textLayoutResult ->
                                    if (textLayoutResult.hasVisualOverflow && fontSize.value > 11f) {
                                        fontSize = (fontSize.value * 0.88f).sp
                                    } else {
                                        readyToDraw = true
                                    }
                                }
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { onEditTag(tag) }
                                .padding(4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (showShadows) {
                                val shadowSettings = LocalShadowSettings.current
                                Icon(
                                    imageVector = Icons.Outlined.Edit,
                                    contentDescription = null,
                                    tint = primaryTextColor.getShadowColor(shadowSettings.shadowColorOverride).copy(alpha = 0.25f),
                                    modifier = Modifier
                                        .size(17.dp)
                                        .offset(1.dp, 1.dp)
                                )
                            }
                            Icon(
                                imageVector = Icons.Outlined.Edit,
                                contentDescription = "Edit Tag",
                                tint = popupTheme.secondaryContentColor,
                                modifier = Modifier.size(17.dp)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(tag.color.copy(alpha = 0.15f))
                                .border(0.8.dp, tag.color.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${apps.size}",
                                color = tag.color,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (apps.isEmpty()) {
                    Text(
                        text = "No apps in this tag",
                        color = popupTheme.secondaryContentColor,
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
                                    onAppLongClick(app, Offset(x, y) + appOffset)
                                },
                                onRemoveAppFromTag = onRemoveAppFromTag,
                                tagId = tag.id,
                                primaryTextColor = primaryTextColor,
                                showShadows = showShadows,
                                popupTheme = popupTheme
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
    showShadows: Boolean,
    popupTheme: PopupTheme = PopupTheme.DARK
) {
    var itemPosition by remember { mutableStateOf(Offset.Zero) }
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnLongClick by rememberUpdatedState(onLongClick)
    val currentOnRemoveApp by rememberUpdatedState(onRemoveAppFromTag)
    val currentIsEditMode by rememberUpdatedState(isEditMode)
    val currentTagId by rememberUpdatedState(tagId)
    val appKey = "${app.packageName}/${app.activityName}"

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { itemPosition = it.positionInRoot() }
            .pointerInput(appKey) {
                detectTapGestures(
                    onTap = {
                        if (currentIsEditMode) {
                            currentOnRemoveApp(currentTagId, appKey)
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
                onLongClick = { currentOnLongClick(it) }
            )

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
            color = popupTheme.contentColor,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Preview icon representing a tag folder within the Favorites section.
 */
@Composable
fun TagFolderIcon(
    tag: Tag,
    apps: List<AppInfo>,
    size: Int = 48,
    modifier: Modifier = Modifier
) {
    val previewApps = remember(apps) { apps.take(4) }
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(Color.Black.copy(alpha = 0.45f))
            .background(tag.color.copy(alpha = 0.12f))
            .border(BorderStroke(1.5.dp, tag.color), shape = RoundedCornerShape(13.dp))
            .padding(3.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize()
        ) {
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
                    Spacer(modifier = Modifier.size(14.dp))
                }
            }
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
                        Spacer(modifier = Modifier.size(14.dp))
                    }
                }
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

/**
 * Context action menu for tag folders (edit group, toggle favorite).
 */
@Composable
fun TagFolderActionMenu(
    tag: Tag,
    isFavorite: Boolean,
    offset: Offset,
    onDismiss: () -> Unit,
    onEditGroup: () -> Unit,
    onToggleFavorite: () -> Unit,
    accentColor: AccentColor = AccentColor.SKY,
    primaryTextColor: PrimaryTextColor = PrimaryTextColor.WHITE,
    popupTheme: PopupTheme = PopupTheme.DARK
) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current

    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }

    val menuWidth = 240.dp
    val menuWidthPx = with(density) { menuWidth.toPx() }

    val itemsCount = 2
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
                .border(1.dp, tag.color.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                .padding(vertical = 8.dp)
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(tag.color)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = tag.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = popupTheme.contentColor,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onDismiss()
                            onEditGroup()
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = null,
                        tint = accentColor.color,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Edit Group",
                        color = popupTheme.contentColor,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onDismiss()
                            onToggleFavorite()
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Outlined.Star else Icons.Outlined.StarOutline,
                        contentDescription = null,
                        tint = accentColor.color,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = if (isFavorite) "Remove from Favorites" else "Add to Favorites",
                        color = popupTheme.contentColor,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }
}


